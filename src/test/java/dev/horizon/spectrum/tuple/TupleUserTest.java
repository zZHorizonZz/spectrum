package dev.horizon.spectrum.tuple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TupleUserTest {

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      user:anne        | ObjectUser   | user  | user
      user:*           | WildcardUser | user  | userset
      group:eng#member | UsersetUser  | group | userset
      """)
  void readsAUser(String text, String kind, String type, String userType) {
    TupleUser user = TupleUser.parse(text);
    assertThat(user.getClass().getSimpleName()).isEqualTo(kind);
    assertThat(user.type()).isEqualTo(type);
    assertThat(user.userType()).isEqualTo(userType);
    assertThat(user).hasToString(text);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      '*'            | must be an object
      anne           | must be an object
      ':1'           | malformed
      'doc:'         | malformed
      'doc:1#'       | malformed
      'doc:a b'      | malformed
      group:*#member | malformed
      doc:a#b#viewer | malformed
      """)
  void refusesAText(String text, String reason) {
    assertThatThrownBy(() -> TupleUser.parse(text)).isInstanceOf(InvalidTupleException.class)
        .hasMessageContaining(reason);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      doc:1   | true
      doc:*   | true
      ':1'    | false
      'doc:'  | false
      doc:1#a | false
      a:b:c   | false
      """)
  void readsAnObject(String text, boolean valid) {
    if (valid) {
      assertThat(TupleObject.parse(text)).hasToString(text);
    } else {
      assertThatThrownBy(() -> TupleObject.parse(text)).isInstanceOf(InvalidTupleException.class);
    }
  }
}
