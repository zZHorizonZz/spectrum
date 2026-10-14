package dev.horizon.spectrum.common;

import com.google.protobuf.Value;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import java.math.BigDecimal;

/**
 * The {@code double} parameter type: a 64-bit floating point number. A number beyond its range is refused, not turned
 * into an infinity.
 */
public final class DoubleParameterType extends NumberParameterType {

  @Override
  public CelType celType() {
    return SimpleType.DOUBLE;
  }

  @Override
  public Object convert(Value value) {
    BigDecimal number = number(value, "double");
    double converted = number.doubleValue();
    if (Double.isInfinite(converted)) {
      throw new IllegalArgumentException("expected a double value, but found out of range value '" + number + "'");
    }
    return converted;
  }
}
