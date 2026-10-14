package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.listobjects.ListObjectsQuery;
import dev.horizon.spectrum.model.TypeSystemResolver;
import dev.horizon.spectrum.openfga.v1.ListObjectsRequest;
import dev.horizon.spectrum.openfga.v1.ListObjectsResponse;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class ListObjectsHandler extends UnaryHandler<ListObjectsRequest, ListObjectsResponse> {

  public static final ServiceMethod<ListObjectsRequest, ListObjectsResponse> SERVICE_METHOD = method(
      "ListObjects", ListObjectsRequest.newBuilder());

  private final TypeSystemResolver resolver;
  private final ListObjectsQuery query;

  public ListObjectsHandler(TypeSystemResolver resolver, ListObjectsQuery query) {
    this.resolver = resolver;
    this.query = query;
  }

  @Override
  Future<ListObjectsResponse> execute(ListObjectsRequest request) {
    return resolver.resolve(request.getStoreId(), request.getAuthorizationModelId())
        .compose(types -> query.list(new ListObjectsQuery.Request(request.getStoreId(), request.getType(),
            request.getRelation(), request.getUser(), request.getContextualTuples().getTupleKeysList(),
            request.getContext()), types))
        .map(objects -> ListObjectsResponse.newBuilder().addAllObjects(objects).build());
  }
}
