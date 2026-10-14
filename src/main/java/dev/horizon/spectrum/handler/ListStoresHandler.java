package dev.horizon.spectrum.handler;

import dev.horizon.aquery.aip158.PageTokenCodec;
import dev.horizon.spectrum.openfga.v1.ListStoresRequest;
import dev.horizon.spectrum.openfga.v1.ListStoresResponse;
import dev.horizon.spectrum.storage.PageQuery;
import dev.horizon.spectrum.storage.StoresBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;
import java.util.List;

public final class ListStoresHandler extends UnaryHandler<ListStoresRequest, ListStoresResponse> {

  public static final ServiceMethod<ListStoresRequest, ListStoresResponse> SERVICE_METHOD = method("ListStores",
      ListStoresRequest.newBuilder());

  private final StoresBackend stores;
  private final PageTokenCodec tokens;

  public ListStoresHandler(StoresBackend stores, PageTokenCodec tokens) {
    this.stores = stores;
    this.tokens = tokens;
  }

  @Override
  Future<ListStoresResponse> execute(ListStoresRequest request) {
    PageQuery query = new PageQuery(tokens, request.getPageSize().getValue(), request.getContinuationToken(),
        "ListStores", request.getName());
    return stores.list(List.of(), request.getName(), query).map(page -> ListStoresResponse.newBuilder()
        .addAllStores(page.items()).setContinuationToken(page.nextPageToken()).build());
  }
}
