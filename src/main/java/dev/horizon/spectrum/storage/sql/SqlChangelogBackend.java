package dev.horizon.spectrum.storage.sql;

import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.gt;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.basepoint.Query;
import dev.horizon.spectrum.openfga.v1.TupleChange;
import dev.horizon.spectrum.storage.ChangelogBackend;
import dev.horizon.spectrum.storage.PageQuery;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import java.util.ArrayList;
import java.util.List;

public final class SqlChangelogBackend implements ChangelogBackend {

  private static final String CHANGELOG = "changelog";

  private final SqlDatabase database;
  private final TupleRows rows;

  public SqlChangelogBackend(SqlDatabase database) {
    this.database = database;
    this.rows = new TupleRows(database.types());
  }

  @Override
  public Future<Page<TupleChange>> read(String store, String objectType, String after, PageQuery page) {
    Query query = database.table(CHANGELOG).query().where(eq("store", store)).orderBy("ulid").limit(page.size());
    if (!objectType.isEmpty()) {
      query = query.where(eq("object_type", objectType));
    }
    if (!after.isEmpty()) {
      query = query.where(gt("ulid", after));
    }
    return database.run(query).map(found -> {
      List<TupleChange> changes = new ArrayList<>();
      String last = "";
      for (Row row : found) {
        changes.add(rows.change(row));
        last = database.types().text(row, "ulid");
      }
      return page.page(changes, changes.isEmpty() ? List.of() : List.of(last, objectType));
    });
  }
}
