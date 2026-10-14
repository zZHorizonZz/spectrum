package dev.horizon.spectrum.storage;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.spectrum.openfga.v1.AuthorizationModel;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * Reads and writes the authorization models of a store. A model never changes once written.
 */
public interface AuthorizationModelBackend {

  Future<Void> write(String store, AuthorizationModel model);

  Future<Optional<AuthorizationModel>> read(String store, String id);

  /**
   * Reads a page of the models of a store, the latest first.
   *
   * @param page the page, whose cursor is the ID of its first model
   */
  Future<Page<AuthorizationModel>> readPage(String store, PageQuery page);

  Future<Optional<AuthorizationModel>> findLatest(String store);
}
