package dev.horizon.spectrum.tuple;

import com.google.protobuf.Value;
import dev.horizon.spectrum.common.ContextValidator;
import dev.horizon.spectrum.common.EvaluableCondition;
import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.RelationshipCondition;
import java.util.List;
import java.util.Map;

/**
 * The rule that the condition of a tuple is one that a type restriction of its relation names for its user, with a
 * context of the parameters of the condition. A tuple without a condition needs a type restriction without one.
 */
public final class ConditionRule implements TupleRule {

  private final TypeSystem types;
  private final ContextValidator contexts = new ContextValidator();

  public ConditionRule(TypeSystem types) {
    this.types = types;
  }

  @Override
  public void check(RelationshipTuple tuple) {
    String type = tuple.object().type();
    if (!types.hasTypeInfo(type, tuple.relation())) {
      return;
    }
    List<RelationReference> restrictions = types.directlyRelatedUserTypes(type, tuple.relation());
    if (tuple.condition().isEmpty()) {
      if (restrictions.stream().noneMatch(restriction -> restriction.getCondition().isEmpty()
          && allows(restriction, tuple.user()))) {
        throw new InvalidTupleException("condition is missing");
      }
      return;
    }
    RelationshipCondition written = tuple.condition().get();
    String name = written.getName();
    if (name.chars().anyMatch(Character::isISOControl)) {
      throw new InvalidTupleException("condition name contains forbidden characters");
    }
    EvaluableCondition condition = types.condition(name)
        .orElseThrow(() -> new InvalidTupleException("undefined condition"));
    if (restrictions.stream().noneMatch(restriction -> restriction.getCondition().equals(name)
        && allows(restriction, tuple.user()))) {
      throw new InvalidTupleException("invalid condition for type restriction");
    }
    Map<String, Value> context = written.getContext().getFieldsMap();
    Map<String, Object> typed;
    try {
      contexts.validate(written.getContext());
      typed = condition.typed(context);
    } catch (IllegalArgumentException invalid) {
      throw new InvalidTupleException(invalid.getMessage());
    }
    for (String key : context.keySet()) {
      if (!typed.containsKey(key)) {
        throw new InvalidTupleException("found invalid context parameter: " + key);
      }
    }
  }

  private boolean allows(RelationReference restriction, TupleUser user) {
    if (!restriction.getType().equals(user.type())) {
      return false;
    }
    return switch (user) {
      case ObjectUser ignored ->
        restriction.getRelationOrWildcardCase() == RelationReference.RelationOrWildcardCase.RELATIONORWILDCARD_NOT_SET;
      case WildcardUser ignored -> restriction.hasWildcard();
      case UsersetUser userset -> restriction.getRelation().equals(userset.relation());
    };
  }
}
