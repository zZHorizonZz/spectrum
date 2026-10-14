package dev.horizon.spectrum.common;

import com.google.protobuf.Value;
import dev.cel.common.types.CelType;
import dev.horizon.spectrum.openfga.v1.ConditionParamTypeRef;
import dev.horizon.spectrum.openfga.v1.ConditionParamTypeRef.TypeName;

/**
 * The type of a parameter of a condition. It gives the CEL type of the parameter, and turns a JSON value of a context
 * into a CEL value.
 */
public interface ParameterType {

  /**
   * Gives the type of a type reference of a model.
   *
   * @throws IllegalArgumentException if the type is unknown, a map or a list does not have exactly one generic type, or
   * another type has one
   */
  static ParameterType of(ConditionParamTypeRef reference) {
    TypeName name = reference.getTypeName();
    boolean generic = name == TypeName.TYPE_NAME_MAP || name == TypeName.TYPE_NAME_LIST;
    int expected = generic ? 1 : 0;
    if (name == TypeName.TYPE_NAME_UNSPECIFIED || name == TypeName.UNRECOGNIZED) {
      throw new IllegalArgumentException("unknown condition parameter type '" + name + "'");
    }
    if (reference.getGenericTypesCount() != expected) {
      throw new IllegalArgumentException("type '" + name + "' requires " + expected + " generic types; found "
          + reference.getGenericTypesCount());
    }
    return switch (name) {
      case TYPE_NAME_BOOL -> new BoolParameterType();
      case TYPE_NAME_STRING -> new StringParameterType();
      case TYPE_NAME_INT -> new IntParameterType();
      case TYPE_NAME_UINT -> new UintParameterType();
      case TYPE_NAME_DOUBLE -> new DoubleParameterType();
      case TYPE_NAME_DURATION -> new DurationParameterType();
      case TYPE_NAME_TIMESTAMP -> new TimestampParameterType();
      case TYPE_NAME_IPADDRESS -> new IpAddressParameterType();
      case TYPE_NAME_MAP -> new MapParameterType(of(reference.getGenericTypes(0)));
      case TYPE_NAME_LIST -> new ListParameterType(of(reference.getGenericTypes(0)));
      default -> new AnyParameterType();
    };
  }

  /** Gives the CEL type, such as {@code int} or {@code map(string, int)}. */
  CelType celType();

  /**
   * Turns a JSON value of a context into a CEL value of this type.
   *
   * @throws IllegalArgumentException if the value is not one of this type
   */
  Object convert(Value value);
}
