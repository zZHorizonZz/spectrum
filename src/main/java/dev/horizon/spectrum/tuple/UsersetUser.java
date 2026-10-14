package dev.horizon.spectrum.tuple;

/**
 * A user that is every user with a relation to an object, such as {@code group:eng#member}.
 */
public record UsersetUser(TupleObject object, String relation) implements TupleUser {

  @Override
  public String type() {
    return object.type();
  }

  @Override
  public String userType() {
    return USERSET;
  }

  @Override
  public String toString() {
    return object + "#" + relation;
  }
}
