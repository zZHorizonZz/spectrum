package dev.horizon.spectrum.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ULIDTest {

  @ParameterizedTest(name = "{0}")
  @CsvSource({
      "1970-01-01T00:00:00Z,       00000000000000000000000000",
      "2026-10-14T16:52:47Z,       01M4XN9ARR0000000000000000",
      "+10889-08-02T05:31:50.655Z, 7ZZZZZZZZZ0000000000000000" })
  void startsATime(Instant time, String text) {
    ULID ulid = ULID.startOf(time);
    assertThat(ulid).hasToString(text);
    assertThat(ulid.timestamp()).isEqualTo(time.toEpochMilli());
  }

  @Test
  void growsWithEachRandomUlid() {
    List<ULID> ulids = IntStream.range(0, 1000).mapToObj(i -> ULID.randomULID()).toList();
    assertThat(ulids).isSorted().doesNotHaveDuplicates();
    assertThat(ulids.stream().map(ULID::toString).toList()).isSorted().allMatch(text -> text.matches(
        "[0-9A-HJKMNP-TV-Z]{26}"));
  }

  @Test
  void readsItsText() {
    ULID ulid = ULID.randomULID();
    assertThat(ULID.fromString(ulid.toString())).isEqualTo(ulid).hasSameHashCodeAs(ulid);
    assertThat(ULID.fromString(ulid.toString().toLowerCase())).isEqualTo(ulid);
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "01M4XN9ARR000000000000000", "01M4XN9ARR00000000000000000", "8ZZZZZZZZZ0000000000000000",
      "01M4XN9ARR000000000000000U" })
  void refusesATextThatIsNotAUlid(String text) {
    assertThatIllegalArgumentException().isThrownBy(() -> ULID.fromString(text));
  }
}
