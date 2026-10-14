package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.listusers.ListUsersQuery;
import dev.horizon.spectrum.model.TypeSystemResolver;
import dev.horizon.spectrum.openfga.v1.ListUsersRequest;
import dev.horizon.spectrum.openfga.v1.ListUsersResponse;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class ListUsersHandler extends UnaryHandler<ListUsersRequest, ListUsersResponse> {

  public static final ServiceMethod<ListUsersRequest, ListUsersResponse> SERVICE_METHOD = method("ListUsers",
      ListUsersRequest.newBuilder());

  private final TypeSystemResolver resolver;
  private final ListUsersQuery query;

  public ListUsersHandler(TypeSystemResolver resolver, ListUsersQuery query) {
    this.resolver = resolver;
    this.query = query;
  }

  @Override
  Future<ListUsersResponse> execute(ListUsersRequest request) {
    return resolver.resolve(request.getStoreId(), request.getAuthorizationModelId())
        .compose(types -> query.list(request, types))
        .map(users -> ListUsersResponse.newBuilder().addAllUsers(users).build());
  }
}
