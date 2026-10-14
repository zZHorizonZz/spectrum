package dev.horizon.spectrum.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.AuthorizationModel;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ModelValidatorTest {

  private static final String USER = "{ \"type\": \"user\" }";

  private static AuthorizationModel model(String version, String types, String conditions) {
    AuthorizationModel.Builder model = AuthorizationModel.newBuilder();
    try {
      JsonFormat.parser().merge("{ \"schema_version\": \"" + version + "\", \"type_definitions\": [" + types + "]"
          + (conditions.isEmpty() ? "" : ", \"conditions\": " + conditions) + " }", model);
    } catch (InvalidProtocolBufferException invalid) {
      throw new IllegalArgumentException(invalid);
    }
    return model.build();
  }

  private static String doc(String relations, String metadata) {
    return "{ \"type\": \"doc\", \"relations\": { " + relations + " }, \"metadata\": { \"relations\": { " + metadata
        + " } } }";
  }

  private static String types(String relation, String... types) {
    return "\"" + relation + "\": { \"directly_related_user_types\": [" + String.join(", ", types) + "] }";
  }

  private static void validate(AuthorizationModel model) {
    new ModelValidator(new TypeSystem(model)).validate();
  }

  static Stream<Arguments> invalidModels() {
    String direct = "\"viewer\": { \"this\": {} }";
    return Stream.of(
        arguments(model("1.0", USER, ""), "invalid schema version"),
        arguments(model("1.1", USER + ", " + USER, ""), "an authorization model cannot contain duplicate types"),
        arguments(model("1.1", "{ \"type\": \"self\" }", ""), "the definition of type 'self' is invalid"),
        arguments(model("1.1", USER + ", " + doc("\"this\": { \"this\": {} }", types("this", USER)), ""),
            "the definition of relation 'this' in object type 'doc' is invalid: self and this are reserved keywords"),
        arguments(model("1.1", doc("\"viewer\": { \"computedUserset\": { \"relation\": \"owner\" } }", ""), ""),
            "'doc#owner' relation is undefined"),
        arguments(model("1.1", doc("\"viewer\": { \"computedUserset\": { \"relation\": \"viewer\" } }", ""), ""),
            "the definition of relation 'viewer' in object type 'doc' is invalid: invalid userset rewrite definition"),
        arguments(model("1.1", USER + ", " + doc("\"owner\": { \"this\": {} }, \"parent\": { \"computedUserset\":"
            + " { \"relation\": \"owner\" } }, \"viewer\": { \"tupleToUserset\": { \"tupleset\": { \"relation\":"
            + " \"parent\" }, \"computedUserset\": { \"relation\": \"owner\" } } }", types("owner", USER)), ""),
            "the 'doc#parent' relation is referenced in at least one tupleset and thus must be a direct relation"),
        arguments(model("1.1", USER + ", " + doc("\"parent\": { \"this\": {} }, \"viewer\": { \"tupleToUserset\":"
            + " { \"tupleset\": { \"relation\": \"parent\" }, \"computedUserset\": { \"relation\": \"viewer\" } } }",
            types("parent", USER)), ""),
            "undefined relation: viewer does not appear as a relation in any of the directly related user types"
                + " [user]"),
        arguments(model("1.1", USER + ", " + doc(direct, ""), ""),
            "the assignable relation 'viewer' in object type 'doc' must contain at least one relation type"),
        arguments(model("1.1", USER + ", " + doc("\"owner\": { \"this\": {} }, \"viewer\": { \"computedUserset\":"
            + " { \"relation\": \"owner\" } }", types("owner", USER) + ", " + types("viewer", USER)), ""),
            "the non-assignable relation 'viewer' in object type 'doc' should not contain a relation type"),
        arguments(model("1.1", USER + ", " + doc(direct, types("viewer", "{ \"type\": \"person\" }")), ""),
            "the relation type 'person' on 'viewer' in object type 'doc' is not valid"),
        arguments(model("1.1", USER + ", " + doc(direct, types("viewer", "{ \"type\": \"user\", \"condition\":"
            + " \"x\" }")), ""), "condition x is undefined for relation viewer"),
        arguments(model("1.1", doc(direct, types("viewer", "{ \"type\": \"doc\", \"relation\": \"viewer\" }")), ""),
            "the definition of relation 'viewer' in object type 'doc' is invalid: no entrypoints defined"),
        arguments(model("1.1", doc("\"a\": { \"computedUserset\": { \"relation\": \"b\" } }, \"b\":"
            + " { \"computedUserset\": { \"relation\": \"a\" } }", ""), ""),
            "the definition of relation 'a' in object type 'doc' is invalid: potential loop"),
        arguments(model("1.1", USER + ", " + doc(direct + ", \"editor\": { \"intersection\": { \"child\":"
            + " [{ \"computedUserset\": { \"relation\": \"viewer\" } }] } }", types("viewer", USER)), ""),
            "invalid relation: 'doc#editor' as intersection has less than 2 children"),
        arguments(model("1.1", USER, "{ \"x\": { \"name\": \"x\", \"expression\": \"1 + 1\" } }"),
            "failed to compile expression on condition 'x'"),
        arguments(model("1.1", USER, "{ \"x\": { \"name\": \"y\", \"expression\": \"true\" } }"),
            "condition key 'x' does not match condition name 'y'"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("invalidModels")
  void refusesAnInvalidModel(AuthorizationModel model, String message) {
    assertThatThrownBy(() -> validate(model)).isInstanceOf(OpenFgaException.class).hasMessageContaining(message);
  }

  @Test
  void acceptsAModelOfEveryRewrite() {
    assertThatCode(() -> validate(model("1.1", USER + ", " + doc("\"parent\": { \"this\": {} }, \"owner\":"
        + " { \"this\": {} }, \"blocked\": { \"this\": {} }, \"viewer\": { \"union\": { \"child\": [{ \"this\": {} },"
        + " { \"computedUserset\": { \"relation\": \"owner\" } }, { \"tupleToUserset\": { \"tupleset\":"
        + " { \"relation\": \"parent\" }, \"computedUserset\": { \"relation\": \"viewer\" } } }] } }, \"reader\":"
        + " { \"difference\": { \"base\": { \"computedUserset\": { \"relation\": \"viewer\" } }, \"subtract\":"
        + " { \"computedUserset\": { \"relation\": \"blocked\" } } } }, \"publisher\": { \"intersection\":"
        + " { \"child\": [{ \"computedUserset\": { \"relation\": \"owner\" } }, { \"computedUserset\":"
        + " { \"relation\": \"viewer\" } }] } }",
        types("parent", "{ \"type\": \"doc\" }") + ", "
            + types("owner", USER) + ", " + types("blocked", USER) + ", " + types("viewer", USER,
                "{ \"type\": \"user\", \"wildcard\": {} }", "{ \"type\": \"user\", \"condition\": \"x\" }")),
        "{ \"x\": { \"name\": \"x\", \"expression\": \"n > 1\", \"parameters\": { \"n\": { \"type_name\":"
            + " \"TYPE_NAME_INT\" } } } }")))
        .doesNotThrowAnyException();
  }
}
