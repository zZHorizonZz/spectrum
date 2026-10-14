package dev.horizon.spectrum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CursorTest {

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      doc:1 viewer user:ana       | ZG9jOjEAdmlld2VyAHVzZXI6YW5h
      group:eng member group:a#b  | Z3JvdXA6ZW5nAG1lbWJlcgBncm91cDphI2I
      01JAZ6RE1H6ZQ4WKQ2M0R0S8XQ  | MDFKQVo2UkUxSDZaUTRXS1EyTTBSMFM4WFE
      """)
  void roundTrips(String keys, String token) {
    List<String> parts = List.of(keys.split(" "));
    assertThat(new Cursor(parts).token()).isEqualTo(token).matches("[A-Za-z0-9_-]+");
    assertThat(Cursor.of(token, parts.size()).keys()).isEqualTo(parts);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({ "'not base64!', 1", "ZG9jOjEAdmlld2VyAHVzZXI6YW5h, 2" })
  void refuses(String token, int size) {
    assertThatIllegalArgumentException().isThrownBy(() -> Cursor.of(token, size))
        .withMessage("the page token '" + token + "' is not valid");
  }
}
