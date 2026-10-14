package dev.horizon.spectrum.tuple;

import dev.horizon.spectrum.model.TypeSystem;
import java.util.List;

/**
 * Checks the tuple of a request that asks about a relation, such as a Check or an assertion: its user, object and
 * relation must be in the model, and nothing more.
 */
public final class KeyValidator extends TupleValidator {

  public KeyValidator(TypeSystem types) {
    super(List.of(new ModelRule(types)));
  }
}
