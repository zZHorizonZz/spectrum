package dev.horizon.spectrum.common;

import com.google.protobuf.NullValue;
import com.google.protobuf.Value;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AnyParameterType implements ParameterType {

  @Override
  public CelType celType() {
    return SimpleType.DYN;
  }

  @Override
  public Object convert(Value value) {
    return switch (value.getKindCase()) {
      case BOOL_VALUE -> value.getBoolValue();
      case NUMBER_VALUE -> value.getNumberValue();
      case STRING_VALUE -> value.getStringValue();
      case LIST_VALUE -> value.getListValue().getValuesList().stream().map(this::convert).toList();
      case STRUCT_VALUE -> {
        Map<String, Object> map = new LinkedHashMap<>();
        value.getStructValue().getFieldsMap().forEach((key, element) -> map.put(key, convert(element)));
        yield map;
      }
      default -> NullValue.NULL_VALUE;
    };
  }
}
