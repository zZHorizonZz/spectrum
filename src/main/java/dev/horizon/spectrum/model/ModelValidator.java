package dev.horizon.spectrum.model;

import dev.horizon.spectrum.OpenFgaException;

import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.Relation;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.TypeDefinition;
import dev.horizon.spectrum.openfga.v1.Userset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks that an authorization model is valid: its names, rewrites, type restrictions, entrypoints, cycles and
 * conditions.
 */
public final class ModelValidator {

  private static final Set<String> RESERVED = Set.of("self", "this");

  private final TypeSystem types;

  public ModelValidator(TypeSystem types) {
    this.types = types;
  }

  /**
   * The result of a search for an entrypoint of a relation.
   *
   * @param found true if a tuple can give the relation a user
   * @param loop true if the search went around a loop of computed relations
   */
  private record Entrypoint(boolean found, boolean loop) {
  }

  /**
   * Checks the model.
   *
   * @throws OpenFgaException with the code {@code invalid_authorization_model} if the model is not valid
   */
  public void validate() {
    if (!types.isSupported()) {
      throw invalid("invalid schema version");
    }
    Set<String> seen = new HashSet<>();
    for (TypeDefinition definition : types.model().getTypeDefinitionsList()) {
      if (!seen.add(definition.getType())) {
        throw invalid("an authorization model cannot contain duplicate types");
      }
    }
    validateNames();
    for (String type : new TreeSet<>(types.typeDefinitions().keySet())) {
      for (String relation : new TreeSet<>(types.typeDefinitions().get(type).getRelationsMap().keySet())) {
        validateRelation(type, relation);
      }
    }
    types.conditions().forEach((key, condition) -> {
      if (!key.equals(condition.name())) {
        throw invalid("condition key '" + key + "' does not match condition name '" + condition.name() + "'");
      }
      try {
        condition.compile();
      } catch (IllegalArgumentException invalid) {
        throw invalid(invalid.getMessage());
      }
    });
  }

  private void validateNames() {
    for (TypeDefinition definition : types.typeDefinitions().values()) {
      String type = definition.getType();
      if (type.isEmpty()) {
        throw invalid("the type name of a type definition cannot be an empty string");
      }
      if (RESERVED.contains(type)) {
        throw invalid("the definition of type '" + type + "' is invalid");
      }
      for (String relation : definition.getRelationsMap().keySet()) {
        if (relation.isEmpty()) {
          throw invalid("type '" + type + "' defines a relation with an empty string for a name");
        }
        if (RESERVED.contains(relation)) {
          throw invalidRelation(type, relation, "self and this are reserved keywords");
        }
      }
    }
  }

  private void validateRelation(String type, String relation) {
    Userset rewrite = types.typeDefinitions().get(type).getRelationsMap().get(relation);
    validateRewrite(type, relation, rewrite);
    validateTypeRestrictions(type, relation);
    Entrypoint entrypoint = entrypoint(type, relation, rewrite, new HashMap<>());
    if (!entrypoint.found()) {
      throw invalidRelation(type, relation, entrypoint.loop() ? "potential loop" : "no entrypoints defined");
    }
    if (hasCycle(type, relation, rewrite, new HashSet<>())) {
      throw invalidRelation(type, relation, "an authorization model cannot contain a cycle");
    }
  }

  private void validateRewrite(String type, String relation, Userset rewrite) {
    switch (rewrite.getUsersetCase()) {
      case USERSET_NOT_SET -> throw invalidRelation(type, relation, "invalid userset rewrite definition");
      case COMPUTED_USERSET -> {
        String computed = rewrite.getComputedUserset().getRelation();
        if (computed.equals(relation)) {
          throw invalidRelation(type, relation, "invalid userset rewrite definition");
        }
        if (types.relation(type, computed).isEmpty()) {
          throw invalid("'" + type + "#" + computed + "' relation is undefined");
        }
      }
      case TUPLE_TO_USERSET -> {
        String tupleset = rewrite.getTupleToUserset().getTupleset().getRelation();
        Optional<Relation> tuplesetRelation = types.relation(type, tupleset);
        if (tuplesetRelation.isEmpty()) {
          throw invalid("'" + type + "#" + tupleset + "' relation is undefined");
        }
        if (tuplesetRelation.get().getRewrite().getUsersetCase() != Userset.UsersetCase.THIS) {
          throw invalid("the '" + type + "#" + tupleset + "' relation is referenced in at least one tupleset and"
              + " thus must be a direct relation");
        }
        String computed = rewrite.getTupleToUserset().getComputedUserset().getRelation();
        List<RelationReference> parents = tuplesetRelation.get().getTypeInfo().getDirectlyRelatedUserTypesList();
        if (parents.stream().noneMatch(parent -> types.relation(parent.getType(), computed).isPresent())) {
          throw invalid("undefined relation: " + computed + " does not appear as a relation in any of the directly"
              + " related user types " + parents.stream().map(RelationReference::getType).toList());
        }
      }
      case UNION -> rewrite.getUnion().getChildList().forEach(child -> validateRewrite(type, relation, child));
      case INTERSECTION -> rewrite.getIntersection().getChildList()
          .forEach(child -> validateRewrite(type, relation, child));
      case DIFFERENCE -> {
        validateRewrite(type, relation, rewrite.getDifference().getBase());
        validateRewrite(type, relation, rewrite.getDifference().getSubtract());
      }
      default -> {
      }
    }
  }

