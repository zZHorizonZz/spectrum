package dev.horizon.spectrum.tuple;

import com.google.protobuf.Struct;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.RelationshipCondition;
import dev.horizon.spectrum.openfga.v1.Tuple;
import java.util.ArrayList;
import java.util.List;

/**
 * Leaves out the stored tuples that do not count for a request: those that the model no longer allows, and those whose
 * condition the context does not meet.
 */
public final class TupleFilter {

  private final TypeSystem types;
  private final StoredTupleValidator validator;

  public TupleFilter(TypeSystem types) {
    this.types = types;
    this.validator = new StoredTupleValidator(types);
  }

  /**
   * Gives the tuples that the model allows.
   *
   * @param tuples the stored tuples
   * @return the tuples, in order
   */
  public List<RelationshipTuple> valid(List<Tuple> tuples) {
    return tuples.stream().map(tuple -> RelationshipTuple.parse(tuple.getKey())).filter(validator::isValid).toList();
  }

  /**
   * Gives the tuples that the model allows and whose condition a context meets. A tuple whose condition fails to
   * evaluate is left out, and when no tuple counts, its failure is the result.
   *
   * @param tuples the stored tuples
   * @param context the context of the request
   * @return the tuples, in order
   * @throws OpenFgaException if no tuple counts and a condition failed to evaluate
   */
  public List<RelationshipTuple> counting(List<Tuple> tuples, Struct context) {
    List<RelationshipTuple> counting = new ArrayList<>();
    OpenFgaException failure = null;
    for (RelationshipTuple tuple : valid(tuples)) {
      try {
        if (conditionMet(tuple, context)) {
          counting.add(tuple);
        }
      } catch (OpenFgaException failed) {
        failure = failed;
      }
    }
    if (counting.isEmpty() && failure != null) {
      throw failure;
    }
    return counting;
  }

  /**
   * Tells if a tuple meets its condition.
   *
   * @param context the context of the request
   * @return true if the tuple has no condition, or the context meets it
   * @throws OpenFgaException if the condition is not in the model or fails to evaluate
   */
  public boolean conditionMet(RelationshipTuple tuple, Struct context) {
    if (tuple.condition().isEmpty() || tuple.condition().get().getName().isEmpty()) {
      return true;
    }
    RelationshipCondition condition = tuple.condition().get();
    return types.condition(condition.getName())
        .orElseThrow(() -> new OpenFgaException(ErrorCode.validation_error,
            "failed to evaluate relationship condition: '" + condition.getName() + "' - condition was not found"))
        .evaluate(tuple.toString(), condition.getContext(), context);
  }
}
