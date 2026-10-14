package dev.horizon.spectrum.storage.sql;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;

import dev.horizon.spectrum.openfga.v1.Assertion;
import dev.horizon.spectrum.openfga.v1.Assertions;
import dev.horizon.spectrum.storage.AssertionsBackend;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The assertions of the {@code assertion} table: one row per model, which holds all its assertions as one serialized
 * message.
 */
public final class SqlAssertionsBackend implements AssertionsBackend {

  private static final String ASSERTION = "assertion";
  private static final String[] KEY = { "store", "authorization_model_id" };

  private final SqlDatabase database;

  public SqlAssertionsBackend(SqlDatabase database) {
    this.database = database;
  }

  @Override
  public Future<Void> write(String store, String modelId, List<Assertion> assertions) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("store", store);
    row.put("authorization_model_id", modelId);
    row.put("assertions", database.types().binary(Assertions.newBuilder().addAllAssertions(assertions).build()
        .toByteArray()));
    return database.run(database.table(ASSERTION).insert(row).replace(KEY)).mapEmpty();
  }

  @Override
  public Future<List<Assertion>> read(String store, String modelId) {
    return database.run(database.table(ASSERTION).query().where(and(eq("store", store),
        eq("authorization_model_id", modelId)))).map(rows -> {
          for (Row row : rows) {
            return database.types().message(row, "assertions", Assertions.parser()).getAssertionsList();
          }
          return List.of();
        });
  }
}
