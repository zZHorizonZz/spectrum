package dev.horizon.spectrum.common;

import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.Value;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import java.math.BigDecimal;

/**
 * The {@code uint} parameter type: a whole number of 64 bits that is not negative, as a JSON number or a string of
 * one.
 */
public final class UintParameterType extends NumberParameterType {

  @Override
  public CelType celType() {
    return SimpleType.UINT;
  }

  @Override
  public Object convert(Value value) {
    BigDecimal number = integer(value, "uint");
    if (number.signum() < 0) {
      throw new IllegalArgumentException("expected a uint value, but found int64 value '" + number + "'");
    }
    return UnsignedLong.valueOf(number.toBigIntegerExact());
  }
}
