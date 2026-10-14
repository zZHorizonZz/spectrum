package dev.horizon.spectrum.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.Condition;
import dev.horizon.spectrum.openfga.v1.ConditionParamTypeRef;
import dev.horizon.spectrum.openfga.v1.ConditionParamTypeRef.TypeName;
import dev.horizon.spectrum.openfga.v1.RelationshipCondition;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EvaluableConditionTest {

  private static Struct struct(String json) {
    Struct.Builder struct = Struct.newBuilder();
    try {
      JsonFormat.parser().merge(json, struct);
    } catch (InvalidProtocolBufferException invalid) {
      throw new IllegalArgumentException(invalid);
    }
    return struct.build();
  }

  private static ConditionParamTypeRef type(String name) {
    if (name.startsWith("map<") || name.startsWith("list<")) {
      String kind = name.substring(0, name.indexOf('<'));
      return ConditionParamTypeRef.newBuilder().setTypeName(TypeName.valueOf("TYPE_NAME_" + kind.toUpperCase()))
          .addGenericTypes(type(name.substring(name.indexOf('<') + 1, name.length() - 1))).build();
    }
    return ConditionParamTypeRef.newBuilder().setTypeName(TypeName.valueOf("TYPE_NAME_" + name.toUpperCase()))
        .build();
  }

  private static boolean evaluate(String type, String expression, String value) {
    EvaluableCondition condition = new EvaluableCondition(Condition.newBuilder().setName("c").setExpression(expression)
        .putParameters("x", type(type)).build());
    TupleKey tuple = TupleKey.newBuilder().setObject("doc:1").setRelation("viewer").setUser("user:anne")
        .setCondition(RelationshipCondition.newBuilder().setName("c")).build();
    return condition.evaluate("doc:1#viewer@user:anne", tuple.getCondition().getContext(), struct("{\"x\": " + value + "}"));
  }

  @ParameterizedTest(name = "{0} {1} with {2}: {3}")
  @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
      bool         | x                                            | true                      | true
      string       | x == 'eu'                                    | "eu"                      | true
      int          | x > 2                                        | 3                         | true
      int          | x > 2                                        | "3"                       | true
      uint         | x > 2u                                       | 3                         | true
      double       | x < 1.5                                      | 1.25                      | true
      duration     | x > duration('1h')                           | "1h30m"                   | true
      duration     | x < duration('1s')                           | "500ms"                   | true
      timestamp    | x < timestamp('2027-01-01T00:00:00Z')        | "2026-10-15T08:00:00Z"    | true
      ipaddress    | x.in_cidr('10.0.0.0/8')                      | "10.1.2.3"                | true
      ipaddress    | x.in_cidr('10.0.0.0/8')                      | "192.168.1.1"             | false
      ipaddress    | x == ipaddress('2001:db8::1')                | "2001:db8::1"             | true
      list<string> | 'b' in x                                     | ["a", "b"]                | true
      map<int>     | x['a'] == 1                                  | {"a": 1}                  | true
      any          | x == 'eu'                                    | "eu"                      | true
      list<int>    | x.all(n, n > 0)                              | [1, 2, 3]                 | true
      """)
  void evaluatesEachParameterType(String type, String expression, String value, boolean met) {
    assertThat(evaluate(type, expression, value)).isEqualTo(met);
  }

  @ParameterizedTest(name = "{0} with {1}")
  @CsvSource(delimiter = '|', textBlock = """
      int       | 1.5
      int       | "one"
      uint      | -1
      bool      | "true"
      duration  | "an hour"
      timestamp | "yesterday"
      ipaddress | "10.1.2"
      ipaddress | 10
      """)
  void refusesAValueOfAnotherType(String type, String value) {
    assertThatThrownBy(() -> evaluate(type, "true", value)).isInstanceOf(OpenFgaException.class)
        .hasMessageContaining("parameter type error on condition 'c'");
  }

  @Test
  void refusesAContextThatMissesAParameter() {
    EvaluableCondition condition = new EvaluableCondition(Condition.newBuilder().setName("c").setExpression("x && y")
        .putParameters("x", type("bool")).putParameters("y", type("bool")).build());
    TupleKey tuple = TupleKey.newBuilder().setObject("doc:1").setRelation("viewer").setUser("user:anne")
        .setCondition(RelationshipCondition.newBuilder().setName("c").setContext(struct("{\"x\": true}"))).build();
    assertThatThrownBy(
        () -> condition.evaluate("doc:1#viewer@user:anne", tuple.getCondition().getContext(), Struct.getDefaultInstance()))
        .isInstanceOf(OpenFgaException.class)
        .hasMessage("failed to evaluate relationship condition: tuple 'doc:1#viewer@user:anne' is missing context"
            + " parameters '[y]'");
  }

  @Test
  void prefersTheContextOfTheTuple() {
    EvaluableCondition condition = new EvaluableCondition(Condition.newBuilder().setName("c").setExpression("x")
        .putParameters("x", type("bool")).build());
    TupleKey tuple = TupleKey.newBuilder().setObject("doc:1").setRelation("viewer").setUser("user:anne")
        .setCondition(RelationshipCondition.newBuilder().setName("c").setContext(struct("{\"x\": false}"))).build();
    assertThat(condition.evaluate("doc:1#viewer@user:anne", tuple.getCondition().getContext(), struct("{\"x\": true}")))
        .isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      x + 1
      y
      x.unknown()
      """)
  void refusesAnExpressionThatIsNotABoolOfItsParameters(String expression) {
    EvaluableCondition condition = new EvaluableCondition(Condition.newBuilder().setName("c").setExpression(expression)
        .putParameters("x", type("int")).build());
    assertThatThrownBy(condition::compile).isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("failed to compile expression on condition 'c'");
  }
}
