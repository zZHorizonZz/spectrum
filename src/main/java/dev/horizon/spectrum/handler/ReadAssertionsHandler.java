package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.model.TypeSystemResolver;
import dev.horizon.spectrum.openfga.v1.ReadAssertionsRequest;
import dev.horizon.spectrum.openfga.v1.ReadAssertionsResponse;
import dev.horizon.spectrum.storage.AssertionsBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class ReadAssertionsHandler extends UnaryHandler<ReadAssertionsRequest, ReadAssertionsResponse> {

  public static final ServiceMethod<ReadAssertionsRequest, ReadAssertionsResponse> SERVICE_METHOD = method(
      "ReadAssertions", ReadAssertionsRequest.newBuilder());

  private final TypeSystemResolver resolver;
  private final AssertionsBackend assertions;

  public ReadAssertionsHandler(TypeSystemResolver resolver, AssertionsBackend assertions) {
    this.resolver = resolver;
    this.assertions = assertions;
  }

  @Override
  Future<ReadAssertionsResponse> execute(ReadAssertionsRequest request) {
    return resolver.resolve(request.getStoreId(), request.getAuthorizationModelId())
        .compose(types -> execute(request, types));
  }

  private Future<ReadAssertionsResponse> execute(ReadAssertionsRequest request, TypeSystem types) {
    return assertions.read(request.getStoreId(), types.modelId()).map(assertions -> ReadAssertionsResponse
        .newBuilder().setAuthorizationModelId(types.modelId()).addAllAssertions(assertions).build());
  }
}
