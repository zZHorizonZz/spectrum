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

  Future<Store> create(String id, String name);

  Future<Optional<Store>> get(String id);

  /**
   * Lists the stores that are not deleted, in order of ID.
   *
   * @param ids the IDs of the stores to list, or none for every store
   * @param name the name of the stores to list, or empty for every name
   * @param page the page, whose cursor is the ID of its first store
   */
  Future<Page<Store>> list(List<String> ids, String name, PageQuery page);

  Future<Optional<Store>> update(String id, String name);

  Future<Void> delete(String id);
}
