package dev.horizon.spectrum.storage;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.gte;
import static dev.horizon.basepoint.Filter.in;
import static dev.horizon.basepoint.Filter.isNull;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.cinder.Assignments;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.Query;
import dev.horizon.spectrum.openfga.v1.Store;
import dev.horizon.spectrum.schema.v1.Tables;
import io.vertx.core.Future;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Creates, reads, renames and deletes stores. A delete sets {@code deleted_at} and keeps the rows of the store, and
 * reads leave out the stores that have it.
 *
 * @param database the database of the {@code store} table
 * @param clock the clock of the creation, update and delete times
 */
public record StoresBackend(Database database, Clock clock) implements Backend<Tables.Store, String> {

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
    Timestamp now = now();
    return insert(Tables.Store.newBuilder().setId(id).setName(name).setCreatedAt(now)
        .setUpdatedAt(now).build()).map(this::store);
  }

  /**
   * Reads a store.
   *
   * @param id the ID of the store
   * @return the store, or empty if there is none with the ID or it is deleted
   */
  public Future<Optional<Store>> get(String id) {
    return find(and(eq("id", id), isNull("deleted_at"))).first().map(found -> found.map(this::store));
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
    Query<Tables.Store> query = find(isNull("deleted_at")).orderBy("id")
        .limit(page.size() + 1L);
    if (!ids.isEmpty()) {
      query = query.where(in("id", ids));
    }
    if (!name.isEmpty()) {
      query = query.where(eq("name", name));
    }
    if (!page.from().isEmpty()) {
      query = query.where(gte("id", page.from()));
    }
    return query.list().map(rows -> {
      List<Store> stores = rows.stream().map(this::store).toList();
      return stores.size() > page.size() ? page.page(stores.subList(0, page.size()), List.of(stores.get(page
          .size()).getId())) : page.page(stores, List.of());
    });
  }

  /**
   * Renames a store, and sets its update time to now.
   *
   * @param id the ID of the store
   * @param name the new name
   * @return the renamed store, or empty if there is none with the ID or it is deleted
   */
  public Future<Optional<Store>> update(String id, String name) {
    return update().where(and(eq("id", id), isNull("deleted_at"))).set(Assignments.none().set("name", name)
        .set("updated_at", now())).returning().map(rows -> rows.stream().findFirst().map(this::store));
  }

  /**
   * Deletes a store. Its models, tuples, changes and assertions stay in their tables.
   *
   * @param id the ID of the store
   * @return a future that completes when the store is deleted, also if there is none with the ID
   */
  public Future<Void> delete(String id) {
    return update().where(eq("id", id)).set(Assignments.none().set("deleted_at", now())).execute().mapEmpty();
  }

  /** Gives the time now, to the microsecond that every database keeps. */
  private Timestamp now() {
    return Timestamps.fromMicros(ChronoUnit.MICROS.between(Instant.EPOCH, clock.instant()));
  }

  private Store store(Tables.Store row) {
    return Store.newBuilder().setId(row.getId()).setName(row.getName()).setCreatedAt(row.getCreatedAt())
        .setUpdatedAt(row.getUpdatedAt()).build();
  }
}
