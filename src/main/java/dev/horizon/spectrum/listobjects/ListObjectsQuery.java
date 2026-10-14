package dev.horizon.spectrum.listobjects;

import com.google.protobuf.Struct;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.OpenFgaServiceOptions;
import dev.horizon.spectrum.check.LocalChecker;
import dev.horizon.spectrum.check.ResolveCheckRequest;
import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.storage.CombinedTupleReader;
import dev.horizon.spectrum.storage.TupleReader;
import dev.horizon.spectrum.tuple.InvalidTupleException;
import dev.horizon.spectrum.tuple.ModelRule;
import dev.horizon.spectrum.tuple.RelationshipTuple;
import dev.horizon.spectrum.tuple.TupleObject;
import dev.horizon.spectrum.tuple.TupleUser;
import dev.horizon.spectrum.tuple.WriteValidator;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Lists the objects of a type that a user has a relation to. A {@link ReverseExpandQuery} finds the candidates, and a
 * check confirms each candidate that it reached through an intersection or an exclusion. At the deadline, the list ends
 * with the objects found so far.
 */
public final class ListObjectsQuery {

  private final Vertx vertx;
  private final TupleReader reader;
  private final OpenFgaServiceOptions options;

  public ListObjectsQuery(Vertx vertx, TupleReader reader, OpenFgaServiceOptions options) {
    this.vertx = vertx;
    this.reader = reader;
    this.options = options;
  }

  public record Request(String store, String type, String relation, String user, List<TupleKey> contextual,
      Struct context) {
  }

  /**
   * Lists the objects, up to the most results of the options.
   *
   * @param request the objects to list
   * @throws OpenFgaException if the request does not fit the model
   */
  public Future<List<String>> list(Request request, TypeSystem types) {
    validate(request, types);
    List<String> objects = Collections.synchronizedList(new ArrayList<>());
    Promise<List<String>> answer = Promise.promise();
    int max = options.listObjectsMaxResults();
    long deadline = vertx.setTimer(options.listObjectsDeadline().toMillis(),
        expired -> answer.tryComplete(List.copyOf(objects)));
    search(request, types, object -> {
      synchronized (objects) {
        if (objects.size() < max) {
          objects.add(object);
        }
        if (objects.size() >= max) {
          answer.tryComplete(List.copyOf(objects));
        }
      }
    }).onComplete(done -> {
      vertx.cancelTimer(deadline);
      if (done.failed() && objects.size() < max) {
        answer.tryFail(done.cause());
      } else {
        answer.tryComplete(List.copyOf(objects));
      }
    });
    return answer.future();
  }

  /**
   * Gives each object to a consumer as the search finds it, without a most results.
   *
   * @param request the objects to list
   * @param found takes each object once
   * @return a future that completes when the search ends, at the deadline at the latest
   * @throws OpenFgaException if the request does not fit the model
   */
  public Future<Void> stream(Request request, TypeSystem types, Consumer<String> found) {
    validate(request, types);
    Promise<Void> answer = Promise.promise();
    long deadline = vertx.setTimer(options.listObjectsDeadline().toMillis(), expired -> answer.tryComplete());
    search(request, types, object -> {
      if (!answer.future().isComplete()) {
        found.accept(object);
      }
    }).onComplete(done -> {
      vertx.cancelTimer(deadline);
      if (done.failed()) {
        answer.tryFail(done.cause());
      } else {
        answer.tryComplete();
      }
    });
    return answer.future();
  }

  private void validate(Request request, TypeSystem types) {
    if (!types.isSupported()) {
      throw new OpenFgaException(ErrorCode.validation_error, "invalid schema version");
    }
    WriteValidator validator = new WriteValidator(types);
    for (TupleKey tuple : request.contextual()) {
      try {
        validator.validate(tuple);
      } catch (OpenFgaException invalid) {
        throw new OpenFgaException(ErrorCode.invalid_tuple, invalid.getMessage());
      }
    }
    if (types.typeDefinition(request.type()).isEmpty()) {
      throw new OpenFgaException(ErrorCode.type_not_found, "type '" + request.type() + "' not found");
    }
    if (types.relation(request.type(), request.relation()).isEmpty()) {
      throw new OpenFgaException(ErrorCode.relation_not_found, "relation '" + request.type() + "#"
          + request.relation() + "' not found");
    }
    try {
      new ModelRule(types).checkUser(TupleUser.parse(request.user()));
    } catch (InvalidTupleException invalid) {
      throw new OpenFgaException(ErrorCode.validation_error, "invalid 'user' value: " + invalid.getMessage());
    }
  }

  private Future<Void> search(Request request, TypeSystem types, Consumer<String> found) {
    TupleReader combined = new CombinedTupleReader(reader, request.contextual());
    LocalChecker checker = new LocalChecker(types, combined, options.resolveNodeLimit());
    List<Future<Void>> checks = Collections.synchronizedList(new ArrayList<>());
    return new ReverseExpandQuery(types, combined, options.resolveNodeLimit()).execute(request.store(),
        request.type(), request.relation(), request.user(), request.context(), (object, needsCheck) -> {
          if (!needsCheck) {
            found.accept(object);
            return;
          }
          RelationshipTuple candidate = new RelationshipTuple(TupleObject.parse(object), request.relation(),
              TupleUser.parse(request.user()), Optional.empty());
          checks.add(checker.check(new ResolveCheckRequest(request.store(), candidate, request.context()))
              .map(result -> {
                if (result.allowed()) {
                  found.accept(object);
                }
                return null;
              }));
        }).compose(expanded -> {
          List<Future<Void>> pending;
          synchronized (checks) {
            pending = List.copyOf(checks);
          }
          return Future.all(pending).mapEmpty();
        });
  }
}
