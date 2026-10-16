package dev.horizon.spectrum.tuple;

/**
 * The user of a tuple: an object such as {@code user:anne}, the typed wildcard of every object of a type such as {@code
 * user:*}, or the userset of a relation of an object such as {@code group:eng#member}.
 */
public sealed interface TupleUser permits ObjectUser, WildcardUser, UsersetUser {

  String WILDCARD = "*";

  String USER = "user";

  String USERSET = "userset";

  /**
   * Reads a user.
   *
   * @param text the user, such as {@code user:anne}, {@code user:*} or {@code group:eng#member}
   * @return an {@link ObjectUser}, a {@link WildcardUser} or a {@link UsersetUser}
   * @throws InvalidTupleException if the text is malformed, or is an ID without a type
   */
  static TupleUser parse(String text) {
    int hash = text.indexOf('#');
    if (hash > 0 && hash == text.lastIndexOf('#') && hash < text.length() - 1) {
      TupleObject object = parseObject(text.substring(0, hash));
      String relation = text.substring(hash + 1);
      if (object != null && !object.id().contains(WILDCARD) && relation.chars().noneMatch(c -> c == ':' || c == ' '
          || c == '*' || Character.isISOControl(c))) {
        return new UsersetUser(object, relation);
      }
    }
    if (hash < 0) {
      TupleObject object = parseObject(text);
      if (object != null) {
        return object.id().equals(WILDCARD) ? new WildcardUser(object.type()) : new ObjectUser(object);
      }
      if (!text.isEmpty() && text.chars().noneMatch(c -> c == ':' || c == ' ' || Character.isISOControl(c))) {
        throw new InvalidTupleException("the 'user' field must be an object (e.g. document:1) or an"
            + " 'object#relation' or a typed wildcard (e.g. group:*)");
      }
    }
    throw new InvalidTupleException("the 'user' field is malformed");
  }

  private static TupleObject parseObject(String text) {
    try {
      return TupleObject.parse(text);
    } catch (InvalidTupleException malformed) {
      return null;
    }
  }

  /**
   * Gives the type of the user.
   *
   * @return the type of the object, the wildcard or the object of the userset, such as {@code user} or {@code group}
   */
  String type();

  /**
   * Gives the value of the {@code user_type} column of a tuple of this user.
   *
   * @return {@link #USER} for an object, {@link #USERSET} for a typed wildcard or a userset
   */
  String userType();
}
