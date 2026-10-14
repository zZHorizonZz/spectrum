package dev.horizon.spectrum;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.gt;
import static dev.horizon.basepoint.Filter.or;
import static dev.horizon.basepoint.Filter.startsWith;

import dev.horizon.basepoint.Filter;
import dev.horizon.basepoint.Query;
import dev.horizon.spectrum.v1.Assertion;
import dev.horizon.spectrum.v1.Change;
import dev.horizon.spectrum.v1.Model;
import dev.horizon.spectrum.v1.Operation;
import dev.horizon.spectrum.v1.Relationship;
import dev.horizon.spectrum.v1.SubjectSet;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * The relationships of a store, as rows of the relationship table.
 *
 * <p>
 * Writing is an insert that skips a relationship the table has, which its unique index finds. A check and a list read
 * the relationships of one relation of an object, or of some subjects, at a time, and read each once.
 */
final class RelationsImpl implements Relations {

  /** How many relationships one insert writes, below the parameters of any database. */
  private static final int RELATIONSHIPS_PER_WRITE = 400;

  private final RelationshipTable table;
  private final Definitions model;
  private final List<Relationship> contextual;

  RelationsImpl(RelationshipTable table, Definitions model, List<Relationship> contextual) {
    this.table = table;
    this.model = model;
    this.contextual = contextual;
  }

  @Override
  public Relations on(SqlClient client) {
    return new RelationsImpl(table.on(client), model, contextual);
  }

  @Override
  public Relations with(List<Relationship> relationships) {
    return new RelationsImpl(table, model, List.copyOf(new LinkedHashSet<>(relationships.stream().map(this::allowed)
        .toList())));
  }

  @Override
  public String store() {
    return table.store();
  }

  @Override
  public Model model() {
    return model.model();
  }

  @Override
  public Future<Boolean> write(Relationship relationship) {
    return write(List.of(relationship)).map(written -> written > 0);
  }

  @Override
  public Future<Long> write(List<Relationship> relationships) {
    List<Relationship> allowed = List.copyOf(new LinkedHashSet<>(relationships.stream().map(this::allowed).toList()));
    if (allowed.isEmpty()) {
      return Future.succeededFuture(0L);
    }
    return table.inTransaction(tx -> {
      Future<Long> written = Future.succeededFuture(0L);
      for (int start = 0; start < allowed.size(); start += RELATIONSHIPS_PER_WRITE) {
        List<Relationship> part = allowed.subList(start, Math.min(allowed.size(), start + RELATIONSHIPS_PER_WRITE));
        written = written.compose(sofar -> writeNew(tx, part).map(more -> sofar + more));
      }
      return written;
    });
  }

  /**
   * Writes the relationships of a part that the store does not have, and records their changes. A relationship that a
   * concurrent write wrote between the read and the insert fails the write, so that the changes hold exactly the
   * relationships that this write added.
   */
  private static Future<Long> writeNew(RelationshipTable tx, List<Relationship> part) {
    Filter[] keys = part.stream().map(relationship -> and(eq("object", relationship.getObject()),
        eq("relation", relationship.getRelation()), eq("subject", relationship.getSubject()))).toArray(Filter[]::new);
    return tx.read(tx.query().columns("object", "relation", "subject").where(or(keys))).compose(found -> {
      Set<Relationship> have = new HashSet<>(found.stream().map(RelationsImpl::key).toList());
      List<Relationship> fresh = part.stream().filter(relationship -> !have.contains(relationship)).toList();
      if (fresh.isEmpty()) {
        return Future.succeededFuture(0L);
      }
      return tx.execute(tx.insert(fresh)).compose(added -> {
        if (added != fresh.size()) {
          throw new IllegalStateException("a concurrent write wrote " + (fresh.size() - added) + " of the"
              + " relationships first; write them again");
        }
        return tx.changes().record(fresh, Operation.OPERATION_WRITE).map((long) fresh.size());
      });
    });
  }

  private static Relationship key(Relationship relationship) {
    return Relationship.newBuilder().setObject(relationship.getObject()).setRelation(relationship.getRelation())
        .setSubject(relationship.getSubject()).build();
  }

  @Override
  public Future<Boolean> delete(Relationship relationship) {
    return delete(List.of(relationship)).map(deleted -> deleted > 0);
  }

  @Override
  public Future<Long> delete(List<Relationship> relationships) {
    List<Relationship> valid = relationships.stream().map(this::valid).toList();
    return table.inTransaction(tx -> {
      List<Relationship> deleted = new ArrayList<>();
      Future<Void> done = Future.succeededFuture();
      for (Relationship relationship : valid) {
        done = done.compose(ignored -> delete(tx, relationship).map(count -> {
          if (count > 0) {
            deleted.add(relationship);
          }
          return null;
        }));
      }
      return done.compose(ignored -> tx.changes().record(deleted, Operation.OPERATION_DELETE))
          .map(ignored -> (long) deleted.size());
    });
  }

  private static Future<Long> delete(RelationshipTable in, Relationship relationship) {
    return in.execute(in.delete().where(and(eq("object", relationship.getObject()),
        eq("relation", relationship.getRelation()), eq("subject", relationship.getSubject()))));
  }

  @Override
  public Future<Page<Change>> changes(String type, Instant since, int size, String token) {
    if (!type.isEmpty() && !Ref.name(type)) {
      throw new IllegalArgumentException("the type '" + type + "' is not a name: at most 64 lower case letters,"
          + " digits and '_', starting with a letter");
    }
    if (size < 1) {
      throw new IllegalArgumentException("the size of a page is " + size + ", not at least 1");
    }
    return table.changes().read(type, since, size, token);
  }

