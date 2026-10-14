package dev.horizon.spectrum.common;

import com.google.protobuf.Duration;
import com.google.protobuf.Value;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code duration} parameter type: a string of numbers with units, such as {@code 1h30m} or {@code -1.5s}.
 */
public final class DurationParameterType extends JsonParameterType {

  private static final long NANOS_PER_SECOND = 1_000_000_000L;
  private static final Pattern PART = Pattern.compile("([0-9]*(?:\\.[0-9]*)?)(ns|us|µs|μs|ms|s|m|h)");
  private static final Map<String, Long> UNITS = Map.of("ns", 1L, "us", 1_000L, "µs", 1_000L, "μs", 1_000L, "ms",
      1_000_000L, "s", NANOS_PER_SECOND, "m", 60 * NANOS_PER_SECOND, "h", 3_600 * NANOS_PER_SECOND);

  @Override
  public CelType celType() {
    return SimpleType.DURATION;
  }

  @Override
  public Object convert(Value value) {
    if (value.getKindCase() != Value.KindCase.STRING_VALUE) {
      throw new IllegalArgumentException("expected a duration string, but found: " + kind(value));
    }
    String text = value.getStringValue();
    String rest = text.startsWith("-") || text.startsWith("+") ? text.substring(1) : text;
    long nanos = 0;
    if (!rest.equals("0")) {
      Matcher part = PART.matcher(rest);
      int end = 0;
      while (part.find() && part.start() == end && !part.group(1).isEmpty() && !part.group(1).equals(".")) {
        nanos += new BigDecimal(part.group(1)).multiply(BigDecimal.valueOf(UNITS.get(part.group(2)))).longValue();
        end = part.end();
      }
      if (end == 0 || end != rest.length()) {
        throw new IllegalArgumentException("expected a valid duration string, but found: '" + text + "'");
      }
    }
    if (text.startsWith("-")) {
      nanos = -nanos;
    }
    return Duration.newBuilder().setSeconds(nanos / NANOS_PER_SECOND).setNanos((int) (nanos % NANOS_PER_SECOND))
        .build();
  }
}
