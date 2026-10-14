package dev.horizon.spectrum.tuple;

import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import java.util.List;

/**
 * Checks tuples against the rules of a validator, in order. Each validator names the rules that fit where its tuples
 * come from.
 */
public abstract class TupleValidator {

  private final List<TupleRule> rules;

  protected TupleValidator(List<TupleRule> rules) {
    this.rules = List.copyOf(rules);
  }

  public void validate(RelationshipTuple tuple) {
    rules.forEach(rule -> rule.check(tuple));
  }

  /**
   * Reads a key and checks its tuple.
   *
   * @throws OpenFgaException with the code {@code validation_error} if the key is malformed or its tuple breaks a rule
   */
  public RelationshipTuple validate(TupleKey key) {
    try {
      RelationshipTuple tuple = RelationshipTuple.parse(key);
      validate(tuple);
      return tuple;
    } catch (InvalidTupleException invalid) {
      throw new OpenFgaException(ErrorCode.validation_error, "Invalid tuple '" + new TupleId(key) + "'. Reason: "
          + invalid.getMessage());
    }
  }

  public boolean isValid(RelationshipTuple tuple) {
    try {
      validate(tuple);
      return true;
    } catch (InvalidTupleException invalid) {
      return false;
    }
  }
}