  /** Validates an assertion: a check of it, with its contextual relationships, would run. */
  void validate(Assertion assertion) {
    Ref target = Ref.object(assertion.getObject());
    relationOf(target.type(), assertion.getRelation());
    subjectOf(assertion.getSubject());
    with(assertion.getContextualList());
  }

  @Override
  public Future<List<Relationship>> read(String object) {
    Ref.object(object);
    return table.read(table.query().where(eq("object", object)).orderBy("relation").orderBy("subject"));
  }

  @Override
  public Future<List<Relationship>> read(String object, String relation) {
    Ref.object(object);
    return table.read(table.query().where(and(eq("object", object), eq("relation", relation))).orderBy("subject"));
  }

  @Override
  public Future<Page<Relationship>> read(Relationship filter, int size, String token) {
    if (size < 1) {
      throw new IllegalArgumentException("the size of a page is " + size + ", not at least 1");
    }
    Query query = table.query();
    String object = filter.getObject();
    if (object.endsWith(":") && Ref.name(object.substring(0, object.length() - 1))) {
      query = query.where(startsWith("object", object));
    } else if (!object.isEmpty()) {
      query = query.where(eq("object", Ref.object(object).text()));
    }
    if (!filter.getRelation().isEmpty()) {
      query = query.where(eq("relation", name(filter.getRelation())));
    }
    if (!filter.getSubject().isEmpty()) {
      query = query.where(eq("subject", Ref.subject(filter.getSubject()).text()));
    }
    if (!token.isEmpty()) {
      List<String> after = Cursor.of(token, 3).keys();
      query = query.where(or(gt("object", after.get(0)),
          and(eq("object", after.get(0)), gt("relation", after.get(1))),
          and(eq("object", after.get(0)), eq("relation", after.get(1)), gt("subject", after.get(2)))));
    }
    return table.read(query.orderBy("object").orderBy("relation").orderBy("subject").limit(size + 1L))
        .map(rows -> {
          if (rows.size() <= size) {
            return new Page<>(rows, "");
          }
          List<Relationship> page = new ArrayList<>(rows.subList(0, size));
          Relationship last = page.getLast();
          return new Page<>(page, new Cursor(List.of(last.getObject(), last.getRelation(), last.getSubject()))
              .token());
        });
  }

  @Override
  public Future<Boolean> check(String subject, String relation, String object) {
    Ref target = Ref.object(object);
    relationOf(target.type(), relation);
    return check(reads(), subjectOf(subject)).apply(target, relation);
  }

  @Override
  public Future<List<String>> listObjects(String subject, String relation, String type) {
    relationOf(type, relation);
    Reads reads = reads();
    Ref who = subjectOf(subject);
    return new ListObjects(model, reads, who, relation, type, check(reads, who)).run();
  }

  @Override
  public Future<SubjectSet> listSubjects(String object, String relation, String type) {
    Ref target = Ref.object(object);
    relationOf(target.type(), relation);
    typeOf(type);
    return new ListSubjects(model, reads(), type).run(target, relation);
  }

  private static String name(String relation) {
    if (!Ref.name(relation)) {
      throw new IllegalArgumentException("the relation '" + relation + "' is not a name: at most 64 lower case"
          + " letters, digits and '_', starting with a letter");
    }
    return relation;
  }

  /** Gives the object, relation and subject of a relationship, which must be valid. */
  private Relationship valid(Relationship relationship) {
    Ref.object(relationship.getObject());
    name(relationship.getRelation());
    Ref.subject(relationship.getSubject());
    return Relationship.newBuilder().setObject(relationship.getObject()).setRelation(relationship.getRelation())
        .setSubject(relationship.getSubject()).build();
  }

  /** Gives the object, relation and subject of a relationship that the model allows. */
  private Relationship allowed(Relationship relationship) {
    Relationship valid = valid(relationship);
    Ref object = Ref.object(valid.getObject());
    relationOf(object.type(), valid.getRelation());
    Ref subject = subjectOf(valid.getSubject());
    if (!model.allows(object.type(), valid.getRelation(), subject)) {
      throw new IllegalArgumentException("the model does not allow " + valid.getObject() + "#" + valid.getRelation()
          + "@" + valid.getSubject() + ": no direct rewrite of " + object.type() + "#" + valid.getRelation()
          + " allows " + (subject.userset() ? subject.type() + "#" + subject.relation()
              : subject.wildcard() ? subject.type() + ":*" : subject.type()));
    }
    return valid;
  }

  /** Reads a subject whose type, and relation if it has one, the model has. */
  private Ref subjectOf(String text) {
    Ref subject = Ref.subject(text);
    typeOf(subject.type());
    if (subject.userset()) {
      relationOf(subject.type(), subject.relation());
    }
    return subject;
  }

  /**
   * Gives the check of a subject. Every object of a type, {@code user:*}, has a relation when all of them are its
   * subjects, with none left out.
   */
  private BiFunction<Ref, String, Future<Boolean>> check(Reads reads, Ref subject) {
    if (subject.wildcard()) {
      return (object, relation) -> new ListSubjects(model, reads, subject.type()).run(object, relation)
          .map(subjects -> subjects.getAll() && subjects.getExceptCount() == 0);
    }
    Check check = new Check(model, reads, subject);
    return check::run;
  }

  private void typeOf(String type) {
    if (!model.has(type)) {
      throw new IllegalArgumentException("the model has no type '" + type + "'");
    }
  }

  private void relationOf(String type, String relation) {
    typeOf(type);
    if (model.rewrite(type, relation).isEmpty()) {
      throw new IllegalArgumentException("the type '" + type + "' has no relation '" + relation + "'");
    }
  }

  private Reads reads() {
    return new Reads(table, contextual);
  }
}
