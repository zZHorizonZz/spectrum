package dev.horizon.spectrum.model;

import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.AuthorizationModel;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.storage.AuthorizationModelBackend;
import io.vertx.core.Future;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Gives the type system of an authorization model of a store, or of its latest model when a request names none. Models
 * never change, so the type system of each is kept once made.
 */
public final class TypeSystemResolver {

  private static final int MAX_CACHED_MODELS = 1000;
  private static final int INITIAL_CAPACITY = 16;
  private static final float LOAD_FACTOR = 0.75f;

  private final AuthorizationModelBackend models;
  private final Map<String, TypeSystem> cache = Collections
      .synchronizedMap(new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TypeSystem> eldest) {
          return size() > MAX_CACHED_MODELS;
        }
      });

  public TypeSystemResolver(AuthorizationModelBackend models) {
    this.models = models;
  }

  /**
   * Gives the type system of a model.
   *
   * @param modelId the model ID, or empty for the latest model of the store
   * @return the type system, or a future that fails with an {@link OpenFgaException} if the store has no such model
   */
  public Future<TypeSystem> resolve(String store, String modelId) {
    if (!modelId.isEmpty()) {
      TypeSystem cached = cache.get(store + "/" + modelId);
      if (cached != null) {
        return Future.succeededFuture(cached);
      }
    }
    Future<Optional<AuthorizationModel>> model = modelId.isEmpty() ? models.findLatest(store)
        : models.read(store, modelId);
    return model.map(found -> {
      AuthorizationModel resolved = found.orElseThrow(() -> modelId.isEmpty()
          ? new OpenFgaException(ErrorCode.latest_authorization_model_not_found, "No authorization models found for"
              + " store '" + store + "'")
          : new OpenFgaException(ErrorCode.authorization_model_not_found, "Authorization Model '" + modelId
              + "' not found"));
      return cache.computeIfAbsent(store + "/" + resolved.getId(), ignored -> new TypeSystem(resolved));
    });
  }
}
