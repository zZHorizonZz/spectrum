package dev.horizon.spectrum.tuple;

/**
 * One rule that a tuple must follow to fit an authorization model.
 */
public interface TupleRule {

  /**
   * Checks a tuple.
   *
   * @param tuple the tuple
   * @throws InvalidTupleException if the tuple breaks the rule, with the reason as the message
   */
  void check(RelationshipTuple tuple);
}
