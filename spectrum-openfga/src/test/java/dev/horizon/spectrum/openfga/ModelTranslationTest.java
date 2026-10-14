package dev.horizon.spectrum.openfga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.google.protobuf.util.JsonFormat;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import dev.horizon.spectrum.v1.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ModelTranslationTest {

  private final ModelTranslation translation = new ModelTranslation();

  private static WriteAuthorizationModelRequest openFga(String json) {
    WriteAuthorizationModelRequest.Builder request = WriteAuthorizationModelRequest.newBuilder();
    try {
      JsonFormat.parser().merge(json, request);
    } catch (InvalidProtocolBufferException invalid) {
      throw new IllegalArgumentException(invalid);
    }
    return request.build();
  }

  private static Model spectrum(String text) {
    try {
      return TextFormat.parse(text, Model.class);
    } catch (TextFormat.ParseException invalid) {
      throw new IllegalArgumentException(invalid);
    }
  }

  private Model translate(WriteAuthorizationModelRequest request) {
    return translation.toSpectrum(request.getSchemaVersion(), request.getTypeDefinitionsList(),
        request.getConditionsMap());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      direct types, wildcards and usersets | {"schema_version":"1.1","type_definitions":[{"type":"user"},{"type":"group","relations":{"member":{"this":{}}},"metadata":{"relations":{"member":{"directly_related_user_types":[{"type":"user"},{"type":"user","wildcard":{}},{"type":"group","relation":"member"}]}}}}]} | types { name: "user" } types { name: "group" relations { name: "member" rewrite { direct { subjects: ["user", "user:*", "group#member"] } } } }
      computed and from                    | {"schema_version":"1.1","type_definitions":[{"type":"user"},{"type":"folder","relations":{"owner":{"this":{}},"viewer":{"computedUserset":{"relation":"owner"}}},"metadata":{"relations":{"owner":{"directly_related_user_types":[{"type":"user"}]},"viewer":{"directly_related_user_types":[]}}}},{"type":"doc","relations":{"parent":{"this":{}},"viewer":{"tupleToUserset":{"tupleset":{"relation":"parent"},"computedUserset":{"relation":"viewer"}}}},"metadata":{"relations":{"parent":{"directly_related_user_types":[{"type":"folder"}]},"viewer":{"directly_related_user_types":[]}}}}]} | types { name: "user" } types { name: "folder" relations { name: "owner" rewrite { direct { subjects: "user" } } } relations { name: "viewer" rewrite { computed { relation: "owner" } } } } types { name: "doc" relations { name: "parent" rewrite { direct { subjects: "folder" } } } relations { name: "viewer" rewrite { from { tupleset: "parent" relation: "viewer" } } } }
      union, intersection and difference   | {"schema_version":"1.1","type_definitions":[{"type":"user"},{"type":"doc","relations":{"a":{"this":{}},"b":{"union":{"child":[{"this":{}},{"computedUserset":{"relation":"a"}}]}},"c":{"intersection":{"child":[{"computedUserset":{"relation":"a"}},{"computedUserset":{"relation":"b"}}]}},"d":{"difference":{"base":{"computedUserset":{"relation":"b"}},"subtract":{"computedUserset":{"relation":"a"}}}}},"metadata":{"relations":{"a":{"directly_related_user_types":[{"type":"user"}]},"b":{"directly_related_user_types":[{"type":"user"}]},"c":{"directly_related_user_types":[]},"d":{"directly_related_user_types":[]}}}}]} | types { name: "user" } types { name: "doc" relations { name: "a" rewrite { direct { subjects: "user" } } } relations { name: "b" rewrite { union { rewrites { direct { subjects: "user" } } rewrites { computed { relation: "a" } } } } } relations { name: "c" rewrite { intersection { rewrites { computed { relation: "a" } } rewrites { computed { relation: "b" } } } } } relations { name: "d" rewrite { difference { base { computed { relation: "b" } } subtract { computed { relation: "a" } } } } } }
      """)
  void translates(String name, String json, String expected) {
    WriteAuthorizationModelRequest request = openFga(json);
    Model model = translate(request);
    assertThat(model).isEqualTo(spectrum(expected));
    assertThat(translation.toOpenFga(model.toBuilder().setId("01JAZ6S3N6T3Y5W1Z8PKV9B4CD").build()))
        .satisfies(back -> {
          assertThat(back.getId()).isEqualTo("01JAZ6S3N6T3Y5W1Z8PKV9B4CD");
          assertThat(back.getSchemaVersion()).isEqualTo("1.1");
          assertThat(back.getTypeDefinitionsList()).isEqualTo(request.getTypeDefinitionsList());
        });
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', quoteCharacter = '\'', textBlock = """
      1.0 | the schema version '1.0' is not supported: use 1.1
      ''  | the schema version '' is not supported: use 1.1
      2.0 | the schema version '2.0' is not supported: use 1.1
      """)
  void refusesSchema(String schema, String message) {
    assertThatIllegalArgumentException().isThrownBy(() -> translate(openFga("{\"schema_version\":\"" + schema
        + "\",\"type_definitions\":[{\"type\":\"user\"}]}"))).withMessage(message);
  }

  @Test
  @DisplayName("refuses conditions on the model and on a directly related user type")
  void refusesConditions() {
    assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> translate(openFga("""
        {"schema_version":"1.1","type_definitions":[{"type":"user"}],
         "conditions":{"office":{"name":"office","expression":"true"}}}
        """))).withMessage("conditions are not supported: [office]");
    assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> translate(openFga("""
        {"schema_version":"1.1","type_definitions":[{"type":"user"},{"type":"doc","relations":{"viewer":{"this":{}}},
         "metadata":{"relations":{"viewer":{"directly_related_user_types":[{"type":"user","condition":"office"}]}}}}]}
        """))).withMessage("conditions are not supported: office");
  }
}
