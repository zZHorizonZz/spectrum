package dev.horizon.spectrum;

import dev.horizon.spectrum.v1.Rewrite;
import io.vertx.core.Future;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Checks if one subject has relations to objects, from the object down the model, as Zanzibar's check does.
 *
 * <p>
 * A part of a check is true, false, or undecided, as in Kleene's logic. A relation of an object that the check reaches
 * again on its own path counts as false there, so the check stops at a cycle; the cut holds until the check is back at
 * that relation, which then has its value, since a subject that has it has it by a path without the cycle. A
 * {@code difference} whose part to leave out is still cut cannot be decided, and stays undecided through every
 * {@code difference} above it, so an undecided part never lets a subject in. The check answers true only for a part that
 * is true.
 */
final class Check {

  /** How many relations deep a check, or a list, follows the model. */
  static final int MAX_DEPTH = 25;

  private final Definitions model;
  private final Reads reads;
  private final Ref subject;
  private final Map<String, Value> known = new HashMap<>();

  /** What a part of a check is. */
  enum Value {
    TRUE,
    FALSE,
    UNDECIDED
  }

  /**
   * What a part of a check gave.
   *
   * @param value the value
   * @param cuts the relations, {@code object#relation}, that a cycle cut the part short at and that are not decided
   * yet; the value holds only on this path while there are any
   */
  record Result(Value value, Set<String> cuts) {

    static final Result TRUE = new Result(Value.TRUE, Set.of());
    static final Result FALSE = new Result(Value.FALSE, Set.of());

    Result {
      cuts = Set.copyOf(cuts);
    }

    /** Gives a value with the cuts of this result and of another. */
    Result with(Value next, Set<String> more) {
      if (more.isEmpty()) {
        return new Result(next, cuts);
      }
      Set<String> all = new HashSet<>(cuts);
      all.addAll(more);
      return new Result(next, all);
    }
  }

  Check(Definitions model, Reads reads, Ref subject) {
    this.model = model;
    this.reads = reads;
    this.subject = subject;
  }

  /** Checks if the subject has a relation to an object; an undecided relation is denied. */
  Future<Boolean> run(Ref object, String relation) {
    return check(object, relation, 1, Set.of()).map(result -> result.value() == Value.TRUE);
  }

  private Future<Result> check(Ref object, String relation, int depth, Set<String> path) {
    String key = object.object() + "#" + relation;
    if (subject.text().equals(key)) {
      return Future.succeededFuture(Result.TRUE);
    }
    Value value = known.get(key);
    if (value != null) {
      return Future.succeededFuture(new Result(value, Set.of()));
    }
    if (path.contains(key)) {
      return Future.succeededFuture(new Result(Value.FALSE, Set.of(key)));
    }
    if (depth > MAX_DEPTH) {
      return Future.failedFuture(tooDeep());
    }
    Optional<Rewrite> rewrite = model.rewrite(object.type(), relation);
    if (rewrite.isEmpty()) {
      return Future.succeededFuture(Result.FALSE);
    }
    Set<String> inner = new HashSet<>(path);
    inner.add(key);
    return evaluate(object, relation, rewrite.get(), depth, inner).map(result -> {
      // Back at the relation, its own cuts are decided.
      Set<String> cuts = new HashSet<>(result.cuts());
      cuts.remove(key);
      Result decided = new Result(result.value(), cuts);
      if (cuts.isEmpty()) {
        known.put(key, decided.value());
      }
      return decided;
    });
  }

