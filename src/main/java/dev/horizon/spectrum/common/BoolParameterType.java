package dev.horizon.spectrum.common;

import com.google.protobuf.Value;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;

/**
 * The {@code bool} parameter type: a JSON boolean.
 */
public final class BoolParameterType extends JsonParameterType {

  @Override
  public CelType celType() {
    return SimpleType.BOOL;
  }

  @Override
  public Object convert(Value value) {
    expect(value, Value.KindCase.BOOL_VALUE, "bool");
    return value.getBoolValue();
  }
}
