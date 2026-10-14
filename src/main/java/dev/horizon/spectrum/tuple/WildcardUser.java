package dev.horizon.spectrum.tuple;

/**
 * A user that is every object of a type, such as {@code user:*}.
 */
public record WildcardUser(String type) implements TupleUser {

  @Override
  public String userType() {
    return USERSET;
  }

  @Override
  public String toString() {
    return type + ":" + WILDCARD;
  }
}
