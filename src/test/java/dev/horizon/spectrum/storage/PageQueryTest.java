package dev.horizon.spectrum.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.aip158.PageTokenCodec;
import dev.horizon.spectrum.OpenFgaException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PageQueryTest {

  private final PageTokenCodec codec = new PageTokenCodec(new byte[32]);

  private String next(String... list) {
    return new PageQuery(codec, 2, "", list).page(List.of("a", "b"), List.of("01M4XN9ARR0000000000000000"))
        .nextPageToken();
  }

  @Test
  void continuesAtTheCursorOfItsToken() {
    PageQuery query = new PageQuery(codec, 2, next("Read", "store"), "Read", "store");
    assertThat(query.cursor()).containsExactly("01M4XN9ARR0000000000000000");
    assertThat(query.from()).isEqualTo("01M4XN9ARR0000000000000000");
  }

  @Test
  void startsAtTheFirstPageWithoutAToken() {
    PageQuery query = new PageQuery(codec, 0, "", "Read", "store");
    assertThat(query.cursor()).isEmpty();
    assertThat(query.size()).isEqualTo(PageQuery.DEFAULT_SIZE);
    assertThat(query.page(List.of("a"), List.of()).nextPageToken()).isEmpty();
  }

  @Test
  void refusesATokenOfAnotherList() {
    String token = next("Read", "store");
    assertThatThrownBy(() -> new PageQuery(codec, 2, token, "Read", "other")).isInstanceOf(OpenFgaException.class)
        .hasMessage("Invalid continuation token");
  }

  @ParameterizedTest
  @ValueSource(strings = { "not a token", "bm9rZXk", "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" })
  void refusesATokenThatItDidNotGive(String token) {
    assertThatThrownBy(() -> new PageQuery(codec, 2, token, "Read", "store")).isInstanceOf(OpenFgaException.class)
        .hasMessage("Invalid continuation token");
  }
}