  private void validateTypeRestrictions(String type, String relationName) {
    Relation relation = types.relation(type, relationName).orElseThrow();
    List<RelationReference> restrictions = relation.getTypeInfo().getDirectlyRelatedUserTypesList();
    boolean assignable = types.isDirectlyAssignable(relation);
    if (assignable && restrictions.isEmpty()) {
      throw invalid("the assignable relation '" + relationName + "' in object type '" + type
          + "' must contain at least one relation type");
    }
    if (!assignable && !restrictions.isEmpty()) {
      throw invalid("the non-assignable relation '" + relationName + "' in object type '" + type
          + "' should not contain a relation type");
    }
    for (RelationReference restriction : restrictions) {
      if (types.relations(restriction.getType()).isEmpty()) {
        throw invalidRelationType(type, relationName, restriction);
      }
      if (restriction.getRelationOrWildcardCase() != RelationReference.RelationOrWildcardCase.RELATIONORWILDCARD_NOT_SET) {
        if (types.isTuplesetRelation(type, relationName)) {
          throw invalidRelationType(type, relationName, restriction);
        }
        if (!restriction.hasWildcard()) {
          if (restriction.getRelation().isEmpty()) {
            throw invalid("the relation type '" + restriction.getType() + "' on '" + relationName
                + "' in object type '" + type + "' has an empty relation reference: empty relation reference");
          }
          if (types.relation(restriction.getType(), restriction.getRelation()).isEmpty()) {
            throw invalidRelationType(type, relationName, restriction);
          }
        }
      }
      if (!restriction.getCondition().isEmpty() && types.condition(restriction.getCondition()).isEmpty()) {
        throw invalid("condition " + restriction.getCondition() + " is undefined for relation " + relationName);
      }
    }
  }

