package dev.horizon.spectrum;

import static dev.horizon.spectrum.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.spectrum.openfga.v1.BatchCheckItem;
import dev.horizon.spectrum.openfga.v1.BatchCheckRequest;
import dev.horizon.spectrum.openfga.v1.BatchCheckResponse;
import dev.horizon.spectrum.openfga.v1.CheckRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.grpc.common.GrpcStatus;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SpectrumTest
class BatchCheckTest {

  private Spectrum spectrum;
  private OpenFGAServiceGrpcClient fga;
  private TestStore store;

  @BeforeEach
  void open(Spectrum spectrum) throws TimeoutException {
    this.spectrum = spectrum;
    this.fga = spectrum.client();
    this.store = spectrum.documents();
  }

  private BatchCheckItem item(String id, String user, String relation, String object) {
    return BatchCheckItem.newBuilder().setCorrelationId(id).setTupleKey(CheckRequestTupleKey.newBuilder()
        .setUser(user).setRelation(relation).setObject(object)).build();
  }

  @Test
  void checksABatch() throws TimeoutException {
    BatchCheckResponse response = fga.batchCheck(BatchCheckRequest.newBuilder().setStoreId(store.id())
        .addChecks(item("anne", "user:anne", "viewer", "doc:plan"))
        .addChecks(item("beth", "user:beth", "reader", "doc:plan"))
        .addChecks(item("unknown", "user:anne", "viewer", "document:1")).build()).await(TIMEOUT);
    assertThat(response.getResultMap().get("anne").getAllowed()).isTrue();
    assertThat(response.getResultMap().get("beth").getAllowed()).isFalse();
    assertThat(response.getResultMap().get("unknown").getError().getInputError()).isEqualTo(
        ErrorCode.validation_error);
  }

  @Test
  void refusesABatchWithADuplicateCorrelationId() {
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.batchCheck(BatchCheckRequest.newBuilder()
        .setStoreId(store.id()).addChecks(item("same", "user:anne", "viewer", "doc:plan"))
        .addChecks(item("same", "user:beth", "viewer", "doc:plan")).build()));
  }

  @Test
  void refusesMoreChecksThanTheLimit() throws TimeoutException {
    fga = spectrum.serve(new OpenFgaServiceOptions().withMaxChecksPerBatchCheck(1));
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.batchCheck(BatchCheckRequest.newBuilder()
        .setStoreId(store.id()).addChecks(item("anne", "user:anne", "viewer", "doc:plan"))
        .addChecks(item("beth", "user:beth", "viewer", "doc:plan")).build()));
  }
}
