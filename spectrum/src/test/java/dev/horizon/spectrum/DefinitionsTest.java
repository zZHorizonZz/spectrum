package dev.horizon.spectrum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.google.protobuf.TextFormat;
import dev.horizon.spectrum.v1.Model;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.FieldSource;

class DefinitionsTest {

  private static Model model(String text) {
    try {
      return TextFormat.parse(text, Model.class);
    } catch (TextFormat.ParseException invalid) {
      throw new IllegalArgumentException(invalid);
    }
  }

  @Nested
  @DisplayName("refuses a model")
  class Refusals {

    static final List<Arguments> MODELS = List.of(
        argumentSet("type that is not a name", """
            types { name: "Doc" }
            """, "the type 'Doc' is not a name: at most 64 lower case letters, digits and '_', starting with a letter"),
        argumentSet("type defined two times", """
            types { name: "user" }
            types { name: "user" }
            """, "the type 'user' is defined two times"),
        argumentSet("relation that is not a name", """
            types { name: "user" }
            types { name: "doc" relations { name: "View" rewrite { direct { subjects: "user" } } } }
            """, "the relation doc#View is not a name: at most 64 lower case letters, digits and '_', starting with a letter"),
        argumentSet("relation defined two times", """
            types { name: "user" }
            types {
              name: "doc"
              relations { name: "viewer" rewrite { direct { subjects: "user" } } }
              relations { name: "viewer" rewrite { direct { subjects: "user" } } }
            }
            """, "the relation doc#viewer is defined two times"),
        argumentSet("rewrite that is not set", """
            types { name: "doc" relations { name: "viewer" } }
            """, "the relation doc#viewer has a rewrite that is not set"),
        argumentSet("direct rewrite with no subjects", """
            types { name: "doc" relations { name: "viewer" rewrite { direct {} } } }
            """, "the relation doc#viewer has a direct rewrite with no subjects"),
        argumentSet("direct rewrite of a type the model does not have", """
            types { name: "doc" relations { name: "viewer" rewrite { direct { subjects: "user" } } } }
            """, "the relation doc#viewer allows 'user', whose type 'user' the model does not have"),
        argumentSet("direct rewrite of a relation the type does not have", """
            types { name: "group" }
            types { name: "doc" relations { name: "viewer" rewrite { direct { subjects: "group#member" } } } }
            """, "the relation doc#viewer allows 'group#member', but the type 'group' has no relation 'member'"),
        argumentSet("computed rewrite of a relation the type does not have", """
            types { name: "doc" relations { name: "viewer" rewrite { computed { relation: "owner" } } } }
            """, "the relation doc#viewer is computed from 'owner', which the type 'doc' does not have"),
        argumentSet("union of no rewrites", """
            types { name: "doc" relations { name: "viewer" rewrite { union {} } } }
            """, "the relation doc#viewer combines no rewrites"),
        argumentSet("from a relation the type does not have", """
            types { name: "doc" relations { name: "viewer" rewrite { from { tupleset: "parent" relation: "viewer" } } } }
            """, "the relation doc#viewer reads 'viewer' from 'parent', which the type 'doc' does not have"),
        argumentSet("from a relation that names subjects of relations", """
            types { name: "user" }
            types {
              name: "doc"
              relations { name: "parent" rewrite { direct { subjects: "doc#owner" } } }
              relations { name: "owner" rewrite { direct { subjects: "user" } } }
              relations { name: "viewer" rewrite { from { tupleset: "parent" relation: "owner" } } }
            }
            """, "the relation doc#viewer reads from 'parent', which must be a direct rewrite of types only, such as"
            + " direct { subjects: \"folder\" }"),
        argumentSet("from objects that do not have the relation", """
            types { name: "folder" }
            types {
              name: "doc"
              relations { name: "parent" rewrite { direct { subjects: "folder" } } }
              relations { name: "viewer" rewrite { from { tupleset: "parent" relation: "viewer" } } }
            }
            """, "the relation doc#viewer reads 'viewer' from 'parent', but none of [folder] has the relation"
            + " 'viewer'"));

    @ParameterizedTest
    @FieldSource("MODELS")
    void refuses(String text, String message) {
      assertThatIllegalArgumentException().isThrownBy(() -> new Definitions(model(text))).withMessage(message);
    }
  }

  @Nested
  @DisplayName("allows a subject in a relationship")
  class Allows {

    private final Definitions definitions = new Definitions(model("""
        types { name: "user" }
        types {
          name: "group"
          relations { name: "member" rewrite { direct { subjects: ["user", "group#member"] } } }
        }
        types {
          name: "doc"
          relations { name: "owner" rewrite { direct { subjects: "user" } } }
          relations { name: "viewer" rewrite { union {
            rewrites { direct { subjects: ["user:*", "group#member"] } }
            rewrites { computed { relation: "owner" } }
          } } }
        }
        """));

    @ParameterizedTest(name = "doc#{0}@{1}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
        owner  | user:ana         | true
        owner  | user:*           | false
        owner  | group:eng#member | false
        viewer | user:*           | true
        viewer | group:eng#member | true
        viewer | user:ana         | false
        viewer | group:eng        | false
        """)
    void allows(String relation, String subject, boolean expected) {
      assertThat(definitions.allows("doc", relation, Ref.subject(subject))).isEqualTo(expected);
    }
  }
}
