package dev.horizon.spectrum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RefTest {

  @Nested
  @DisplayName("reads a subject")
  class Subjects {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
        user:ana         | user  | ana | ''     | false | false
        user:*           | user  | *   | ''     | true  | false
        group:eng#member | group | eng | member | false | true
        doc:A-9.x/y      | doc   | A-9.x/y | '' | false | false
        """)
    void reads(String text, String type, String id, String relation, boolean wildcard, boolean userset) {
      Ref ref = Ref.subject(text);
      assertThat(ref).isEqualTo(new Ref(type, id, relation));
      assertThat(ref.wildcard()).isEqualTo(wildcard);
      assertThat(ref.userset()).isEqualTo(userset);
      assertThat(ref.text()).isEqualTo(text);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
        user           | the subject 'user' is not valid: a type and a relation have at most 64 lower case letters, digits and '_', and an id has no spaces and no '#'
        User:ana       | the subject 'User:ana' is not valid: a type and a relation have at most 64 lower case letters, digits and '_', and an id has no spaces and no '#'
        user:a b       | the subject 'user:a b' is not valid: a type and a relation have at most 64 lower case letters, digits and '_', and an id has no spaces and no '#'
        group:eng#     | the subject 'group:eng#' is not valid: a type and a relation have at most 64 lower case letters, digits and '_', and an id has no spaces and no '#'
        user:*#member  | the subject 'user:*#member' is not valid: a subject is type:id, type:* or type:id#relation
        """)
    void refuses(String text, String message) {
      assertThatIllegalArgumentException().isThrownBy(() -> Ref.subject(text)).withMessage(message);
    }

    @Test
    @DisplayName("refuses a subject of more than 255 characters")
    void refusesLong() {
      String text = "user:" + "a".repeat(251);
      assertThatIllegalArgumentException().isThrownBy(() -> Ref.subject(text))
          .withMessage("the subject '" + text + "' is not valid: it has more than 255 characters");
      assertThat(Ref.subject("user:" + "a".repeat(250)).text()).hasSize(255);
    }
  }

  @Nested
  @DisplayName("reads an object")
  class Objects {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
        doc:*         | the object 'doc:*' is not valid: an object is type:id
        group:eng#member | the object 'group:eng#member' is not valid: an object is type:id
        """)
    void refuses(String text, String message) {
      assertThatIllegalArgumentException().isThrownBy(() -> Ref.object(text)).withMessage(message);
    }
  }
}
