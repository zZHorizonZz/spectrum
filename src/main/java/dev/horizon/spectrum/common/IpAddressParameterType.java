package dev.horizon.spectrum.common;

import com.google.protobuf.Value;
import dev.cel.common.types.CelType;
import dev.cel.common.types.OpaqueType;

/**
 * The {@code ipaddress} parameter type: a string of an IPv4 or IPv6 address, as an {@link IpAddress}.
 */
public final class IpAddressParameterType extends JsonParameterType {

  public static final OpaqueType CEL_TYPE = OpaqueType.create("IPAddress");

  @Override
  public CelType celType() {
    return CEL_TYPE;
  }

  @Override
  public Object convert(Value value) {
    if (value.getKindCase() != Value.KindCase.STRING_VALUE) {
      throw new IllegalArgumentException("expected an ipaddress string, but found: " + kind(value));
    }
    return new IpAddress(value.getStringValue());
  }
}
