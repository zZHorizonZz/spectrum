package dev.horizon.spectrum.check;

import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.TupleToUserset;
import dev.horizon.spectrum.openfga.v1.Userset;
import dev.horizon.spectrum.storage.TupleReader;
import dev.horizon.spectrum.tuple.ObjectUser;
import dev.horizon.spectrum.tuple.RelationshipTuple;
import dev.horizon.spectrum.tuple.TupleFilter;
import dev.horizon.spectrum.tuple.TupleUser;
import dev.horizon.spectrum.tuple.UsersetUser;
import dev.horizon.spectrum.tuple.WildcardUser;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Checks whether a user has a relation to an object. It follows the rewrite of the relation through direct tuples,
 * usersets, computed relations, tuple to userset rewrites, unions, intersections and exclusions. The branches of a set
 * operation run at once, and a union or an intersection answers as soon as one branch decides it.
 */
public final class LocalChecker {

  private final TypeSystem types;
  private final TupleReader reader;
  private final TupleFilter filter;
  private final int maxDepth;

  public LocalChecker(TypeSystem types, TupleReader reader, int maxDepth) {
    this.types = types;
    this.reader = reader;
    this.filter = new TupleFilter(types);
    this.maxDepth = maxDepth;
  }

  /**
   * Checks a tuple.
   *
   * @return the answer, or a future that fails with an {@link OpenFgaException} if the check goes deeper than the
   * resolution depth limit, or a condition fails to evaluate
   */
  public Future<CheckResult> check(ResolveCheckRequest request) {
    if (request.depth() >= maxDepth) {
      return Future.failedFuture(new OpenFgaException(ErrorCode.authorization_model_resolution_too_complex,
          "Authorization Model resolution required too many rewrite rules to be resolved. Check your authorization"
              + " model for infinite recursion or too much nesting"));
    }
    if (request.cycle()) {
      return Future.succeededFuture(CheckResult.CYCLE);
    }
    RelationshipTuple tuple = request.tuple();
    if (tuple.user() instanceof UsersetUser userset && userset.object().equals(tuple.object())
        && userset.relation().equals(tuple.relation())) {
      return Future.succeededFuture(CheckResult.ALLOWED);
    }
    String objectType = tuple.object().type();
    return types.relation(objectType, tuple.relation())
        .map(relation -> rewrite(request.visiting(), relation.getRewrite()))
        .orElseGet(() -> Future.failedFuture(new OpenFgaException(ErrorCode.validation_error, "relation '"
            + tuple.relation() + "' undefined for object type '" + objectType + "'")));
  }

  private Future<CheckResult> rewrite(ResolveCheckRequest request, Userset rewrite) {
    RelationshipTuple tuple = request.tuple();
    return switch (rewrite.getUsersetCase()) {
      case THIS -> direct(request);
      case COMPUTED_USERSET -> check(request.rewritten(tuple.withRelation(rewrite.getComputedUserset()
          .getRelation())));
      case TUPLE_TO_USERSET -> tupleToUserset(request, rewrite.getTupleToUserset());
      case UNION -> union(rewrite.getUnion().getChildList().stream()
          .<Supplier<Future<CheckResult>>> map(child -> () -> rewrite(request, child)).toList());
      case INTERSECTION -> intersection(rewrite.getIntersection().getChildList().stream()
          .<Supplier<Future<CheckResult>>> map(child -> () -> rewrite(request, child)).toList());
      case DIFFERENCE -> exclusion(start(() -> rewrite(request, rewrite.getDifference().getBase())),
          start(() -> rewrite(request, rewrite.getDifference().getSubtract())));
      default -> Future.failedFuture(new IllegalStateException("unexpected set operator "
          + rewrite.getUsersetCase()));
    };
  }

