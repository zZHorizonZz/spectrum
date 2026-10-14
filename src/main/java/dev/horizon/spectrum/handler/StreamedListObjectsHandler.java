package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.listobjects.ListObjectsQuery;
import dev.horizon.spectrum.model.TypeSystemResolver;
import dev.horizon.spectrum.openfga.v1.StreamedListObjectsRequest;
import dev.horizon.spectrum.openfga.v1.StreamedListObjectsResponse;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServerResponse;

public final class StreamedListObjectsHandler
    extends OpenFgaHandler<StreamedListObjectsRequest, StreamedListObjectsResponse> {

  public static final ServiceMethod<StreamedListObjectsRequest, StreamedListObjectsResponse> SERVICE_METHOD = method(
      "StreamedListObjects", StreamedListObjectsRequest.newBuilder());

  private final TypeSystemResolver resolver;
  private final ListObjectsQuery query;

  public StreamedListObjectsHandler(TypeSystemResolver resolver, ListObjectsQuery query) {
    this.resolver = resolver;
    this.query = query;
  }

  @Override
  public void handle(GrpcServerRequest<StreamedListObjectsRequest, StreamedListObjectsResponse> request) {
    request.handler(message -> stream(message, request.response()).onComplete(done -> {
      if (done.succeeded()) {
        request.response().end();
      } else {
        request.response().fail(status(done.cause()));
      }
    }));
  }

  private Future<Void> stream(StreamedListObjectsRequest request,
      GrpcServerResponse<StreamedListObjectsRequest, StreamedListObjectsResponse> response) {
    try {
      validate(request);
      return resolver.resolve(request.getStoreId(), request.getAuthorizationModelId())
          .compose(types -> query.stream(new ListObjectsQuery.Request(request.getStoreId(),
              request.getType(), request.getRelation(), request.getUser(),
              request.getContextualTuples().getTupleKeysList(), request.getContext()), types,
              object -> response.write(StreamedListObjectsResponse.newBuilder().setObject(object).build())));
    } catch (RuntimeException failed) {
      return Future.failedFuture(failed);
    }
  }
}
