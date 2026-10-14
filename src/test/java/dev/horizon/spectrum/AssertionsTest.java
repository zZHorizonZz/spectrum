package dev.horizon.spectrum;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.spectrum.openfga.v1.Assertion;
import dev.horizon.spectrum.openfga.v1.AssertionTupleKey;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import dev.horizon.spectrum.openfga.v1.ReadAssertionsRequest;
import dev.horizon.spectrum.openfga.v1.WriteAssertionsRequest;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SpectrumTest
class AssertionsTest {

  private Spectrum spectrum;
  private OpenFGAServiceGrpcClient fga;
  private TestStore store;

  @BeforeEach
  void open(Spectrum spectrum) {
    this.spectrum = spectrum;
    this.fga = spectrum.client();
    this.store = spectrum.store(Documents.MODEL);
  }

  private Assertion assertion(String user, String relation, String object, boolean expectation) {
    return Assertion.newBuilder().setTupleKey(AssertionTupleKey.newBuilder().setUser(user).setRelation(relation)
        .setObject(object)).setExpectation(expectation).build();
  }

  private WriteAssertionsRequest write(List<Assertion> assertions) {
    return WriteAssertionsRequest.newBuilder().setStoreId(store.id()).setAuthorizationModelId(store.model())
        .addAllAssertions(assertions).build();
  }

  private List<Assertion> read() {
    return fga.readAssertions(ReadAssertionsRequest.newBuilder().setStoreId(store.id())
        .setAuthorizationModelId(store.model()).build()).await().getAssertionsList();
  }

  @Test
  void readsTheAssertionsInPlaceOfThoseBefore() {
    assertThat(read()).isEmpty();
    fga.writeAssertions(write(List.of(assertion("user:anne", "viewer", "doc:1", true)))).await();
    List<Assertion> assertions = List.of(assertion("user:beth", "reader", "doc:1", false),
        assertion("user:anne", "viewer", "doc:2", true).toBuilder().addContextualTuples(spectrum.tuple(
            "doc:2#viewer@user:anne")).build());
    fga.writeAssertions(write(assertions)).await();
    assertThat(read()).containsExactlyElementsOf(assertions);
  }

  @Test
  void refusesAnAssertionThatTheModelDoesNotAllow() {
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.writeAssertions(write(List.of(assertion(
        "user:anne", "viewers", "doc:1", true)))));
  }
}
