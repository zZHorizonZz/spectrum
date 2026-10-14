package dev.horizon.spectrum.tuple;

import dev.horizon.spectrum.model.TypeSystem;

/**
 * The rule that the relation of a tuple is a relation of the type of its object, which the model may have dropped since
 * the tuple was written.
 */
public final class RelationRule implements TupleRule {

  private final TypeSystem types;

  public RelationRule(TypeSystem types) {
    this.types = types;
  }

  @Override
  public void check(RelationshipTuple tuple) {
    if (types.relation(tuple.object().type(), tuple.relation()).isEmpty()) {
      throw new InvalidTupleException("relation '" + tuple.object().type() + "#" + tuple.relation() + "' not found");
    }
  }
}
