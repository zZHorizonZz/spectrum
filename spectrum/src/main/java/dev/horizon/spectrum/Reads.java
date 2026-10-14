package dev.horizon.spectrum;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.in;

import dev.horizon.spectrum.v1.Relationship;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads the relationships for one check or one list, with the contextual relationships as if the table had them. Each
 * relation of an object is read once.
 */
final class Reads {

  /** How many subjects one read of the relationships of subjects names, below the parameters of any database. */
  private static final int SUBJECTS_PER_READ = 500;

  private final RelationshipTable table;
  private final List<Relationship> contextual;
  private final Map<String, Future<List<Ref>>> subjects = new HashMap<>();

  Reads(RelationshipTable table, List<Relationship> contextual) {
    this.table = table;
    this.contextual = contextual;
  }

  /** Reads the subjects of a relation of an object, in order. */
  Future<List<Ref>> subjects(String object, String relation) {
    return subjects.computeIfAbsent(object + "#" + relation, key -> table.read(table.query().columns("subject")
        .where(and(eq("object", object), eq("relation", relation))).orderBy("subject")).map(records -> {
          if (contextual.isEmpty()) {
            return records.stream().map(record -> Ref.subject(record.getSubject())).toList();
          }
          TreeMap<String, Ref> all = new TreeMap<>();
          records.forEach(record -> all.put(record.getSubject(), Ref.subject(record.getSubject())));
          contextual.stream().filter(c -> c.getObject().equals(object) && c.getRelation().equals(relation))
              .forEach(c -> all.put(c.getSubject(), Ref.subject(c.getSubject())));
          return List.copyOf(all.values());
        }));
  }

  /** Reads the relationships whose subject is one of some subjects, a part of them at a time. */
  Future<List<Relationship>> of(List<String> subjects) {
    List<Relationship> all = new ArrayList<>();
    Future<Void> done = Future.succeededFuture();
    for (int start = 0; start < subjects.size(); start += SUBJECTS_PER_READ) {
      List<String> part = subjects.subList(start, Math.min(subjects.size(), start + SUBJECTS_PER_READ));
      done = done.compose(ignored -> table.read(table.query().columns("object", "relation", "subject")
          .where(in("subject", part))).map(records -> {
            all.addAll(records);
            return null;
          }));
    }
    if (contextual.isEmpty()) {
      return done.map(all);
    }
    Set<String> named = new HashSet<>(subjects);
    return done.map(ignored -> {
      contextual.stream().filter(c -> named.contains(c.getSubject())).forEach(all::add);
      return all;
    });
  }
}
