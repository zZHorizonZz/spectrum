package dev.horizon.spectrum;

import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.gt;
import static dev.horizon.basepoint.Filter.gte;
import static dev.horizon.basepoint.Filter.startsWith;

import dev.horizon.basepoint.Query;
import dev.horizon.spectrum.v1.Change;
import dev.horizon.spectrum.v1.Operation;
import dev.horizon.spectrum.v1.Relationship;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The changes of one store in the change table: each write of a relationship that the store did not have, and each
 * delete of one that it had, in order of id.
 */
final class ChangeLog {

  /** The name of the table, as {@link Change} declares it. */
  static final String NAME = "spectrum_change";

  /** How many changes one insert writes, below the parameters of any database. */
  private static final int CHANGES_PER_WRITE = 300;

  private final Database database;
  private final String store;
  private final UlidGenerator ids;

  ChangeLog(Database database, String store, UlidGenerator ids) {
    this.database = database;
    this.store = store;
    this.ids = ids;
  }

  /** Records changes of relationships, in the order given. */
  Future<Void> record(List<Relationship> relationships, Operation operation) {
    List<Map<String, String>> rows = relationships.stream().map(relationship -> {
      Map<String, String> row = new LinkedHashMap<>();
      row.put("id", ids.next());
      row.put("store_id", store);
      row.put("object", relationship.getObject());
      row.put("relation", relationship.getRelation());
      row.put("subject", relationship.getSubject());
      row.put("operation", operation.name());
      return row;
    }).toList();
    Future<Void> done = Future.succeededFuture();
    for (int start = 0; start < rows.size(); start += CHANGES_PER_WRITE) {
      List<Map<String, String>> part = rows.subList(start, Math.min(rows.size(), start + CHANGES_PER_WRITE));
      done = done.compose(ignored -> database.count(database.table(NAME).insert(part)).mapEmpty());
    }
    return done;
  }

  /**
   * Reads a page of changes, in order of id.
   *
   * @param type the type of the objects whose changes to read, or empty for every type
   * @param since the earliest time of a change to read, if no token is given
   * @param size the most changes the page has
   * @param token the token of the page, or empty to start at {@code since}
   * @return the page, whose token reads the changes after it: the given token if no change follows
   */
  Future<Page<Change>> read(String type, Instant since, int size, String token) {
    Query query = database.table(NAME).query().where(eq("store_id", store));
    if (!type.isEmpty()) {
      query = query.where(startsWith("object", type + ":"));
    }
    if (token.isEmpty()) {
      query = query.where(gte("id", ids.floor(since)));
    } else {
      List<String> keys = Cursor.of(token, 2).keys();
      if (!keys.get(1).equals(type)) {
        throw new IllegalArgumentException("the page token is one of the changes of the type '" + keys.get(1)
            + "', not '" + type + "'");
      }
      query = query.where(gt("id", keys.get(0)));
    }
    return database.rows(query.orderBy("id").limit(size)).map(rows -> {
      List<Change> changes = new ArrayList<>(rows.size());
      for (Row row : rows) {
        changes.add(change(row));
      }
      return new Page<>(changes, changes.isEmpty() ? token
          : new Cursor(List.of(changes.getLast().getId(), type))
              .token());
    });
  }

  private Change change(Row row) {
    Change.Builder change = Change.newBuilder().setId(database.text(row, "id")).setStoreId(database.text(row, "store_id"))
        .setObject(database.text(row, "object")).setRelation(database.text(row, "relation"))
        .setSubject(database.text(row, "subject")).setOperation(Operation.valueOf(database.text(row, "operation")));
    var created = database.timestamp(row, "create_time");
    if (created != null) {
      change.setCreateTime(created);
    }
    return change.build();
  }
}
