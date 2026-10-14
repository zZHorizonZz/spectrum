package dev.horizon.spectrum.tuple;

/**
 * One rule that a tuple must follow to fit an authorization model.
 */
public interface TupleRule {

  void check(RelationshipTuple tuple);
}
