package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.openfga.v1.DeleteStoreRequest;
import dev.horizon.spectrum.openfga.v1.DeleteStoreResponse;
import dev.horizon.spectrum.storage.StoresBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class DeleteStoreHandler extends UnaryHandler<DeleteStoreRequest, DeleteStoreResponse> {

  public static final ServiceMethod<DeleteStoreRequest, DeleteStoreResponse> SERVICE_METHOD = method(
      "DeleteStore", DeleteStoreRequest.newBuilder());

  private final StoresBackend stores;

  public DeleteStoreHandler(StoresBackend stores) {
    this.stores = stores;
  }

  @Override
  Future<DeleteStoreResponse> execute(DeleteStoreRequest request) {
    return stores.get(request.getStoreId()).compose(found -> found.isEmpty() ? Future.<Void> succeededFuture()
        : stores.delete(request.getStoreId())).map(DeleteStoreResponse.getDefaultInstance());
  }
}
