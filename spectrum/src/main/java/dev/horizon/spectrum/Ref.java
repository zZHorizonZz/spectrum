package dev.horizon.spectrum;

import java.util.regex.Pattern;

/**
 * An object or a subject, read from its text: {@code doc:9}, {@code user:*} or {@code group:eng#member}.
 *
 * @param type the type, for example {@code group}
 * @param id the id, or {@code *} for every object of the type
 * @param relation the relation of the object whose subjects the subject is, or empty
 */
record Ref(String type, String id, String relation) {

  /** The most characters of an object or a subject, the most a keyed column keeps. */
  static final int MAX_LENGTH = 255;

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");
  private static final Pattern STORE = Pattern.compile("[A-Za-z0-9_-]{1,32}");
  private static final Pattern ID = Pattern.compile("[^\\s#]+");

  /** Tells if a text is a name of a type or a relation. */
  static boolean name(String text) {
    return NAME.matcher(text).matches();
  }

  /** Gives a store, which must be 1 to 32 letters, digits, {@code _} and {@code -}. */
  static String store(String text) {
    if (!STORE.matcher(text).matches()) {
      throw new IllegalArgumentException("the store '" + text + "' is not valid: a store has 1 to 32 letters, digits,"
          + " '_' and '-'");
    }
    return text;
  }

  /** Reads an object, {@code type:id}. */
  static Ref object(String text) {
    Ref ref = read(text, "object");
    if (ref.wildcard() || ref.userset()) {
      throw invalid(text, "object", "an object is type:id");
    }
    return ref;
  }

  /** Reads a subject: {@code type:id}, {@code type:*} or {@code type:id#relation}. */
  static Ref subject(String text) {
    Ref ref = read(text, "subject");
    if (ref.wildcard() && ref.userset()) {
      throw invalid(text, "subject", "a subject is type:id, type:* or type:id#relation");
    }
    return ref;
  }

  private static Ref read(String text, String what) {
    if (text.length() > MAX_LENGTH) {
      throw invalid(text, what, "it has more than " + MAX_LENGTH + " characters");
    }
    int colon = text.indexOf(':');
    int hash = text.indexOf('#');
    String type = colon < 0 ? "" : text.substring(0, colon);
    String id = colon < 0 ? "" : text.substring(colon + 1, hash < 0 ? text.length() : hash);
    String relation = hash < 0 ? "" : text.substring(hash + 1);
    if (!name(type) || !ID.matcher(id).matches() || (hash >= 0 && !name(relation))) {
      throw invalid(text, what, "a type and a relation have at most 64 lower case letters, digits and '_', and an"
          + " id has no spaces and no '#'");
    }
    return new Ref(type, id, relation);
  }

  private static IllegalArgumentException invalid(String text, String what, String why) {
    return new IllegalArgumentException("the " + what + " '" + text + "' is not valid: " + why);
  }

  /** Tells if the subject is every object of its type. */
  boolean wildcard() {
    return id.equals("*");
  }

  /** Tells if the subject is the subjects of a relation of an object. */
  boolean userset() {
    return !relation.isEmpty();
  }

  /** Gives the object, without the relation. */
  String object() {
    return type + ":" + id;
  }

  /** Gives the text. */
  String text() {
    return userset() ? object() + "#" + relation : object();
  }
}
