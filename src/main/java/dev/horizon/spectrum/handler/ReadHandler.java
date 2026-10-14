package dev.horizon.spectrum.handler;

import dev.horizon.aquery.aip158.PageTokenCodec;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.ReadRequest;
import dev.horizon.spectrum.openfga.v1.ReadResponse;
import dev.horizon.spectrum.storage.PageQuery;
import dev.horizon.spectrum.storage.TupleBackend;
import dev.horizon.spectrum.storage.TupleReader;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class ReadHandler extends UnaryHandler<ReadRequest, ReadResponse> {

  public static final ServiceMethod<ReadRequest, ReadResponse> SERVICE_METHOD = method(
      "Read", ReadRequest.newBuilder());

  private final TupleBackend tuples;
  private final PageTokenCodec tokens;

  public ReadHandler(TupleBackend tuples, PageTokenCodec tokens) {
    this.tuples = tuples;
    this.tokens = tokens;
  }

  @Override
  Future<ReadResponse> execute(ReadRequest request) {
    TupleReader.ReadFilter filter = new TupleReader.ReadFilter(request.getTupleKey().getObject(),
        request.getTupleKey().getRelation(), request.getTupleKey().getUser());
    if (request.hasTupleKey() && (filter.objectType().isEmpty() || filter.objectId().isEmpty() && filter.user()
        .isEmpty())) {
      throw new OpenFgaException(ErrorCode.validation_error, "the 'tuple_key' field was provided but the object"
          + " type field is required and both the object id and user cannot be empty");
    }
    PageQuery query = new PageQuery(tokens, request.getPageSize().getValue(), request.getContinuationToken(), "Read",
        request.getStoreId(), filter.object(), filter.relation(), filter.user());
    return tuples.readPage(request.getStoreId(), filter, query).map(page -> ReadResponse.newBuilder()
        .addAllTuples(page.items()).setContinuationToken(page.nextPageToken()).build());
  }
}
