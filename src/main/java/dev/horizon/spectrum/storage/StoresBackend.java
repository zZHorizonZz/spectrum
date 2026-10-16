package dev.horizon.spectrum.storage;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.spectrum.openfga.v1.Store;
import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;

/**
 * Creates, reads, renames and deletes stores. A deleted store keeps its rows, and reads leave it out.
 */
public interface StoresBackend {

  /**
   * Creates a store.
   *
   * @param id the ID of the store, a ULID
   * @param name the name of the store
   * @return the store, with the time of its creation as its creation and update times
   */
  Future<Store> create(String id, String name);

  /**
   * Reads a store.
   *
   * @param id the ID of the store
   * @return the store, or empty if there is none with the ID or it is deleted
   */
  Future<Optional<Store>> get(String id);

  /**
   * Lists the stores that are not deleted, in order of ID.
   *
   * @param ids the IDs of the stores to list, or none for every store
   * @param name the name of the stores to list, or empty for every name
   * @param page the page, whose cursor is the ID of its first store
   * @return the stores of the page, with the token of the next page
   */
  Future<Page<Store>> list(List<String> ids, String name, PageQuery page);

  /**
   * Renames a store, and sets its update time to now.
   *
   * @param id the ID of the store
   * @param name the new name
   * @return the renamed store, or empty if there is none with the ID or it is deleted
   */
  Future<Optional<Store>> update(String id, String name);

  /**
   * Deletes a store. Its models, tuples, changes and assertions stay in their tables.
   *
   * @param id the ID of the store
   * @return a future that completes when the store is deleted, also if there is none with the ID
   */
  Future<Void> delete(String id);
}
