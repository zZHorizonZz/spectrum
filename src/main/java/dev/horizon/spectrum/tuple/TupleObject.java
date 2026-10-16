package dev.horizon.spectrum.tuple;

/**
 * The object of a tuple: a type and an ID, written {@code type:id}.
 *
 * @param type the type, such as {@code doc}
 * @param id the ID, such as {@code 1}
 */
public record TupleObject(String type, String id) {

  /**
   * Reads an object: a type and an ID that one {@code :} separates, without a {@code #}, a space or a control
   * character.
   *
   * @param text the object, such as {@code doc:1}
   * @return the object
   * @throws InvalidTupleException if the text is not an object
   */
  public static TupleObject parse(String text) {
    int colon = text.indexOf(':');
    if (colon <= 0 || colon == text.length() - 1 || text.indexOf(':', colon + 1) >= 0 || text.chars()
        .anyMatch(c -> Character.isISOControl(c) || c == '#' || c == ' ')) {
      throw new InvalidTupleException("invalid 'object' field format");
    }
    return new TupleObject(text.substring(0, colon), text.substring(colon + 1));
  }

  /**
   * Gives the userset of a relation of this object.
   *
   * @param relation the relation, such as {@code member}
   * @return the userset, such as {@code group:eng#member}
   */
  public UsersetUser userset(String relation) {
    return new UsersetUser(this, relation);
  }

  @Override
  public String toString() {
    return type + ":" + id;
  }
}
