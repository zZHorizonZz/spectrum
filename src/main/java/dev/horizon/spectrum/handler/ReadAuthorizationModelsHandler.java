package dev.horizon.spectrum.handler;

import dev.horizon.aquery.aip158.PageTokenCodec;
import dev.horizon.spectrum.openfga.v1.ReadAuthorizationModelsRequest;
import dev.horizon.spectrum.openfga.v1.ReadAuthorizationModelsResponse;
import dev.horizon.spectrum.storage.AuthorizationModelBackend;
import dev.horizon.spectrum.storage.PageQuery;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class ReadAuthorizationModelsHandler
    extends UnaryHandler<ReadAuthorizationModelsRequest, ReadAuthorizationModelsResponse> {

  public static final ServiceMethod<ReadAuthorizationModelsRequest, ReadAuthorizationModelsResponse> SERVICE_METHOD = method(
      "ReadAuthorizationModels", ReadAuthorizationModelsRequest.newBuilder());

  private final AuthorizationModelBackend models;
  private final PageTokenCodec tokens;

  public ReadAuthorizationModelsHandler(AuthorizationModelBackend models, PageTokenCodec tokens) {
    this.models = models;
    this.tokens = tokens;
  }

  @Override
  Future<ReadAuthorizationModelsResponse> execute(ReadAuthorizationModelsRequest request) {
    PageQuery query = new PageQuery(tokens, request.getPageSize().getValue(), request.getContinuationToken(),
        "ReadAuthorizationModels", request.getStoreId());
    return models.readPage(request.getStoreId(), query).map(page -> ReadAuthorizationModelsResponse.newBuilder()
        .addAllAuthorizationModels(page.items()).setContinuationToken(page.nextPageToken()).build());
  }
}
