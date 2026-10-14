package dev.horizon.spectrum;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UlidGeneratorTest {

  @ParameterizedTest(name = "{0}")
  @CsvSource({
      "1970-01-01T00:00:00Z, 0000000000",
      "2026-10-14T16:52:47Z, 01M4XN9ARR",
      "+10889-08-02T05:31:50.655Z, 7ZZZZZZZZZ" })
  void encodesTime(Instant time, String prefix) {
    String ulid = new UlidGenerator(Clock.fixed(time, ZoneOffset.UTC)).next();
    assertThat(ulid).hasSize(26).startsWith(prefix).matches("[0-9A-HJKMNP-TV-Z]{26}");
  }

  @Test
  @DisplayName("grows within one millisecond, and across milliseconds")
  void grows() {
    UlidGenerator fixed = new UlidGenerator(Clock.fixed(Instant.parse("2026-10-14T16:52:47Z"), ZoneOffset.UTC));
    List<String> same = IntStream.range(0, 1000).mapToObj(i -> fixed.next()).toList();
    UlidGenerator system = new UlidGenerator(Clock.systemUTC());
    List<String> later = IntStream.range(0, 1000).mapToObj(i -> system.next()).toList();
    assertThat(same).isSorted().doesNotHaveDuplicates();
    assertThat(later).isSorted().doesNotHaveDuplicates();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
      "1970-01-01T00:00:00Z, 00000000000000000000000000",
      "2026-10-14T16:52:47Z, 01M4XN9ARR0000000000000000" })
  void floors(Instant time, String expected) {
    UlidGenerator ids = new UlidGenerator(Clock.fixed(time, ZoneOffset.UTC));
    assertThat(ids.floor(time)).isEqualTo(expected).isLessThan(ids.next());
  }
}
