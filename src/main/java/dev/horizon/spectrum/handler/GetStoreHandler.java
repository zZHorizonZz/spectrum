package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.GetStoreRequest;
import dev.horizon.spectrum.openfga.v1.GetStoreResponse;
import dev.horizon.spectrum.openfga.v1.NotFoundErrorCode;
import dev.horizon.spectrum.storage.StoresBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class GetStoreHandler extends UnaryHandler<GetStoreRequest, GetStoreResponse> {

  public static final ServiceMethod<GetStoreRequest, GetStoreResponse> SERVICE_METHOD = method("GetStore",
      GetStoreRequest.newBuilder());

  private final StoresBackend stores;

  public GetStoreHandler(StoresBackend stores) {
    this.stores = stores;
  }

  @Override
  Future<GetStoreResponse> execute(GetStoreRequest request) {
    return stores.get(request.getStoreId()).map(found -> found.map(store -> GetStoreResponse.newBuilder()
        .setId(store.getId()).setName(store.getName()).setCreatedAt(store.getCreatedAt())
        .setUpdatedAt(store.getUpdatedAt()).build())
        .orElseThrow(() -> new OpenFgaException(NotFoundErrorCode.store_id_not_found, "Store ID not found")));
  }
}
