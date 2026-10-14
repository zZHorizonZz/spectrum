package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.common.ULID;
import dev.horizon.spectrum.openfga.v1.CreateStoreRequest;
import dev.horizon.spectrum.openfga.v1.CreateStoreResponse;
import dev.horizon.spectrum.storage.StoresBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class CreateStoreHandler extends UnaryHandler<CreateStoreRequest, CreateStoreResponse> {

  public static final ServiceMethod<CreateStoreRequest, CreateStoreResponse> SERVICE_METHOD = method(
      "CreateStore", CreateStoreRequest.newBuilder());

  private final StoresBackend stores;

  public CreateStoreHandler(StoresBackend stores) {
    this.stores = stores;
  }

  @Override
  Future<CreateStoreResponse> execute(CreateStoreRequest request) {
    return stores.create(ULID.randomULID().toString(), request.getName()).map(store -> CreateStoreResponse.newBuilder()
        .setId(store.getId()).setName(store.getName()).setCreatedAt(store.getCreatedAt())
        .setUpdatedAt(store.getUpdatedAt()).build());
  }
}
