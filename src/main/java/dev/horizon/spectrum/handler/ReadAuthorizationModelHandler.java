package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.ReadAuthorizationModelRequest;
import dev.horizon.spectrum.openfga.v1.ReadAuthorizationModelResponse;
import dev.horizon.spectrum.storage.AuthorizationModelBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class ReadAuthorizationModelHandler
    extends UnaryHandler<ReadAuthorizationModelRequest, ReadAuthorizationModelResponse> {

  public static final ServiceMethod<ReadAuthorizationModelRequest, ReadAuthorizationModelResponse> SERVICE_METHOD = method(
      "ReadAuthorizationModel", ReadAuthorizationModelRequest.newBuilder());

  private final AuthorizationModelBackend models;

  public ReadAuthorizationModelHandler(AuthorizationModelBackend models) {
    this.models = models;
  }

  @Override
  Future<ReadAuthorizationModelResponse> execute(ReadAuthorizationModelRequest request) {
    return models.read(request.getStoreId(), request.getId()).map(found -> found
        .map(model -> ReadAuthorizationModelResponse.newBuilder().setAuthorizationModel(model).build())
        .orElseThrow(() -> new OpenFgaException(ErrorCode.authorization_model_not_found, "Authorization Model '"
            + request.getId() + "' not found")));
  }
}
