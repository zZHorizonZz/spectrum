package dev.horizon.spectrum.handler;

import com.google.protobuf.Timestamp;
import dev.horizon.aquery.aip158.PageTokenCodec;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.ReadChangesRequest;
import dev.horizon.spectrum.openfga.v1.ReadChangesResponse;
import dev.horizon.spectrum.storage.ChangelogBackend;
import dev.horizon.spectrum.storage.PageQuery;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;
import java.util.Optional;

public final class ReadChangesHandler extends UnaryHandler<ReadChangesRequest, ReadChangesResponse> {

  public static final ServiceMethod<ReadChangesRequest, ReadChangesResponse> SERVICE_METHOD = method(
      "ReadChanges", ReadChangesRequest.newBuilder());

  private final ChangelogBackend changelog;
  private final PageTokenCodec tokens;

  public ReadChangesHandler(ChangelogBackend changelog, PageTokenCodec tokens) {
    this.changelog = changelog;
    this.tokens = tokens;
  }

  @Override
  Future<ReadChangesResponse> execute(ReadChangesRequest request) {
    PageQuery query = new PageQuery(tokens, request.getPageSize().getValue(), request.getContinuationToken(),
        "ReadChanges", request.getStoreId());
    if (!query.from().isEmpty() && !query.cursor().getLast().equals(request.getType())) {
      throw new OpenFgaException(ErrorCode.query_string_type_continuation_token_mismatch, "The type in the"
          + " querystring and the continuation token don't match");
    }
    Optional<Timestamp> startTime = request.hasStartTime() ? Optional.of(request.getStartTime()) : Optional.empty();
    return changelog.read(request.getStoreId(), request.getType(), startTime, query).map(page -> ReadChangesResponse
        .newBuilder().addAllChanges(page.items()).setContinuationToken(page.items().isEmpty() ? request
            .getContinuationToken() : page.nextPageToken())
        .build());
  }
}