  /**
   * Searches for an entrypoint of a relation: a type restriction to a type or a typed wildcard that the rewrite
   * reaches. The visited relations record whether each has an entrypoint.
   */
  private Entrypoint entrypoint(String type, String relation, Userset rewrite,
      Map<String, Map<String, Boolean>> visited) {
    Map<String, Map<String, Boolean>> current = new HashMap<>(visited);
    current.computeIfAbsent(type, ignored -> new HashMap<>()).put(relation, false);
    Relation definition = relation(type, relation);
    switch (rewrite.getUsersetCase()) {
      case THIS -> {
        for (RelationReference restriction : definition.getTypeInfo().getDirectlyRelatedUserTypesList()) {
          if (restriction.getRelationOrWildcardCase() == RelationReference.RelationOrWildcardCase.RELATIONORWILDCARD_NOT_SET
              || restriction.hasWildcard()) {
            current.get(type).put(relation, true);
            return new Entrypoint(true, false);
          }
          Relation userset = relation(restriction.getType(), restriction.getRelation());
          if (current.getOrDefault(restriction.getType(), Map.of()).containsKey(restriction.getRelation())) {
            continue;
          }
          if (entrypoint(restriction.getType(), restriction.getRelation(), userset.getRewrite(), current).found()) {
            return new Entrypoint(true, false);
          }
        }
        return new Entrypoint(false, false);
      }
      case COMPUTED_USERSET -> {
        String computed = rewrite.getComputedUserset().getRelation();
        Relation computedRelation = relation(type, computed);
        Boolean known = current.get(type).get(computed);
        if (known != null) {
          return new Entrypoint(known, true);
        }
        return entrypoint(type, computed, computedRelation.getRewrite(), current);
      }
      case TUPLE_TO_USERSET -> {
        String computed = rewrite.getTupleToUserset().getComputedUserset().getRelation();
        Relation tupleset = relation(type, rewrite.getTupleToUserset().getTupleset().getRelation());
        for (RelationReference parent : tupleset.getTypeInfo().getDirectlyRelatedUserTypesList()) {
          Optional<Relation> parentRelation = types.relation(parent.getType(), computed);
          if (parentRelation.isEmpty()) {
            continue;
          }
          Boolean known = current.getOrDefault(parent.getType(), Map.of()).get(computed);
          if (known != null) {
            if (known) {
              return new Entrypoint(true, false);
            }
            continue;
          }
          if (entrypoint(parent.getType(), computed, parentRelation.get().getRewrite(), current).found()) {
            return new Entrypoint(true, false);
          }
        }
        return new Entrypoint(false, false);
      }
      case UNION -> {
        List<Userset> children = rewrite.getUnion().getChildList();
        if (children.size() < 2) {
          throw invalid("invalid relation: '" + type + "#" + relation + "' as union has less than 2 children");
        }
        boolean loop = false;
        for (Userset child : children) {
          Entrypoint result = entrypoint(type, relation, child, visited);
          if (result.found()) {
            return new Entrypoint(true, false);
          }
          loop = loop || result.loop();
        }
        return new Entrypoint(false, loop);
      }
      case INTERSECTION -> {
        List<Userset> children = rewrite.getIntersection().getChildList();
        if (children.size() < 2) {
          throw invalid("invalid relation: '" + type + "#" + relation + "' as intersection has less than 2"
              + " children");
        }
        for (Userset child : children) {
          Entrypoint result = entrypoint(type, relation, child, visited);
          if (!result.found()) {
            return new Entrypoint(false, result.loop());
          }
        }
        return new Entrypoint(true, false);
      }
      case DIFFERENCE -> {
        Entrypoint base = entrypoint(type, relation, rewrite.getDifference().getBase(), visited);
        if (!base.found()) {
          return new Entrypoint(false, base.loop());
        }
        Entrypoint subtract = entrypoint(type, relation, rewrite.getDifference().getSubtract(), visited);
        return subtract.found() ? new Entrypoint(true, false) : new Entrypoint(false, subtract.loop());
      }
      default -> throw invalidRelation(type, relation, "invalid userset rewrite definition");
    }
  }

  /** Tells if a rewrite reaches its own relation through computed relations alone. */
  private boolean hasCycle(String type, String relation, Userset rewrite, Set<String> visited) {
    visited.add(type + "#" + relation);
    Set<String> path = new HashSet<>(visited);
    return switch (rewrite.getUsersetCase()) {
      case COMPUTED_USERSET -> {
        String computed = rewrite.getComputedUserset().getRelation();
        yield visited.contains(type + "#" + computed)
            || hasCycle(type, computed, relation(type, computed).getRewrite(), path);
      }
      case UNION -> hasCycle(type, relation, rewrite.getUnion().getChildList(), path);
      case INTERSECTION -> hasCycle(type, relation, rewrite.getIntersection().getChildList(), path);
      case DIFFERENCE -> hasCycle(type, relation, List.of(rewrite.getDifference().getBase(),
          rewrite.getDifference().getSubtract()), path);
      default -> false;
    };
  }

  private boolean hasCycle(String type, String relation, List<Userset> children, Set<String> path) {
    for (Userset child : children) {
      if (hasCycle(type, relation, child, path)) {
        return true;
      }
    }
    return false;
  }

  private Relation relation(String type, String relation) {
    return types.relation(type, relation).orElseThrow(() -> invalid("undefined type definition for '" + type + "#"
        + relation + "'"));
  }

  private OpenFgaException invalidRelationType(String type, String relation, RelationReference restriction) {
    String related = restriction.getRelation().isEmpty() ? restriction.getType()
        : restriction.getType() + "#" + restriction.getRelation();
    return invalid("the relation type '" + related + "' on '" + relation + "' in object type '" + type
        + "' is not valid");
  }

  private OpenFgaException invalidRelation(String type, String relation, String cause) {
    return invalid("the definition of relation '" + relation + "' in object type '" + type + "' is invalid: "
        + cause);
  }

  private OpenFgaException invalid(String message) {
    return new OpenFgaException(ErrorCode.invalid_authorization_model, message);
  }
}
