package dev.horizon.spectrum;

import dev.horizon.basepoint.Dialect;
import dev.horizon.spectrum.v1.Change;
import dev.horizon.spectrum.v1.Model;
import dev.horizon.spectrum.v1.Relationship;
import dev.horizon.spectrum.v1.SubjectSet;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.SqlConnection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The relationships of a database, Zanzibar's relation tuples, and what they give by a {@link Model}.
 *
 * <p>
 * A {@link Relationship} says that a subject has a relation to an object; the model says which other subjects the
 * relation has: those of another relation, of a relation of related objects, and their union, intersection and
 * difference. {@link #check} answers whether a subject has a relation to an object; {@link #listObjects} and
 * {@link #listSubjects} list them, with the same answers.
 *
 * <p>
 * The relationships are rows of one store in the relationship table, which {@link Relationship} declares and the
 * migrations in {@code spectrum/migration} create. Basepoint writes the statements in the SQL of a {@link Dialect}, and a Vert.x
 * {@link SqlClient} runs them. A relationship written on a connection in a transaction holds with the rows it guards: a
 * rollback means it never existed.
 *
 * <p>
 * Each method throws an {@link IllegalArgumentException} at once if an object, a relation or a subject is not valid or
 * the model does not have it. A check and a list follow the model at most 25 levels deep, and fail with an
 * {@link IllegalStateException} when they would go deeper. A relation that a cycle through a difference leaves
 * undecided is not given.
 */
public interface Relations {

  /**
   * Opens the relationships of a store in a database with a model.
   *
   * @param client the client that runs the statements: a {@link Pool}, or a {@link SqlConnection}. A MariaDB client
   * counts affected rows, {@code MySQLConnectOptions.setUseAffectedRows(true)}, so that a write of a relationship that
   * is there counts as none
   * @param dialect the SQL of the database, with the placeholders that the client takes, for example
   * {@code ParameterStyle.DOLLAR} for the PostgreSQL client
   * @param store the store: the id of one that {@link Stores} created, or any other 1 to 32 letters, digits, {@code _}
   * and {@code -}, such as {@code default}
   * @param model how the relations of each type combine
   * @return the relationships
   * @throws IllegalArgumentException if the store is not valid, or the model names a type or a relation that it does
   * not have
   */
  static Relations open(SqlClient client, Dialect dialect, String store, Model model) {
    return new RelationsImpl(new RelationshipTable(new Database(client, dialect), Ref.store(store),
        new UlidGenerator(Clock.systemUTC())), new Definitions(model), List.of());
  }

  /**
   * Gives the same relationships through another client, for example a connection in a transaction.
   *
   * @param client the client
   * @return the relationships through the client
   */
  Relations on(SqlClient client);

  /**
   * Gives the same relationships with contextual relationships: a check and a list count them as if the table had
   * them, but nothing writes them. A read gives only the relationships of the table.
   *
   * @param contextual the contextual relationships, such as those that a request holds
   * @return the relationships with the contextual relationships, in place of those this one has
   * @throws IllegalArgumentException if the model does not allow one of them
   */
  Relations with(List<Relationship> contextual);

  /**
   * Gives the store.
   *
   * @return the store
   */
  String store();

  /**
   * Gives the model.
   *
   * @return the model
   */
  Model model();

  /**
   * Writes a relationship, once. Only its object, relation and subject are written.
   *
   * @param relationship the relationship
   * @return true if it was written, false if it was there
   * @throws IllegalArgumentException if the model does not allow it: its relation has no {@code direct} rewrite that
   * allows the subject
   */
  Future<Boolean> write(Relationship relationship);

  /**
   * Writes relationships, each once, in one transaction.
   *
   * @param relationships the relationships
   * @return the number that were written
   * @throws IllegalArgumentException if the model does not allow one of them
   */
  Future<Long> write(List<Relationship> relationships);

  /**
   * Deletes a relationship, by its object, relation and subject.
   *
   * @param relationship the relationship
   * @return true if it was deleted, false if it was not there
   */
  Future<Boolean> delete(Relationship relationship);

  /**
   * Deletes relationships, in one transaction.
   *
   * @param relationships the relationships
   * @return the number that were deleted
   */
  Future<Long> delete(List<Relationship> relationships);

  /**
   * Reads the relationships of an object.
   *
   * @param object the object, for example {@code folder:7}
   * @return the relationships, by relation and subject
   */
  Future<List<Relationship>> read(String object);

  /**
   * Reads the relationships of a relation of an object.
   *
   * @param object the object, for example {@code folder:7}
   * @param relation the relation, for example {@code parent}
   * @return the relationships, by subject
   */
  Future<List<Relationship>> read(String object, String relation);

  /**
   * Reads a page of the relationships that a filter selects, in order of object, relation and subject.
   *
   * @param filter the filter: an empty field selects every value, and an object of a type alone, such as {@code doc:},
   * selects every object of the type
   * @param size the most relationships the page has, at least 1
   * @param token the token of the page, from {@link Page#next()}, or empty for the first page
   * @return the page
   * @throws IllegalArgumentException if the filter, the size or the token is not valid
   */
  Future<Page<Relationship>> read(Relationship filter, int size, String token);

  /**
   * Reads a page of the changes of the store, in the order that they were made: each write of a relationship that the
   * store did not have, and each delete of one that it had. A write and a delete record their changes in their own
   * transaction, so a rollback records none.
   *
   * @param type the type of the objects whose changes to read, such as {@code doc}, or empty for every type
   * @param since the earliest time of a change to read, when the token is empty; {@link Instant#EPOCH} for all
   * @param size the most changes the page has, at least 1
   * @param token the token of the page, from {@link Page#next()}, or empty to start at {@code since}
   * @return the page; its token reads the changes after it, and is the given token when no change follows, so that a
   * reader can ask again later
   * @throws IllegalArgumentException if the type, the size or the token is not valid, or the token is one of another
   * type
   */
  Future<Page<Change>> changes(String type, Instant since, int size, String token);

  /**
   * Checks if a subject has a relation to an object.
   *
   * @param subject the subject, for example {@code user:ana} or {@code group:eng#member}; {@code user:*} asks if every
   * object of the type has the relation
   * @param relation the relation, for example {@code viewer}
   * @param object the object, for example {@code doc:9}
   * @return true if the subject has the relation
   */
  Future<Boolean> check(String subject, String relation, String object);

  /**
   * Lists the objects of a type to which a subject has a relation.
   *
   * @param subject the subject, for example {@code user:ana}; {@code user:*} lists the objects that every object of
   * the type has the relation to
   * @param relation the relation, for example {@code viewer}
   * @param type the type of the objects, for example {@code doc}
   * @return the objects, in order
   */
  Future<List<String>> listObjects(String subject, String relation, String type);

  /**
   * Lists the subjects of a type that have a relation to an object: some of them, or every one of them but some.
   *
   * @param object the object, for example {@code doc:9}
   * @param relation the relation, for example {@code viewer}
   * @param type the type of the subjects, for example {@code user}
   * @return the subjects
   */
  Future<SubjectSet> listSubjects(String object, String relation, String type);
}
