package dev.horizon.spectrum.tuple;

import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.openfga.v1.Userset;

/**
 * The rule that a tuple of a tupleset relation names one object as its user, as a tuple to userset rewrite reads it.
 * The rewrite of a tupleset relation must be its direct tuples alone.
 */
public final class TuplesetRule implements TupleRule {

  private final TypeSystem types;

  public TuplesetRule(TypeSystem types) {
    this.types = types;
  }

  @Override
  public void check(RelationshipTuple tuple) {
    String type = tuple.object().type();
    if (!types.isTuplesetRelation(type, tuple.relation())) {
      return;
    }
    String relation = "'" + type + "#" + tuple.relation() + "'";
    Userset rewrite = types.relation(type, tuple.relation()).orElseThrow().getRewrite();
    if (rewrite.getUsersetCase() != Userset.UsersetCase.THIS
        && rewrite.getUsersetCase() != Userset.UsersetCase.USERSET_NOT_SET) {
      throw new InvalidTupleException("unexpected rewrite encountered with tupleset relation " + relation);
    }
    switch (tuple.user()) {
      case ObjectUser ignored -> {
      }
      case WildcardUser ignored -> throw new InvalidTupleException(
          "unexpected wildcard relationship with tupleset relation " + relation);
      case UsersetUser userset -> throw new InvalidTupleException("unexpected user '" + userset
          + "' with tupleset relation " + relation);
    }
  }
}
