package dev.horizon.spectrum.model;

import dev.horizon.spectrum.openfga.v1.RelationReference;

/**
 * An edge of a {@link RelationshipGraph}: a way that the tuples of a user type can give the user a relation.
 *
 * @param type how the tuples give the relation
 * @param target the relation, such as {@code doc#viewer}
 * @param tuplesetRelation the tupleset relation of a tuple to userset edge, or empty
 * @param involvesIntersectionOrExclusion true if the relation passes through an intersection or an exclusion, so that a
 * check must confirm each object that the edge gives
 */
public record RelationshipEdge(Type type, RelationReference target, String tuplesetRelation,
    boolean involvesIntersectionOrExclusion) {

  /**
   * How the tuples of an edge give the user the relation.
   */
  public enum Type {
    /** A tuple of the relation names the user. */
    DIRECT,
    /** The relation is a computed relation of the userset of the user. */
    COMPUTED_USERSET,
    /** A tuple of the tupleset relation names an object whose relation the user has. */
    TUPLE_TO_USERSET
  }

  RelationshipEdge throughIntersectionOrExclusion() {
    return new RelationshipEdge(type, target, tuplesetRelation, true);
  }

  @Override
  public String toString() {
    String relation = target.getType() + "#" + target.getRelation();
    return tuplesetRelation.isEmpty() ? type + " " + relation : type + " " + relation + " from " + tuplesetRelation;
  }
}
