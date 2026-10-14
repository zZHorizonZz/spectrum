package dev.horizon.spectrum.storage;

import dev.horizon.spectrum.openfga.v1.Assertion;
import io.vertx.core.Future;
import java.util.List;

public interface AssertionsBackend {

  /** Writes the assertions of a model, in place of those it had. */
  Future<Void> write(String store, String modelId, List<Assertion> assertions);

  Future<List<Assertion>> read(String store, String modelId);
}
