package dev.horizon.spectrum;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.spectrum.openfga.v1.ListObjectsRequest;
import dev.horizon.spectrum.openfga.v1.ListUsersRequest;
import dev.horizon.spectrum.openfga.v1.Object;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SpectrumTest
class LimitsTest {

  private Spectrum spectrum;
  private TestStore store;

  @BeforeEach
  void open(Spectrum spectrum) {
    this.spectrum = spectrum;
    this.store = spectrum.documents();
  }

  @Test
  void listsObjectsUpToTheMostResults() {
    assertThat(spectrum.serve(new OpenFgaServiceOptions().withListObjects(2, Duration.ofSeconds(3)))
        .listObjects(ListObjectsRequest.newBuilder().setStoreId(store.id()).setUser("user:anne").setRelation("viewer")
            .setType("doc").build())
        .await().getObjectsList()).hasSize(2).isSubsetOf("doc:memo", "doc:plan", "doc:wiki");
  }

  @Test
  void listsUsersUpToTheMostResults() {
    assertThat(spectrum.serve(new OpenFgaServiceOptions().withListUsers(2, Duration.ofSeconds(3)))
        .listUsers(ListUsersRequest.newBuilder().setStoreId(store.id()).setObject(Object.newBuilder().setType("doc")
            .setId("plan")).setRelation("viewer").addUserFilters(UserTypeFilter.newBuilder().setType("user")).build())
        .await().getUsersList()).hasSize(2);
  }
}
