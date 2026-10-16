package dev.horizon.spectrum.common;

import com.google.protobuf.Value;
import dev.cel.common.types.CelType;
import dev.cel.common.types.ListType;

/**
 * The {@code list} parameter type: a JSON array whose elements are of one type.
 */
public final class ListParameterType extends JsonParameterType {

  private final ParameterType elements;

  public ListParameterType(ParameterType elements) {
    this.elements = elements;
  }

  @Override
  public CelType celType() {
    return ListType.create(elements.celType());
  }

  @Override
  public Object convert(Value value) {
    expect(value, Value.KindCase.LIST_VALUE, "list");
    return value.getListValue().getValuesList().stream().map(elements::convert).toList();
  }
}
