package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.OpenFgaServiceOptions;
import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.model.TypeSystemResolver;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import dev.horizon.spectrum.openfga.v1.WriteRequest;
import dev.horizon.spectrum.openfga.v1.WriteResponse;
import dev.horizon.spectrum.storage.TupleBackend;
import dev.horizon.spectrum.tuple.InvalidTupleException;
import dev.horizon.spectrum.tuple.RelationshipTuple;
import dev.horizon.spectrum.tuple.TupleId;
import dev.horizon.spectrum.tuple.TupleUser;
import dev.horizon.spectrum.tuple.UsersetUser;
import dev.horizon.spectrum.tuple.WriteValidator;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WriteHandler extends UnaryHandler<WriteRequest, WriteResponse> {

  public static final ServiceMethod<WriteRequest, WriteResponse> SERVICE_METHOD = method(
      "Write", WriteRequest.newBuilder());

  private static final String ERROR = "error";
  private static final String IGNORE = "ignore";

  private final TypeSystemResolver resolver;
  private final TupleBackend tuples;
  private final OpenFgaServiceOptions options;

  public WriteHandler(TypeSystemResolver resolver, TupleBackend tuples, OpenFgaServiceOptions options) {
    this.resolver = resolver;
    this.tuples = tuples;
    this.options = options;
  }

  @Override
  Future<WriteResponse> execute(WriteRequest request) {
    return resolver.resolve(request.getStoreId(), request.getAuthorizationModelId())
        .compose(types -> execute(request, types));
  }

  private Future<WriteResponse> execute(WriteRequest request, TypeSystem types) {
    List<TupleKeyWithoutCondition> deletes = request.getDeletes().getTupleKeysList();
    List<TupleKey> writes = request.getWrites().getTupleKeysList();
    if (deletes.isEmpty() && writes.isEmpty()) {
      throw new OpenFgaException(ErrorCode.invalid_write_input, "Invalid input. Make sure you provide at least one"
          + " write, or at least one delete");
    }
    boolean ignoreDuplicates = option("on_duplicate", request.getWrites().getOnDuplicate());
    boolean ignoreMissing = option("on_missing", request.getDeletes().getOnMissing());
    WriteValidator validator = new WriteValidator(types);
    for (TupleKey write : writes) {
      RelationshipTuple tuple = validator.validate(write);
      if (tuple.user() instanceof UsersetUser userset && userset.object().equals(tuple.object())
          && userset.relation().equals(tuple.relation())) {
        throw invalidTuple(tuple.id(), "cannot write a tuple that is implicit");
      }
      int contextSize = write.getCondition().getContext().getSerializedSize();
      if (contextSize > options.maxConditionContextSizeInBytes()) {
        throw invalidTuple(new TupleId(write), "condition context size limit exceeded: " + contextSize
            + " bytes exceeds " + options.maxConditionContextSizeInBytes() + " bytes");
      }
    }
    for (TupleKeyWithoutCondition delete : deletes) {
      try {
        TupleUser.parse(delete.getUser());
      } catch (InvalidTupleException malformed) {
        throw invalidTuple(new TupleId(delete), malformed.getMessage());
      }
    }
    Set<TupleId> unique = new HashSet<>();
    deletes.forEach(delete -> unique(unique, new TupleId(delete)));
    writes.forEach(write -> unique(unique, new TupleId(write)));
    if (unique.size() > options.maxTuplesPerWrite()) {
      throw new OpenFgaException(ErrorCode.exceeded_entity_limit, "The number of write operations exceeds the"
          + " allowed limit of " + options.maxTuplesPerWrite());
    }
    return tuples.write(request.getStoreId(), deletes, writes, new TupleBackend.WriteOptions(ignoreMissing,
        ignoreDuplicates)).map(WriteResponse.getDefaultInstance());
  }

  private boolean option(String name, String value) {
    return switch (value) {
      case "", ERROR -> false;
      case IGNORE -> true;
      default -> throw new OpenFgaException(ErrorCode.validation_error, "invalid " + name + " option: " + value);
    };
  }

  private void unique(Set<TupleId> tuples, TupleId tuple) {
    if (!tuples.add(tuple)) {
      throw new OpenFgaException(ErrorCode.cannot_allow_duplicate_tuples_in_one_request, "duplicate tuple in write:"
          + " user: '" + tuple.user() + "', relation: '" + tuple.relation() + "', object: '" + tuple.object() + "'");
    }
  }

  private OpenFgaException invalidTuple(TupleId tuple, String reason) {
    return new OpenFgaException(ErrorCode.validation_error, "Invalid tuple '" + tuple + "'. Reason: " + reason);
  }
}