  /** Checks the tuples of the relation itself: of the user, of its typed wildcard, and of usersets. */
  private Future<CheckResult> direct(ResolveCheckRequest request) {
    RelationshipTuple tuple = request.tuple();
    String objectType = tuple.object().type();
    TupleUser user = tuple.user();
    RelationReference target = types.directReference(objectType, tuple.relation());
    List<Supplier<Future<CheckResult>>> handlers = new ArrayList<>();
    String userRelation = user instanceof UsersetUser userset ? userset.relation() : "";
    if (types.isDirectlyRelated(target, types.directReference(user.type(), userRelation))) {
      handlers.add(() -> directUserTuple(request));
    }
    if (!(user instanceof UsersetUser) && types.isPubliclyAssignable(target, user.type())) {
      handlers.add(() -> publiclyAssignable(request, user.type()));
    }
    List<RelationReference> usersets = types.directlyRelatedUsersets(objectType, tuple.relation());
    if (!usersets.isEmpty()) {
      handlers.add(() -> usersetTuples(request, usersets));
    }
    return union(handlers);
  }

  private Future<CheckResult> directUserTuple(ResolveCheckRequest request) {
    return reader.readUserTuple(request.store(), request.tuple().id()).map(found -> found
        .map(stored -> filter.valid(List.of(stored)))
        .filter(valid -> !valid.isEmpty())
        .map(valid -> filter.conditionMet(valid.getFirst(), request.context()) ? CheckResult.ALLOWED
            : CheckResult.DENIED)
        .orElse(CheckResult.DENIED));
  }

  private Future<CheckResult> publiclyAssignable(ResolveCheckRequest request, String userType) {
    RelationshipTuple tuple = request.tuple();
    return reader.readUsersetTuples(request.store(), new TupleReader.UsersetFilter(tuple.object().toString(),
        tuple.relation(), List.of(types.wildcardReference(userType))))
        .map(tuples -> filter.counting(tuples, request.context()).isEmpty() ? CheckResult.DENIED
            : CheckResult.ALLOWED);
  }

  /** Checks the usersets of the relation: the user has the relation if it is in one of them. */
  private Future<CheckResult> usersetTuples(ResolveCheckRequest request, List<RelationReference> usersets) {
    RelationshipTuple tuple = request.tuple();
    return reader.readUsersetTuples(request.store(), new TupleReader.UsersetFilter(tuple.object().toString(),
        tuple.relation(), usersets)).compose(tuples -> {
          List<Supplier<Future<CheckResult>>> dispatches = new ArrayList<>();
          for (RelationshipTuple stored : filter.counting(tuples, request.context())) {
            if (stored.user() instanceof WildcardUser wildcard && wildcard.type().equals(tuple.user().type())) {
              return Future.succeededFuture(CheckResult.ALLOWED);
            }
            if (stored.user() instanceof UsersetUser userset) {
              dispatches.add(() -> check(request.dispatched(new RelationshipTuple(userset.object(),
                  userset.relation(), tuple.user(), Optional.empty()))));
            }
          }
          return union(dispatches);
        });
  }

  /** Checks a relation of the objects that the tupleset relation of the object names. */
  private Future<CheckResult> tupleToUserset(ResolveCheckRequest request, TupleToUserset rewrite) {
    RelationshipTuple tuple = request.tuple();
    String computed = rewrite.getComputedUserset().getRelation();
    return reader.read(request.store(), new TupleReader.ReadFilter(tuple.object().toString(),
        rewrite.getTupleset().getRelation(), "")).compose(tuples -> {
          List<Supplier<Future<CheckResult>>> dispatches = new ArrayList<>();
          for (RelationshipTuple tupleset : filter.counting(tuples, request.context())) {
            if (tupleset.user() instanceof ObjectUser parent && types.relation(parent.type(), computed).isPresent()) {
              dispatches.add(() -> check(request.dispatched(new RelationshipTuple(parent.object(), computed,
                  tuple.user(), Optional.empty()))));
            }
          }
          return union(dispatches);
        });
  }

