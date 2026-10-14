package dev.horizon.spectrum;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Int32Value;
import com.google.protobuf.Timestamp;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import dev.horizon.spectrum.openfga.v1.ReadChangesRequest;
import dev.horizon.spectrum.openfga.v1.ReadChangesResponse;
import dev.horizon.spectrum.openfga.v1.TupleChange;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import dev.horizon.spectrum.openfga.v1.TupleOperation;
import dev.horizon.spectrum.openfga.v1.WriteRequest;
import dev.horizon.spectrum.openfga.v1.WriteRequestDeletes;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SpectrumTest
class ChangesTest {

  private static final long MOMENT = 10;

  private Spectrum spectrum;
  private OpenFGAServiceGrpcClient fga;
  private TestStore store;

  @BeforeEach
  void open(Spectrum spectrum) {
    this.spectrum = spectrum;
    this.fga = spectrum.client();
    this.store = spectrum.store(Documents.MODEL);
    store.write("doc:1#viewer@user:anne", "folder:1#viewer@user:anne");
    fga.write(WriteRequest.newBuilder().setStoreId(store.id()).setDeletes(WriteRequestDeletes.newBuilder()
        .addTupleKeys(TupleKeyWithoutCondition.newBuilder().setObject("doc:1").setRelation("viewer")
            .setUser("user:anne")))
        .build()).await();
  }

  private ReadChangesResponse changes(String type, String token) {
    return fga.readChanges(ReadChangesRequest.newBuilder().setStoreId(store.id()).setType(type)
        .setPageSize(Int32Value.of(2)).setContinuationToken(token).build()).await();
  }

  private String text(TupleChange change) {
    return change.getOperation().name() + " " + spectrum.text(change.getTupleKey());
  }

  @Test
  void readsTheChangesInTheOrderTheyCommitted() {
    List<String> read = new ArrayList<>();
    ReadChangesResponse page = changes("", "");
    page.getChangesList().forEach(change -> read.add(text(change)));
    ReadChangesResponse next = changes("", page.getContinuationToken());
    next.getChangesList().forEach(change -> read.add(text(change)));
    assertThat(read).containsExactly("TUPLE_OPERATION_WRITE doc:1#viewer@user:anne",
        "TUPLE_OPERATION_WRITE folder:1#viewer@user:anne", "TUPLE_OPERATION_DELETE doc:1#viewer@user:anne");
    assertThat(changes("", next.getContinuationToken()).getContinuationToken())
        .isEqualTo(next.getContinuationToken());
  }

  @Test
  void readsTheChangesOfAType() {
    assertThat(changes("folder", "").getChangesList()).extracting(this::text)
        .containsExactly("TUPLE_OPERATION_WRITE folder:1#viewer@user:anne");
  }

  @Test
  void readsTheChangesAfterAStartTime() {
    spectrum.vertx().timer(MOMENT).await();
    Instant start = Instant.now();
    spectrum.vertx().timer(MOMENT).await();
    store.write("doc:2#viewer@user:beth");
    assertThat(fga.readChanges(ReadChangesRequest.newBuilder().setStoreId(store.id()).setStartTime(Timestamp
        .newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano())).build()).await()
        .getChangesList()).extracting(this::text).containsExactly("TUPLE_OPERATION_WRITE doc:2#viewer@user:beth");
  }

  @Test
  void refusesAStartTimeInTheFuture() {
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.readChanges(ReadChangesRequest.newBuilder()
        .setStoreId(store.id()).setStartTime(Timestamp.newBuilder().setSeconds(Instant.now().plusSeconds(60)
            .getEpochSecond()))
        .build()));
  }

  @Test
  void timesEachChange() {
    assertThat(changes("", "").getChangesList()).allSatisfy(change -> assertThat(change.getTimestamp().getSeconds())
        .isPositive());
    assertThat(changes("", "").getChanges(0).getOperation()).isEqualTo(TupleOperation.TUPLE_OPERATION_WRITE);
  }

  @Test
  void refusesATokenOfAnotherType() {
    String token = changes("doc", "").getContinuationToken();
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.readChanges(ReadChangesRequest.newBuilder()
        .setStoreId(store.id()).setType("folder").setContinuationToken(token).build()));
  }
}
