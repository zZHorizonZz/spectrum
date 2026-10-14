package dev.horizon.spectrum.common;

import com.google.protobuf.Value;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;

public final class IntParameterType extends NumberParameterType {

  @Override
  public CelType celType() {
    return SimpleType.INT;
  }

  @Override
  public Object convert(Value value) {
    return integer(value, "int").longValueExact();
  }
}
