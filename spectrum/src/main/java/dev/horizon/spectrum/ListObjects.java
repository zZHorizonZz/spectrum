package dev.horizon.spectrum;

import dev.horizon.spectrum.v1.Relationship;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;

/**
 * Lists the objects of one type to which a subject has a relation, from the subject up the model, as OpenFGA's
 * ListObjects does.
 *
 * <p>
 * Each level is one read of the tuples whose subject is one of the sets of subjects that the level before reached,
 * {@code object#relation}, or the object of one of them, for a {@code from}. A set is reached once, so the list stops
 * at a cycle. Going up, an intersection or a difference cannot be decided, so when the model has one, each object found
 * is checked. Every object of a type, {@code user:*}, finds the objects that a tuple of it reaches, which are the only
 * ones that every object of the type can have the relation to.
 */
final class ListObjects {

  private final Definitions model;
  private final Reads reads;
  private final Ref subject;
  private final BiFunction<Ref, String, Future<Boolean>> check;
  private final String type;
  private final String relation;
  private final Set<String> reached = new HashSet<>();
  private final Set<String> found = new TreeSet<>();
  private List<Ref> level = new ArrayList<>();

  ListObjects(Definitions model, Reads reads, Ref subject, String relation, String type,
      BiFunction<Ref, String, Future<Boolean>> check) {
    this.model = model;
    this.reads = reads;
    this.subject = subject;
    this.check = check;
    this.relation = relation;
    this.type = type;
  }

  /** Lists the objects, in order. */
  Future<List<String>> run() {
    if (subject.userset()) {
      reach(subject);
    } else {
      level.add(subject);
      if (!subject.wildcard()) {
        // A tuple of every object of the type names the subject too.
        level.add(new Ref(subject.type(), "*", ""));
      }
    }
    return levels(1).compose(ignored -> model.monotone() ? Future.succeededFuture(List.copyOf(found)) : checked());
  }

  private Future<Void> levels(int depth) {
    if (level.isEmpty()) {
      return Future.succeededFuture();
    }
    if (depth > Check.MAX_DEPTH) {
      return Future.failedFuture(Check.tooDeep());
    }
    Set<String> subjects = new LinkedHashSet<>();
    Map<String, List<Ref>> byObject = new HashMap<>();
    for (Ref ref : level) {
      subjects.add(ref.text());
      if (ref.userset()) {
        subjects.add(ref.object());
        byObject.computeIfAbsent(ref.object(), key -> new ArrayList<>()).add(ref);
      }
    }
    Set<String> levelTexts = new HashSet<>(level.stream().map(Ref::text).toList());
    level = new ArrayList<>();
    return reads.of(List.copyOf(subjects)).compose(records -> {
      for (Relationship record : records) {
        Ref object = Ref.object(record.getObject());
        Ref named = Ref.subject(record.getSubject());
        if (!model.allows(object.type(), record.getRelation(), named)) {
          continue;
        }
        if (levelTexts.contains(named.text())) {
          reach(new Ref(object.type(), object.id(), record.getRelation()));
        }
        // The tuple names a related object, for the relations that read a relation of it with from.
        for (Ref set : byObject.getOrDefault(named.text(), List.of())) {
          for (Ref rule : model.fromRules(record.getRelation(), set.relation())) {
            if (rule.type().equals(object.type())) {
              reach(new Ref(object.type(), object.id(), rule.relation()));
            }
          }
        }
      }
      return levels(depth + 1);
    });
  }

  /** Reaches a set of subjects, {@code object#relation}, and the relations of the object computed from it. */
  private void reach(Ref set) {
    if (!reached.add(set.text())) {
      return;
    }
    level.add(set);
    if (set.type().equals(type) && set.relation().equals(relation)) {
      found.add(set.object());
    }
    for (String computed : model.computedFrom(set.type(), set.relation())) {
      reach(new Ref(set.type(), set.id(), computed));
    }
  }

  /** Keeps the objects found for which a check holds, since an intersection or a difference can leave them out. */
  private Future<List<String>> checked() {
    List<String> kept = new ArrayList<>();
    Future<Void> done = Future.succeededFuture();
    for (String object : found) {
      done = done.compose(ignored -> check.apply(Ref.object(object), relation)
          .map(holds -> {
            if (holds) {
              kept.add(object);
            }
            return null;
          }));
    }
    return done.map(kept);
  }
}
