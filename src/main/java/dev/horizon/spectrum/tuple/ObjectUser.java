package dev.horizon.spectrum.tuple;

/**
 * A user that is one object, such as {@code user:anne}.
 *
 * @param object the object
 */
public record ObjectUser(TupleObject object) implements TupleUser {

  @Override
  public String type() {
    return object.type();
  }

  @Override
  public String userType() {
    return USER;
  }

  @Override
  public String toString() {
    return object.toString();
  }
}
