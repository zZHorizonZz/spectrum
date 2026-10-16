package dev.horizon.spectrum.storage;

import dev.horizon.spectrum.openfga.v1.Assertion;
import io.vertx.core.Future;
import java.util.List;

/**
 * Reads and writes the assertions of the authorization models of a store: the checks that a model is expected to
 * answer.
 */
public interface AssertionsBackend {

  /**
   * Writes the assertions of a model, in place of those it had.
   *
   * @param store the ID of the store
   * @param modelId the ID of the model
   * @param assertions the assertions, or none to clear them
   * @return a future that completes when the assertions are stored
   */
  Future<Void> write(String store, String modelId, List<Assertion> assertions);

  /**
   * Reads the assertions of a model.
   *
   * @param store the ID of the store
   * @param modelId the ID of the model
   * @return the assertions, in the order they were written, or none if the model has none
   */
  Future<List<Assertion>> read(String store, String modelId);
}
