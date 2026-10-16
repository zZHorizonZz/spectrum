package dev.horizon.spectrum.tuple;

import dev.horizon.spectrum.openfga.v1.RelationshipCondition;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import java.util.Optional;

/**
 * A relationship tuple: a user that has a relation to an object, when its condition, if any, is met.
 *
 * @param object the object, such as {@code doc:1}
 * @param relation the relation, such as {@code viewer}
 * @param user the user, such as {@code user:anne}
 * @param condition the condition of the tuple, or empty
 */
public record RelationshipTuple(TupleObject object, String relation, TupleUser user,
    Optional<RelationshipCondition> condition) {

  /**
   * Reads the tuple of a key: its user first, then its object.
   *
   * @param key the key, with the condition of the tuple if it has one
   * @return the tuple
   * @throws InvalidTupleException if the user or the object is malformed
   */
  public static RelationshipTuple parse(TupleKey key) {
    TupleUser user = TupleUser.parse(key.getUser());
    return new RelationshipTuple(TupleObject.parse(key.getObject()), key.getRelation(), user,
        key.hasCondition() ? Optional.of(key.getCondition()) : Optional.empty());
  }

  /**
   * Gives a tuple of the same object and user, with another relation and no condition.
   *
   * @param other the relation, such as {@code editor}
   * @return the tuple
   */
  public RelationshipTuple withRelation(String other) {
    return new RelationshipTuple(object, other, user, Optional.empty());
  }

  /**
   * Gives the ID of the tuple: its object, relation and user, without its condition.
   *
   * @return the ID
   */
  public TupleId id() {
    return new TupleId(object.toString(), relation, user.toString());
  }

  @Override
  public String toString() {
    return id().toString();
  }
}