  /**
   * Gives true as soon as one branch gives true. A cycle in a branch that does not allow is kept in the answer, and a
   * failure fails the answer only when no branch allows.
   */
  private Future<CheckResult> union(List<Supplier<Future<CheckResult>>> branches) {
    if (branches.isEmpty()) {
      return Future.succeededFuture(CheckResult.DENIED);
    }
    Promise<CheckResult> answer = Promise.promise();
    AtomicInteger pending = new AtomicInteger(branches.size());
    AtomicBoolean cycle = new AtomicBoolean();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    for (Supplier<Future<CheckResult>> branch : branches) {
      start(branch).onComplete(result -> {
        if (result.succeeded() && result.result().allowed()) {
          answer.tryComplete(result.result());
        } else if (result.succeeded()) {
          cycle.compareAndSet(false, result.result().cycleDetected());
        } else {
          failure.compareAndSet(null, result.cause());
        }
        if (pending.decrementAndGet() == 0) {
          if (failure.get() != null) {
            answer.tryFail(failure.get());
          } else {
            answer.tryComplete(cycle.get() ? CheckResult.CYCLE : CheckResult.DENIED);
          }
        }
      });
    }
    return answer.future();
  }

  /**
   * Gives false as soon as one branch gives false or finds a cycle. A failure fails the answer only when no branch
   * denies.
   */
  private Future<CheckResult> intersection(List<Supplier<Future<CheckResult>>> branches) {
    if (branches.isEmpty()) {
      return Future.succeededFuture(CheckResult.DENIED);
    }
    Promise<CheckResult> answer = Promise.promise();
    AtomicInteger pending = new AtomicInteger(branches.size());
    AtomicReference<Throwable> failure = new AtomicReference<>();
    for (Supplier<Future<CheckResult>> branch : branches) {
      start(branch).onComplete(result -> {
        if (result.succeeded() && (!result.result().allowed() || result.result().cycleDetected())) {
          answer.tryComplete(result.result().cycleDetected() ? CheckResult.CYCLE : CheckResult.DENIED);
        } else if (result.failed()) {
          failure.compareAndSet(null, result.cause());
        }
        if (pending.decrementAndGet() == 0) {
          if (failure.get() != null) {
            answer.tryFail(failure.get());
          } else {
            answer.tryComplete(CheckResult.ALLOWED);
          }
        }
      });
    }
    return answer.future();
  }

  /**
   * Gives true if the base allows and the subtracted relation does not. A cycle in either gives false.
   */
  private Future<CheckResult> exclusion(Future<CheckResult> base, Future<CheckResult> subtract) {
    Promise<CheckResult> answer = Promise.promise();
    AtomicInteger pending = new AtomicInteger(2);
    AtomicReference<Throwable> baseFailure = new AtomicReference<>();
    AtomicReference<Throwable> subtractFailure = new AtomicReference<>();
    base.onComplete(result -> {
      if (result.succeeded() && (!result.result().allowed() || result.result().cycleDetected())) {
        answer.tryComplete(new CheckResult(false, result.result().cycleDetected()));
      } else if (result.failed()) {
        baseFailure.set(result.cause());
      }
      finish(answer, pending, baseFailure, subtractFailure);
    });
    subtract.onComplete(result -> {
      if (result.succeeded() && (result.result().allowed() || result.result().cycleDetected())) {
        answer.tryComplete(new CheckResult(false, result.result().cycleDetected()));
      } else if (result.failed()) {
        subtractFailure.set(result.cause());
      }
      finish(answer, pending, baseFailure, subtractFailure);
    });
    return answer.future();
  }

  private void finish(Promise<CheckResult> answer, AtomicInteger pending, AtomicReference<Throwable> base,
      AtomicReference<Throwable> subtract) {
    if (pending.decrementAndGet() > 0) {
      return;
    }
    if (base.get() != null) {
      answer.tryFail(base.get());
    } else if (subtract.get() != null) {
      answer.tryFail(subtract.get());
    } else {
      answer.tryComplete(CheckResult.ALLOWED);
    }
  }

  private Future<CheckResult> start(Supplier<Future<CheckResult>> branch) {
    try {
      return branch.get();
    } catch (RuntimeException failed) {
      return Future.failedFuture(failed);
    }
  }
}
