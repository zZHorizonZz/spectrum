package dev.horizon.spectrum;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Int32Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import dev.horizon.spectrum.openfga.v1.AuthorizationModel;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import dev.horizon.spectrum.openfga.v1.ReadAuthorizationModelRequest;
import dev.horizon.spectrum.openfga.v1.ReadAuthorizationModelsRequest;
import dev.horizon.spectrum.openfga.v1.ReadAuthorizationModelsResponse;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import io.vertx.grpc.common.GrpcStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SpectrumTest
class AuthorizationModelsTest {

  private Spectrum spectrum;
  private OpenFGAServiceGrpcClient fga;
  private TestStore store;

  @BeforeEach
  void open(Spectrum spectrum) {
    this.spectrum = spectrum;
    this.fga = spectrum.client();
    this.store = spectrum.store();
  }

  private WriteAuthorizationModelRequest json(String json) throws InvalidProtocolBufferException {
    WriteAuthorizationModelRequest.Builder request = WriteAuthorizationModelRequest.newBuilder();
    JsonFormat.parser().merge(json, request);
    return request.setStoreId(store.id()).build();
  }

  @Test
  void readsAModelAsItWasWritten() {
    WriteAuthorizationModelRequest written = spectrum.model(store.id(), Documents.MODEL);
    String id = fga.writeAuthorizationModel(written).await().getAuthorizationModelId();
    AuthorizationModel read = fga.readAuthorizationModel(ReadAuthorizationModelRequest.newBuilder()
        .setStoreId(store.id()).setId(id).build()).await().getAuthorizationModel();
    assertThat(read.getId()).isEqualTo(id);
    assertThat(read.getSchemaVersion()).isEqualTo("1.1");
    assertThat(read.getTypeDefinitionsList()).isEqualTo(written.getTypeDefinitionsList());
  }

  @Test
  void readsTheModelsPageByPageTheLatestFirst() {
    List<String> ids = IntStream.range(0, 3).mapToObj(i -> store.writeModel(Documents.MODEL)).toList();
    List<String> read = new ArrayList<>();
    String token = "";
    do {
      ReadAuthorizationModelsResponse page = fga.readAuthorizationModels(ReadAuthorizationModelsRequest.newBuilder()
          .setStoreId(store.id()).setPageSize(Int32Value.of(2)).setContinuationToken(token).build()).await();
      page.getAuthorizationModelsList().forEach(model -> read.add(model.getId()));
      token = page.getContinuationToken();
    } while (!token.isEmpty());
    assertThat(read).containsExactlyElementsOf(ids.reversed());
  }

  @Test
  void refusesAModelIdThatTheStoreDoesNotHave() {
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.readAuthorizationModel(ReadAuthorizationModelRequest
        .newBuilder().setStoreId(store.id()).setId("01ARZ3NDEKTSV4RRFFQ69G5FAV").build()));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      """
          { "schema_version": "1.0", "type_definitions": [{ "type": "user" }] }
          """,
      """
          { "schema_version": "1.1", "type_definitions": [{ "type": "user" }, { "type": "user" }] }
          """,
      """
          { "schema_version": "1.1", "type_definitions": [
            { "type": "doc", "relations": { "viewer": { "computedUserset": { "relation": "owner" } } } }
          ] }
          """,
      """
          { "schema_version": "1.1", "type_definitions": [{ "type": "user" },
            { "type": "doc", "relations": { "viewer": { "this": {} } } }
          ] }
          """,
      """
          { "schema_version": "1.1", "type_definitions": [{ "type": "user" }, { "type": "doc",
            "relations": { "viewer": { "this": {} } },
            "metadata": { "relations": { "viewer": { "directly_related_user_types": [{ "type": "user", "condition": "x" }] } } }
          }] }
          """,
      """
          { "schema_version": "1.1", "type_definitions": [{ "type": "user" }],
            "conditions": { "x": { "name": "x", "expression": "1 + 1" } } }
          """ })
  void refusesAModelThatOpenFgaDoesNotAllow(String model) throws InvalidProtocolBufferException {
    WriteAuthorizationModelRequest request = json(model);
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.writeAuthorizationModel(request));
  }

  @Test
  void refusesAModelWithMoreTypesThanTheLimit() {
    fga = spectrum.serve(new OpenFgaServiceOptions().withMaxTypesPerAuthorizationModel(1));
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.writeAuthorizationModel(spectrum.model(store.id(),
        Documents.MODEL)));
  }
}
