package dev.horizon.spectrum.storage;

import static dev.horizon.basepoint.expression.Expression.now;
import static dev.horizon.basepoint.filter.Filter.and;
import static dev.horizon.basepoint.filter.Filter.eq;
import static dev.horizon.basepoint.filter.Filter.gte;
import static dev.horizon.basepoint.filter.Filter.in;
import static dev.horizon.basepoint.filter.Filter.isNull;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.basepoint.Query;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.spectrum.openfga.v1.Store;
import dev.horizon.spectrum.schema.v1.Tables;
import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;

/**
 * Creates, reads, renames and deletes stores. A delete sets {@code deleted_at} and keeps the rows of the store, and
 * reads leave out the stores that have it. The database writes the times: the time of the creation as the creation and
 * the update time, the time of each update as the update time, and the time of the delete.
 *
 * @param database the database of the {@code store} table
 */
public record StoresBackend(Database database) implements Backend<Tables.Store, String> {

  @Override
  public Tables.Store prototype() {
    return Tables.Store.getDefaultInstance();
  }

  /**
   * Creates a store.
   *
   * @param id the ID of the store, a ULID
   * @param name the name of the store
   * @return the store, with the time of its creation as its creation and update times
   */
  public Future<Store> create(String id, String name) {
    return insert(Tables.Store.newBuilder().setId(id).setName(name).build()).map(this::store);
  }

  /**
   * Reads a store.
   *
   * @param id the ID of the store
   * @return the store, or empty if there is none with the ID or it is deleted
   */
  public Future<Optional<Store>> get(String id) {
    return first(query().where(and(eq("id", id), isNull("deleted_at")))).map(found -> found.map(this::store));
  }

  /**
   * Lists the stores that are not deleted, in order of ID.
   *
   * @param ids the IDs of the stores to list, or none for every store
   * @param name the name of the stores to list, or empty for every name
   * @param page the page, whose cursor is the ID of its first store
   * @return the stores of the page, with the token of the next page
   */
  public Future<Page<Store>> list(List<String> ids, String name, PageQuery page) {
    Query query = query().where(isNull("deleted_at")).orderBy("id").limit(page.size() + 1L);
    if (!ids.isEmpty()) {
      query = query.where(in("id", ids));
    }
    if (!name.isEmpty()) {
      query = query.where(eq("name", name));
    }
    if (!page.from().isEmpty()) {
      query = query.where(gte("id", page.from()));
    }
    return list(query).map(rows -> {
      List<Store> stores = rows.stream().map(this::store).toList();
      return stores.size() > page.size() ? page.page(stores.subList(0, page.size()), List.of(stores.get(page
          .size()).getId())) : page.page(stores, List.of());
    });
  }

  /**
   * Renames a store. The database sets its update time to now.
   *
   * @param id the ID of the store
   * @param name the new name
   * @return the renamed store, or empty if there is none with the ID or it is deleted
   */
  public Future<Optional<Store>> update(String id, String name) {
    return returning(update().set("name", name).where(and(eq("id", id), isNull("deleted_at"))))
        .map(rows -> rows.stream().findFirst().map(this::store));
  }

  /**
   * Deletes a store. Its models, tuples, changes and assertions stay in their tables.
   *
   * @param id the ID of the store
   * @return a future that completes when the store is deleted, also if there is none with the ID
   */
  public Future<Void> delete(String id) {
    return execute(update().set("deleted_at", now()).where(eq("id", id))).mapEmpty();
  }

  private Store store(Tables.Store row) {
    return Store.newBuilder().setId(row.getId()).setName(row.getName()).setCreatedAt(row.getCreatedAt())
        .setUpdatedAt(row.getUpdatedAt()).build();
  }
}
