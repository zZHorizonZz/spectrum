package dev.horizon.spectrum.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ULIDTest {

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
