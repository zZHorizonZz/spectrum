package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.OpenFgaServiceOptions;
import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.model.TypeSystemResolver;
import dev.horizon.spectrum.openfga.v1.BatchCheckItem;
import dev.horizon.spectrum.openfga.v1.BatchCheckRequest;
import dev.horizon.spectrum.openfga.v1.BatchCheckResponse;
import dev.horizon.spectrum.openfga.v1.BatchCheckSingleResult;
import dev.horizon.spectrum.openfga.v1.CheckError;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.InternalErrorCode;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BatchCheckHandler extends UnaryHandler<BatchCheckRequest, BatchCheckResponse> {

  public static final ServiceMethod<BatchCheckRequest, BatchCheckResponse> SERVICE_METHOD = method(
      "BatchCheck", BatchCheckRequest.newBuilder());

  private static final System.Logger LOGGER = System.getLogger(BatchCheckHandler.class.getName());

  private final TypeSystemResolver resolver;
  private final CheckHandler check;
  private final OpenFgaServiceOptions options;

  public BatchCheckHandler(TypeSystemResolver resolver, CheckHandler check, OpenFgaServiceOptions options) {
    this.resolver = resolver;
    this.check = check;
    this.options = options;
  }

  @Override
  Future<BatchCheckResponse> execute(BatchCheckRequest request) {
    return resolver.resolve(request.getStoreId(), request.getAuthorizationModelId())
        .compose(types -> execute(request, types));
  }

  private Future<BatchCheckResponse> execute(BatchCheckRequest request, TypeSystem types) {
    List<BatchCheckItem> items = request.getChecksList();
    if (items.size() > options.maxChecksPerBatchCheck()) {
      throw new OpenFgaException(ErrorCode.validation_error, "batchCheck received " + items.size() + " checks, the"
          + " maximum allowed is " + options.maxChecksPerBatchCheck());
    }
    if (items.isEmpty()) {
      throw new OpenFgaException(ErrorCode.validation_error, "batch check requires at least one check to evaluate,"
          + " no checks were received");
    }
    Set<String> ids = new HashSet<>();
    for (BatchCheckItem item : items) {
      if (item.getCorrelationId().isEmpty()) {
        throw new OpenFgaException(ErrorCode.validation_error, "received empty correlation id for tuple: "
            + item.getTupleKey());
      }
      if (!ids.add(item.getCorrelationId())) {
        throw new OpenFgaException(ErrorCode.validation_error, "received duplicate correlation id: "
            + item.getCorrelationId());
      }
    }
    List<Future<BatchCheckSingleResult>> results = new ArrayList<>();
    for (BatchCheckItem item : items) {
      results.add(start(request.getStoreId(), item, types).transform(done -> Future.succeededFuture(done.succeeded()
          ? BatchCheckSingleResult.newBuilder().setAllowed(done.result()).build()
          : BatchCheckSingleResult.newBuilder().setError(error(done.cause())).build())));
    }
    return Future.all(results).map(done -> {
      BatchCheckResponse.Builder response = BatchCheckResponse.newBuilder();
      for (int i = 0; i < items.size(); i++) {
        response.putResult(items.get(i).getCorrelationId(), results.get(i).result());
      }
      return response.build();
    });
  }

  private Future<Boolean> start(String store, BatchCheckItem item, TypeSystem types) {
    try {
      return check.check(store, item.getTupleKey(), item.getContextualTuples().getTupleKeysList(),
          item.getContext(), types);
    } catch (RuntimeException failed) {
      return Future.failedFuture(failed);
    }
  }

  private CheckError error(Throwable failure) {
    if (failure instanceof OpenFgaException openFga && openFga.inputError().isPresent()) {
      return CheckError.newBuilder().setInputError(openFga.inputError().get()).setMessage(failure.getMessage())
          .build();
    }
    LOGGER.log(System.Logger.Level.ERROR, "a check of a batch failed", failure);
    return CheckError.newBuilder().setInternalError(InternalErrorCode.internal_error)
        .setMessage(INTERNAL_ERROR).build();
  }
}
