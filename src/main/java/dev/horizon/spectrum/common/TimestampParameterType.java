package dev.horizon.spectrum.common;

import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * The {@code timestamp} parameter type: an RFC 3339 string.
 */
public final class TimestampParameterType extends JsonParameterType {

  @Override
  public CelType celType() {
    return SimpleType.TIMESTAMP;
  }

  @Override
  public Object convert(Value value) {
    if (value.getKindCase() != Value.KindCase.STRING_VALUE) {
      throw new IllegalArgumentException("expected RFC 3339 formatted timestamp string, but found: " + kind(value));
    }
    try {
      Instant instant = Instant.parse(value.getStringValue());
      return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    } catch (DateTimeParseException invalid) {
      throw new IllegalArgumentException("expected RFC 3339 formatted timestamp string, but found '"
          + value.getStringValue() + "'", invalid);
    }
  }
}
