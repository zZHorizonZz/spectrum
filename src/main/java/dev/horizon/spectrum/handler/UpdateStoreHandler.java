package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.NotFoundErrorCode;
import dev.horizon.spectrum.openfga.v1.UpdateStoreRequest;
import dev.horizon.spectrum.openfga.v1.UpdateStoreResponse;
import dev.horizon.spectrum.storage.StoresBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class UpdateStoreHandler extends UnaryHandler<UpdateStoreRequest, UpdateStoreResponse> {

  public static final ServiceMethod<UpdateStoreRequest, UpdateStoreResponse> SERVICE_METHOD = method(
      "UpdateStore", UpdateStoreRequest.newBuilder());

  private final StoresBackend stores;

  public UpdateStoreHandler(StoresBackend stores) {
    this.stores = stores;
  }

  @Override
  Future<UpdateStoreResponse> execute(UpdateStoreRequest request) {
    return stores.update(request.getStoreId(), request.getName()).map(found -> found
        .map(store -> UpdateStoreResponse.newBuilder().setId(store.getId()).setName(store.getName())
            .setCreatedAt(store.getCreatedAt()).setUpdatedAt(store.getUpdatedAt()).build())
        .orElseThrow(() -> new OpenFgaException(NotFoundErrorCode.store_id_not_found, "Store ID not found")));
  }
}
