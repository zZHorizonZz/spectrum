package dev.horizon.spectrum.tuple;

import dev.horizon.spectrum.model.TypeSystem;
import java.util.List;

/**
 * Checks a tuple to write, or a contextual tuple of a request: its parts must be in the model, and it must count as a
 * stored tuple would.
 */
public final class WriteValidator extends TupleValidator {

  public WriteValidator(TypeSystem types) {
    super(List.of(new ModelRule(types), new RelationRule(types), new TuplesetRule(types),
        new TypeRestrictionRule(types), new ConditionRule(types)));
  }
}
