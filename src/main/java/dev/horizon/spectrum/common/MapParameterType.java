package dev.horizon.spectrum.common;

import com.google.protobuf.Value;
import dev.cel.common.types.CelType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code map} parameter type: a JSON object whose keys are strings and whose values are of one type.
 */
public final class MapParameterType extends JsonParameterType {

  private final ParameterType values;

  public MapParameterType(ParameterType values) {
    this.values = values;
  }

  @Override
  public CelType celType() {
    return MapType.create(SimpleType.STRING, values.celType());
  }

  @Override
  public Object convert(Value value) {
    expect(value, Value.KindCase.STRUCT_VALUE, "map");
    Map<String, Object> map = new LinkedHashMap<>();
    value.getStructValue().getFieldsMap().forEach((key, element) -> map.put(key, values.convert(element)));
    return map;
  }
}
