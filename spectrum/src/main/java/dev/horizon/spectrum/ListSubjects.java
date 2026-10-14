package dev.horizon.spectrum;

import dev.horizon.spectrum.v1.Rewrite;
import dev.horizon.spectrum.v1.SubjectSet;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lists the subjects of one type that have a relation to an object, as OpenFGA's ListUsers does, with the same answer
 * as a {@link Check} of each.
 *
 * <p>
 * A check reads only the tuples of the relations it reaches, and it tells subjects apart only by whether a tuple names
 * them. So a subject that no tuple of those relations names has the same answer as any other such subject. The list
 * visits every relation that a check of the object can reach, gathers the subjects of the type that their tuples
 * name, and checks each of them and one subject that no tuple names, which stands for all the others. The answer is
 * thus some subjects, or every subject but some.
 */
final class ListSubjects {

  private final Definitions model;
  private final Reads reads;
  private final String type;
  private final Set<String> visited = new HashSet<>();
  private final Set<String> named = new TreeSet<>();

  ListSubjects(Definitions model, Reads reads, String type) {
    this.model = model;
    this.reads = reads;
    this.type = type;
  }

  /** Lists the subjects of a relation of an object. */
  Future<SubjectSet> run(Ref object, String relation) {
    Ref start = new Ref(object.type(), object.id(), relation);
    visited.add(start.text());
    return level(List.of(start), 1).compose(ignored -> {
      Ref anyone = anyone();
      return new Check(model, reads, anyone).run(object, relation).compose(all -> {
        List<String> differ = new ArrayList<>();
        Future<Void> done = Future.succeededFuture();
        for (String subject : named) {
          done = done.compose(sofar -> new Check(model, reads, Ref.subject(subject)).run(object, relation)
              .map(has -> {
                if (has != all) {
                  differ.add(subject);
                }
                return null;
              }));
        }
        return done.map(checked -> all ? SubjectSet.newBuilder().setAll(true).addAllExcept(differ).build()
            : SubjectSet.newBuilder().addAllSubjects(differ).build());
      });
    });
  }

  /** Gives a subject of the type that no tuple the list read names. */
  private Ref anyone() {
    String id = "spectrum_anyone";
    for (int n = 2; named.contains(type + ":" + id); n++) {
      id = "spectrum_anyone_" + n;
    }
    return new Ref(type, id, "");
  }

  /**
   * Visits relations of objects, {@code object#relation}, which a check reaches as deep as {@code depth}, and then the
   * relations that they reach. A relation is visited once.
   */
  private Future<Void> level(List<Ref> relations, int depth) {
    if (relations.isEmpty() || depth > Check.MAX_DEPTH) {
      return Future.succeededFuture();
    }
    List<Ref> next = new ArrayList<>();
    Future<Void> done = Future.succeededFuture();
    for (Ref set : relations) {
      Ref object = new Ref(set.type(), set.id(), "");
      done = done.compose(ignored -> model.rewrite(set.type(), set.relation())
          .map(rewrite -> visit(object, set.relation(), rewrite, next)).orElse(Future.succeededFuture()));
    }
    return done.compose(ignored -> level(next, depth + 1));
  }

  /** Visits a rewrite of a relation of an object: gathers the subjects its tuples name and the relations it reaches. */
  private Future<Void> visit(Ref object, String relation, Rewrite rewrite, List<Ref> next) {
    return switch (rewrite.getRewriteCase()) {
      case DIRECT -> reads.subjects(object.object(), relation).map(subjects -> {
        for (Ref ref : subjects) {
          if (!Definitions.allows(rewrite.getDirect(), ref)) {
            continue;
          }
          if (ref.userset()) {
            reach(ref, next);
          } else if (ref.type().equals(type) && !ref.wildcard()) {
            named.add(ref.text());
          }
        }
        return null;
      });
      case COMPUTED -> {
        reach(new Ref(object.type(), object.id(), rewrite.getComputed().getRelation()), next);
        yield Future.succeededFuture();
      }
      case FROM -> reads.subjects(object.object(), rewrite.getFrom().getTupleset()).map(subjects -> {
        subjects.stream().filter(ref -> !ref.userset() && !ref.wildcard()
            && model.allows(object.type(), rewrite.getFrom().getTupleset(), ref)).forEach(
                related -> reach(new Ref(related.type(), related.id(), rewrite.getFrom().getRelation()), next));
        return null;
      });
      case UNION -> visitAll(object, relation, rewrite.getUnion().getRewritesList(), next);
      case INTERSECTION -> visitAll(object, relation, rewrite.getIntersection().getRewritesList(), next);
      case DIFFERENCE -> visitAll(object, relation,
          List.of(rewrite.getDifference().getBase(), rewrite.getDifference().getSubtract()), next);
      case REWRITE_NOT_SET -> Future.succeededFuture();
    };
  }

  private Future<Void> visitAll(Ref object, String relation, List<Rewrite> rewrites, List<Ref> next) {
    Future<Void> done = Future.succeededFuture();
    for (Rewrite child : rewrites) {
      done = done.compose(ignored -> visit(object, relation, child, next));
    }
    return done;
  }

  private void reach(Ref set, List<Ref> next) {
    if (visited.add(set.text())) {
      next.add(set);
    }
  }
}
