package dev.horizon.spectrum.tuple;

import dev.horizon.spectrum.model.TypeSystem;
import java.util.List;

/**
 * Checks that a stored tuple still counts in the model, which may have changed since the tuple was written: its
 * relation, the tupleset rule, its type restriction and its condition.
 */
public final class StoredTupleValidator extends TupleValidator {

  public StoredTupleValidator(TypeSystem types) {
    super(List.of(new RelationRule(types), new TuplesetRule(types), new TypeRestrictionRule(types),
        new ConditionRule(types)));
  }
}
