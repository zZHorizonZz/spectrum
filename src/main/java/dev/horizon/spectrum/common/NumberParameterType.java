package dev.horizon.spectrum.common;

import com.google.protobuf.Value;
import java.math.BigDecimal;

/**
 * A parameter type of numbers, which reads a JSON number or a string of one.
 */
public abstract class NumberParameterType extends JsonParameterType {

  /**
   * Reads a number.
   *
   * @param value a number, or a string of one
   * @param type the name of this type, for the message
   * @return the number, exactly as the value has it
   * @throws IllegalArgumentException if the value is not a number
   */
  protected BigDecimal number(Value value, String type) {
    return switch (value.getKindCase()) {
      case NUMBER_VALUE -> BigDecimal.valueOf(value.getNumberValue());
      case STRING_VALUE -> {
        try {
          yield new BigDecimal(value.getStringValue());
        } catch (NumberFormatException invalid) {
          throw new IllegalArgumentException("expected a " + type + " value, but found invalid string value '"
              + value.getStringValue() + "'", invalid);
        }
      }
      default -> throw new IllegalArgumentException("expected type value '" + type + "', but found '" + kind(value)
          + "'");
    };
  }

  /**
   * Reads a whole number.
   *
   * @param value a number, or a string of one
   * @param type the name of this type, for the message
   * @return the number, which has no fraction
   * @throws IllegalArgumentException if the value is not a whole number
   */
  protected BigDecimal integer(Value value, String type) {
    BigDecimal number = number(value, type);
    if (number.stripTrailingZeros().scale() > 0) {
      throw new IllegalArgumentException("expected an " + type + " value, but found numeric value '"
          + number.stripTrailingZeros().toPlainString() + "'");
    }
    return number;
  }
}
