package dev.horizon.spectrum.common;

import com.google.protobuf.Value;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;

/**
 * The {@code string} parameter type: a JSON string.
 */
public final class StringParameterType extends JsonParameterType {

  @Override
  public CelType celType() {
    return SimpleType.STRING;
  }

  @Override
  public Object convert(Value value) {
    expect(value, Value.KindCase.STRING_VALUE, "string");
    return value.getStringValue();
  }
}
