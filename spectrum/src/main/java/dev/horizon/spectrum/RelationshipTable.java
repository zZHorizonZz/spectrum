package dev.horizon.spectrum;

import static dev.horizon.basepoint.Filter.eq;

import dev.horizon.basepoint.Buildable;
import dev.horizon.basepoint.DeleteQuery;
import dev.horizon.basepoint.InsertQuery;
import dev.horizon.basepoint.Query;
import dev.horizon.spectrum.v1.Relationship;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The rows of one store in the relationship table.
 */
final class RelationshipTable {

  /** The name of the table, as {@link Relationship} declares it. */
  static final String NAME = "spectrum_relationship";

  /** The columns that a write of a relationship sets, in key order. */
  static final String[] KEY = { "store_id", "object", "relation", "subject" };

  private final Database database;
  private final String store;
  private final UlidGenerator ids;

  RelationshipTable(Database database, String store, UlidGenerator ids) {
    this.database = database;
    this.store = store;
    this.ids = ids;
  }

  /** Gives the same table through another client. */
  RelationshipTable on(SqlClient client) {
    return new RelationshipTable(database.on(client), store, ids);
  }

  /** Gives the changes of the store, through the same client. */
  ChangeLog changes() {
    return new ChangeLog(database, store, ids);
  }

  /** Gives the store. */
  String store() {
    return store;
  }

  /** Starts a query of the rows of the store. */
  Query query() {
    return database.table(NAME).query().where(eq("store_id", store));
  }

  /** Starts a delete of rows of the store. */
  DeleteQuery delete() {
    return database.table(NAME).delete().where(eq("store_id", store));
  }

  /** Starts an insert of relationships into the store, which skips those that the table has. */
  InsertQuery insert(List<Relationship> relationships) {
    List<Map<String, String>> rows = relationships.stream().map(relationship -> {
      Map<String, String> row = new LinkedHashMap<>();
      row.put("store_id", store);
      row.put("object", relationship.getObject());
      row.put("relation", relationship.getRelation());
      row.put("subject", relationship.getSubject());
      return row;
    }).toList();
    return database.table(NAME).insert(rows).ignore(KEY);
  }

  /** Reads the relationships that a query selects. */
  Future<List<Relationship>> read(Query query) {
    return database.rows(query).map(rows -> {
      List<Relationship> relationships = new ArrayList<>(rows.size());
      for (Row row : rows) {
        relationships.add(relationship(row));
      }
      return relationships;
    });
  }

  /** Runs a write, and gives the number of rows it wrote. */
  Future<Long> execute(Buildable statement) {
    return database.count(statement);
  }

  /** Runs work in one transaction. */
  <T> Future<T> inTransaction(Function<RelationshipTable, Future<T>> work) {
    return database.inTransaction(in -> work.apply(new RelationshipTable(in, store, ids)));
  }

  /** Reads a relationship from the columns of a row that it has. */
  private Relationship relationship(Row row) {
    Relationship.Builder relationship = Relationship.newBuilder().setId(database.text(row, "id"))
        .setStoreId(database.text(row, "store_id")).setObject(database.text(row, "object"))
        .setRelation(database.text(row, "relation")).setSubject(database.text(row, "subject"));
    var created = database.timestamp(row, "create_time");
    if (created != null) {
      relationship.setCreateTime(created);
    }
    return relationship.build();
  }
}
