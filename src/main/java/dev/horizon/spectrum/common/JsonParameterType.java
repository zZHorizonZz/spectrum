package dev.horizon.spectrum.common;

import com.google.protobuf.Value;

/**
 * A parameter type that reads a value of one JSON kind, such as a string or a number.
 */
public abstract class JsonParameterType implements ParameterType {

  /**
   * Checks that a value is of a JSON kind.
   *
   * @param kind the kind it must be
   * @param type the name of this type, for the message
   * @throws IllegalArgumentException if the value is of another kind
   */
  protected void expect(Value value, Value.KindCase kind, String type) {
    if (value.getKindCase() != kind) {
      throw new IllegalArgumentException("expected type value '" + type + "', but found '" + kind(value) + "'");
    }
  }

  /** Gives the JSON kind of a value as a message names it: bool, float64, string, list, map or null. */
  protected String kind(Value value) {
    return switch (value.getKindCase()) {
      case BOOL_VALUE -> "bool";
      case NUMBER_VALUE -> "float64";
      case STRING_VALUE -> "string";
      case LIST_VALUE -> "list";
      case STRUCT_VALUE -> "map";
      default -> "null";
    };
  }
}
