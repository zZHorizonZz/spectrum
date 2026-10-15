package dev.horizon.spectrum.storage.sql;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.gte;
import static dev.horizon.basepoint.Filter.in;
import static dev.horizon.basepoint.Filter.isNull;

import com.google.protobuf.Timestamp;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.basepoint.Query;
import dev.horizon.spectrum.openfga.v1.Store;
import dev.horizon.spectrum.storage.PageQuery;
import dev.horizon.spectrum.storage.StoresBackend;
import io.vertx.core.Future;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The stores of the {@code store} table. A delete sets {@code deleted_at}, and reads leave out the rows that have it.
 */
public final class SqlStoresBackend implements StoresBackend {

  private static final String STORE = "spectrum_store";

  private final SqlDatabase database;

  public SqlStoresBackend(SqlDatabase database) {
    this.database = database;
  }

  @Override
  public Future<Store> create(String id, String name) {
    Instant now = database.now();
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", id);
    row.put("name", name);
    row.put("created_at", database.types().time(now));
    row.put("updated_at", database.types().time(now));
    Timestamp created = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();
    return database.run(database.table(STORE).insert(row)).map(ignored -> Store.newBuilder().setId(id).setName(name)
        .setCreatedAt(created).setUpdatedAt(created).build());
  }

  @Override
  public Future<Optional<Store>> get(String id) {
    return stores(database.table(STORE).query().where(and(eq("id", id), isNull("deleted_at"))))
        .map(stores -> stores.stream().findFirst());
  }

  @Override
  public Future<Page<Store>> list(List<String> ids, String name, PageQuery page) {
    Query query = database.table(STORE).query().where(isNull("deleted_at")).orderBy("id").limit(page.size() + 1L);
    if (!ids.isEmpty()) {
      query = query.where(in("id", ids));
    }
    if (!name.isEmpty()) {
      query = query.where(eq("name", name));
    }
    if (!page.from().isEmpty()) {
      query = query.where(gte("id", page.from()));
    }
    return stores(query).map(stores -> stores.size() > page.size() ? page.page(stores.subList(0, page.size()),
        List.of(stores.get(page.size()).getId())) : page.page(stores, List.of()));
  }

  @Override
  public Future<Optional<Store>> update(String id, String name) {
    return database.run(database.table(STORE).update().set("name", name).set("updated_at", database.types()
        .time(database.now())).where(and(eq("id", id), isNull("deleted_at")))).compose(updated -> get(id));
  }

  @Override
  public Future<Void> delete(String id) {
    return database.run(database.table(STORE).update().set("deleted_at", database.types().time(database.now()))
        .where(eq("id", id))).mapEmpty();
  }

  private Future<List<Store>> stores(Query query) {
    SqlTypes types = database.types();
    return database.run(query).map(rows -> {
      List<Store> stores = new ArrayList<>(rows.size());
      rows.forEach(row -> stores.add(Store.newBuilder().setId(types.text(row, "id")).setName(types.text(row, "name"))
          .setCreatedAt(types.timestamp(row, "created_at")).setUpdatedAt(types.timestamp(row, "updated_at"))
          .build()));
      return stores;
    });
  }
}
