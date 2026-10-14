package dev.horizon.spectrum;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Int32Value;
import dev.horizon.spectrum.openfga.v1.CreateStoreRequest;
import dev.horizon.spectrum.openfga.v1.CreateStoreResponse;
import dev.horizon.spectrum.openfga.v1.DeleteStoreRequest;
import dev.horizon.spectrum.openfga.v1.GetStoreRequest;
import dev.horizon.spectrum.openfga.v1.GetStoreResponse;
import dev.horizon.spectrum.openfga.v1.ListStoresRequest;
import dev.horizon.spectrum.openfga.v1.ListStoresResponse;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import dev.horizon.spectrum.openfga.v1.Store;
import dev.horizon.spectrum.openfga.v1.UpdateStoreRequest;
import io.vertx.grpc.common.GrpcStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SpectrumTest
class StoresTest {

  private Spectrum spectrum;
  private OpenFGAServiceGrpcClient fga;

  @BeforeEach
  void open(Spectrum spectrum) {
    this.spectrum = spectrum;
    this.fga = spectrum.client();
  }

  private CreateStoreResponse create(String name) {
    return fga.createStore(CreateStoreRequest.newBuilder().setName(name).build()).await();
  }

  private GetStoreResponse get(String id) {
    return fga.getStore(GetStoreRequest.newBuilder().setStoreId(id).build()).await();
  }

  @Test
  void createsAndGetsAStore() {
    CreateStoreResponse created = create("documents");
    GetStoreResponse store = get(created.getId());
    assertThat(created.getId()).matches("[0-9A-HJKMNP-TV-Z]{26}");
    assertThat(created.getUpdatedAt()).isEqualTo(created.getCreatedAt());
    assertThat(store.getName()).isEqualTo("documents");
    assertThat(store.getCreatedAt()).isEqualTo(created.getCreatedAt());
  }

  @Test
  void listsTheStoresPageByPage() {
    List<String> ids = List.of(create("first").getId(), create("second").getId(), create("third").getId());
    List<String> listed = new ArrayList<>();
    String token = "";
    do {
      ListStoresResponse page = fga.listStores(ListStoresRequest.newBuilder().setPageSize(Int32Value.of(2))
          .setContinuationToken(token).build()).await();
      page.getStoresList().forEach(store -> listed.add(store.getId()));
      token = page.getContinuationToken();
    } while (!token.isEmpty());
    assertThat(listed).containsExactlyElementsOf(ids);
  }

  @Test
  void listsTheStoresOfAName() {
    create("first");
    String second = create("second").getId();
    assertThat(fga.listStores(ListStoresRequest.newBuilder().setName("second").build()).await().getStoresList())
        .extracting(Store::getId).containsExactly(second);
  }

  @Test
  void renamesAStore() {
    String id = create("documents").getId();
    fga.updateStore(UpdateStoreRequest.newBuilder().setStoreId(id).setName("records").build()).await();
    assertThat(get(id).getName()).isEqualTo("records");
  }

  @Test
  void deletesAStore() {
    String id = create("documents").getId();
    fga.deleteStore(DeleteStoreRequest.newBuilder().setStoreId(id).build()).await();
    fga.deleteStore(DeleteStoreRequest.newBuilder().setStoreId(id).build()).await();
    spectrum.assertStatus(GrpcStatus.NOT_FOUND, () -> fga.getStore(GetStoreRequest.newBuilder().setStoreId(id)
        .build()));
    assertThat(fga.listStores(ListStoresRequest.getDefaultInstance()).await().getStoresList()).isEmpty();
    spectrum.assertStatus(GrpcStatus.NOT_FOUND, () -> fga.updateStore(UpdateStoreRequest.newBuilder().setStoreId(id)
        .setName("records").build()));
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "ab", "a name that is longer than the sixty four characters that a store name may have" })
  void refusesANameThatOpenFgaDoesNotAllow(String name) {
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.createStore(CreateStoreRequest.newBuilder()
        .setName(name).build()));
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "unknown", "01ARZ3NDEKTSV4RRFFQ69G5FA" })
  void refusesAStoreIdThatIsNotAUlid(String id) {
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.getStore(GetStoreRequest.newBuilder().setStoreId(id)
        .build()));
  }
}
