package dev.horizon.spectrum.storage;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.spectrum.openfga.v1.AuthorizationModel;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * Reads and writes the authorization models of a store. A model never changes once written.
 */
public interface AuthorizationModelBackend {

  /**
   * Writes a new model.
   *
   * @param store the ID of the store
   * @param model the model, with its ID
   * @return a future that completes when the model is stored, or fails if the store has a model with the same ID
   */
  Future<Void> write(String store, AuthorizationModel model);

  /**
   * Reads a model.
   *
   * @param store the ID of the store
   * @param id the ID of the model
   * @return the model, or empty if the store has no model with the ID
   */
  Future<Optional<AuthorizationModel>> read(String store, String id);

  /**
   * Reads a page of the models of a store, the latest first.
   *
   * @param store the ID of the store
   * @param page the page, whose cursor is the ID of its first model
   * @return the models of the page, with the token of the next page
   */
  Future<Page<AuthorizationModel>> readPage(String store, PageQuery page);

  /**
   * Reads the latest model of a store, the one with the greatest ID.
   *
   * @param store the ID of the store
   * @return the model, or empty if the store has none
   */
  Future<Optional<AuthorizationModel>> findLatest(String store);
}