  private Future<Result> evaluate(Ref object, String relation, Rewrite rewrite, int depth, Set<String> path) {
    return switch (rewrite.getRewriteCase()) {
      case DIRECT -> reads.subjects(object.object(), relation).compose(named -> {
        List<Ref> allowed = named.stream().filter(ref -> Definitions.allows(rewrite.getDirect(), ref)).toList();
        if (allowed.stream().anyMatch(this::names)) {
          return Future.succeededFuture(Result.TRUE);
        }
        return any(allowed.stream().filter(Ref::userset).toList(),
            userset -> check(new Ref(userset.type(), userset.id(), ""), userset.relation(), depth + 1, path));
      });
      case COMPUTED -> check(object, rewrite.getComputed().getRelation(), depth + 1, path);
      case FROM -> reads.subjects(object.object(), rewrite.getFrom().getTupleset())
          .compose(named -> any(named.stream().filter(ref -> !ref.userset() && !ref.wildcard()
              && model.allows(object.type(), rewrite.getFrom().getTupleset(), ref)).toList(),
              related -> check(related, rewrite.getFrom().getRelation(), depth + 1, path)));
      case UNION -> any(rewrite.getUnion().getRewritesList(), child -> evaluate(object, relation, child, depth, path));
      case INTERSECTION -> all(rewrite.getIntersection().getRewritesList(),
          child -> evaluate(object, relation, child, depth, path));
      case DIFFERENCE -> evaluate(object, relation, rewrite.getDifference().getBase(), depth, path)
          .compose(kept -> kept.value() == Value.FALSE ? Future.succeededFuture(kept)
              : evaluate(object, relation, rewrite.getDifference().getSubtract(), depth, path)
                  .map(left -> difference(kept, left)));
      case REWRITE_NOT_SET -> Future.succeededFuture(Result.FALSE);
    };
  }

  /** Leaves out of a part that is not false what another part has. */
  private static Result difference(Result kept, Result left) {
    if (left.value() == Value.TRUE) {
      return kept.with(Value.FALSE, left.cuts());
    }
    if (left.value() == Value.FALSE && left.cuts().isEmpty()) {
      return kept;
    }
    // What to leave out is undecided, or false only while a cut holds: so is what is left.
    return kept.with(Value.UNDECIDED, left.cuts());
  }

  /** Tells if a subject that a tuple names is the subject of the check, itself or as every object of its type. */
  private boolean names(Ref named) {
    return named.text().equals(subject.text())
        || named.wildcard() && !subject.userset() && named.type().equals(subject.type());
  }

  /** Tells if any of some parts is true, checking them one after the other until one is. */
  static <T> Future<Result> any(List<T> items, Function<T, Future<Result>> part) {
    return any(items, 0, Result.FALSE, part);
  }

  private static <T> Future<Result> any(List<T> items, int index, Result sofar, Function<T, Future<Result>> part) {
    if (index == items.size()) {
      return Future.succeededFuture(sofar);
    }
    return part.apply(items.get(index)).compose(result -> {
      if (result.value() == Value.TRUE) {
        return Future.succeededFuture(sofar.with(Value.TRUE, result.cuts()));
      }
      Value next = sofar.value() == Value.UNDECIDED || result.value() == Value.UNDECIDED ? Value.UNDECIDED
          : Value.FALSE;
      return any(items, index + 1, sofar.with(next, result.cuts()), part);
    });
  }

  /** Tells if all of some parts are true, checking them one after the other until one is false. */
  private static <T> Future<Result> all(List<T> items, Function<T, Future<Result>> part) {
    return all(items, 0, Result.TRUE, part);
  }

  private static <T> Future<Result> all(List<T> items, int index, Result sofar, Function<T, Future<Result>> part) {
    if (index == items.size()) {
      return Future.succeededFuture(sofar);
    }
    return part.apply(items.get(index)).compose(result -> {
      if (result.value() == Value.FALSE) {
        return Future.succeededFuture(sofar.with(Value.FALSE, result.cuts()));
      }
      Value next = sofar.value() == Value.UNDECIDED || result.value() == Value.UNDECIDED ? Value.UNDECIDED
          : Value.TRUE;
      return all(items, index + 1, sofar.with(next, result.cuts()), part);
    });
  }

  static IllegalStateException tooDeep() {
    return new IllegalStateException("the relations go more than " + MAX_DEPTH + " levels deep; check the model and"
        + " the tuples for a chain that long");
  }
}
