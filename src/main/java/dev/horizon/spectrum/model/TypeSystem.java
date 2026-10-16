package dev.horizon.spectrum.model;

import dev.horizon.spectrum.common.EvaluableCondition;

import dev.horizon.spectrum.openfga.v1.AuthorizationModel;
import dev.horizon.spectrum.openfga.v1.Relation;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.RelationTypeInfo;
import dev.horizon.spectrum.openfga.v1.TypeDefinition;
import dev.horizon.spectrum.openfga.v1.Userset;
import dev.horizon.spectrum.openfga.v1.Wildcard;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An authorization model, with lookups of its types, relations and conditions.
 */
public final class TypeSystem {

  public static final String SCHEMA_VERSION_1_1 = "1.1";

  static final String SCHEMA_VERSION_1_2 = "1.2";

  private final AuthorizationModel model;
  private final Map<String, TypeDefinition> typeDefinitions = new LinkedHashMap<>();
  private final Map<String, Map<String, Relation>> relations = new LinkedHashMap<>();
  private final Map<String, EvaluableCondition> conditions = new LinkedHashMap<>();

  public TypeSystem(AuthorizationModel model) {
    this.model = model;
    for (TypeDefinition definition : model.getTypeDefinitionsList()) {
      typeDefinitions.put(definition.getType(), definition);
      Map<String, Relation> typeRelations = new LinkedHashMap<>();
      definition.getRelationsMap().forEach((name, rewrite) -> typeRelations.put(name, Relation.newBuilder()
          .setName(name).setRewrite(rewrite).setTypeInfo(RelationTypeInfo.newBuilder()
              .addAllDirectlyRelatedUserTypes(definition.getMetadata().getRelationsMap().containsKey(name)
                  ? definition.getMetadata().getRelationsMap().get(name).getDirectlyRelatedUserTypesList()
                  : List.of()))
          .build()));
      relations.put(definition.getType(), typeRelations);
    }
    model.getConditionsMap().forEach((name, condition) -> conditions.put(name, new EvaluableCondition(condition)));
  }

  /**
   * Tells if the service supports the schema version of the model.
   *
   * @return true if the schema version is 1.1 or 1.2
   */
  public boolean isSupported() {
    return SCHEMA_VERSION_1_1.equals(schemaVersion()) || SCHEMA_VERSION_1_2.equals(schemaVersion());
  }

  AuthorizationModel model() {
    return model;
  }

  /**
   * Gives the ID of the model.
   *
   * @return the ID, a ULID
   */
  public String modelId() {
    return model.getId();
  }

  /**
   * Gives the schema version of the model.
   *
   * @return the version, such as {@code 1.1}
   */
  public String schemaVersion() {
    return model.getSchemaVersion();
  }

  Map<String, TypeDefinition> typeDefinitions() {
    return typeDefinitions;
  }

  /**
   * Gives the definition of a type.
   *
   * @param type the type, such as {@code doc}
   * @return the definition, or empty if the model does not define the type
   */
  public Optional<TypeDefinition> typeDefinition(String type) {
    return Optional.ofNullable(typeDefinitions.get(type));
  }

  Optional<Map<String, Relation>> relations(String type) {
    return Optional.ofNullable(relations.get(type));
  }

  /**
   * Gives a relation of a type, with its rewrite and its type restrictions.
   *
   * @param type the type, such as {@code doc}
   * @param relation the relation, such as {@code viewer}
   * @return the relation, or empty if the type is not in the model or does not define the relation
   */
  public Optional<Relation> relation(String type, String relation) {
    return relations(type).map(typeRelations -> typeRelations.get(relation));
  }

  Relation requireRelation(String type, String relation) {
    return relation(type, relation).orElseThrow(() -> new IllegalStateException("'" + type + "#" + relation
        + "' relation is undefined"));
  }

  Map<String, EvaluableCondition> conditions() {
    return conditions;
  }

  /**
   * Gives a condition of the model.
   *
   * @param name the name of the condition
   * @return the condition, or empty if the model does not define it
   */
  public Optional<EvaluableCondition> condition(String name) {
    return Optional.ofNullable(conditions.get(name));
  }

  /**
   * Gives the type restrictions of a relation: the users that its tuples can name.
   *
   * @param type the type, such as {@code doc}
   * @param relation the relation, such as {@code viewer}
   * @return the restrictions, such as {@code user}, {@code user:*} or {@code group#member}, or none if the relation is
   * not in the model
   */
  public List<RelationReference> directlyRelatedUserTypes(String type, String relation) {
    return relation(type, relation).map(found -> found.getTypeInfo().getDirectlyRelatedUserTypesList())
        .orElse(List.of());
  }

  /**
   * Gives the type restrictions of a relation that are usersets.
   *
   * @param type the type, such as {@code doc}
   * @param relation the relation, such as {@code viewer}
   * @return the restrictions, such as {@code group#member}, or none if the relation is not in the model
   */
  public List<RelationReference> directlyRelatedUsersets(String type, String relation) {
    return directlyRelatedUserTypes(type, relation).stream().filter(reference -> !reference.getRelation().isEmpty())
        .toList();
  }

  /**
   * Tells if the target relation allows tuples of the source: a type, a typed wildcard or a userset.
   *
   * @param target the relation, such as {@code doc#viewer}
   * @param source the user type, such as {@code user}, {@code user:*} or {@code group#member}
   * @return true if a type restriction of the target is the source
   */
  public boolean isDirectlyRelated(RelationReference target, RelationReference source) {
    return directlyRelatedUserTypes(target.getType(), target.getRelation()).stream()
        .anyMatch(restriction -> equal(source, restriction));
  }

  /**
   * Tells if the target relation allows the typed wildcard of a type.
   *
   * @param target the relation, such as {@code doc#viewer}
   * @param type the type of the wildcard, such as {@code user}
   * @return true if a type restriction of the target is the wildcard, such as {@code user:*}
   */
  public boolean isPubliclyAssignable(RelationReference target, String type) {
    return directlyRelatedUserTypes(target.getType(), target.getRelation()).stream()
        .anyMatch(restriction -> restriction.getType().equals(type) && restriction.hasWildcard());
  }

  /**
   * Tells if a relation has type restrictions to check tuples against, as models of schema 1.1 and later do.
   *
   * @param type the type, such as {@code doc}
   * @param relation the relation, such as {@code viewer}
   * @return true if the schema version is supported and the type defines the relation
   */
  public boolean hasTypeInfo(String type, String relation) {
    return isSupported() && relation(type, relation).isPresent();
  }

  /**
   * Tells if a relation is the tupleset of a tuple to userset rewrite of its type.
   *
   * @param type the type, such as {@code doc}
   * @param relation the relation, such as {@code parent}
   * @return true if a rewrite of the type reads the objects that the tuples of the relation name
   */
  public boolean isTuplesetRelation(String type, String relation) {
    return relations(type).orElse(Map.of()).values().stream()
        .anyMatch(other -> tuplesets(other.getRewrite()).contains(relation));
  }

  /** Tells if a rewrite has a direct part, {@code this}. */
  boolean isDirectlyAssignable(Relation relation) {
    return containsThis(relation.getRewrite());
  }

  /** Tells if the subjects of a relation pass through an intersection. */
  boolean involvesIntersection(String type, String relation) {
    return involves(type, relation, new HashSet<>(), Userset.UsersetCase.INTERSECTION);
  }

  /** Tells if the subjects of a relation pass through a difference. */
  boolean involvesExclusion(String type, String relation) {
    return involves(type, relation, new HashSet<>(), Userset.UsersetCase.DIFFERENCE);
  }

  /** Tells if two relation references name the same type and the same relation or wildcard. */
  private boolean equal(RelationReference left, RelationReference right) {
    if (!left.getType().equals(right.getType())) {
      return false;
    }
    if (left.getRelationOrWildcardCase() == RelationReference.RelationOrWildcardCase.RELATIONORWILDCARD_NOT_SET
        && right.getRelationOrWildcardCase() == RelationReference.RelationOrWildcardCase.RELATIONORWILDCARD_NOT_SET) {
      return true;
    }
    if (left.hasWildcard() && right.hasWildcard()) {
      return true;
    }
    return !left.getRelation().isEmpty() && left.getRelation().equals(right.getRelation());
  }

  /**
   * Gives a reference to a type, or to a relation of a type.
   *
   * @param type the type, such as {@code group}
   * @param relation the relation, such as {@code member}, or empty for the type alone
   * @return the reference, such as {@code group#member} or {@code group}
   */
  public RelationReference directReference(String type, String relation) {
    RelationReference.Builder reference = RelationReference.newBuilder().setType(type);
    if (!relation.isEmpty()) {
      reference.setRelation(relation);
    }
    return reference.build();
  }

  /**
   * Gives a reference to the typed wildcard of a type.
   *
   * @param type the type, such as {@code user}
   * @return the reference, such as {@code user:*}
   */
  public RelationReference wildcardReference(String type) {
    return RelationReference.newBuilder().setType(type).setWildcard(Wildcard.getDefaultInstance()).build();
  }

  private boolean containsThis(Userset rewrite) {
    return switch (rewrite.getUsersetCase()) {
      case THIS -> true;
      case UNION -> rewrite.getUnion().getChildList().stream().anyMatch(this::containsThis);
      case INTERSECTION -> rewrite.getIntersection().getChildList().stream().anyMatch(this::containsThis);
      case DIFFERENCE -> containsThis(rewrite.getDifference().getBase())
          || containsThis(rewrite.getDifference().getSubtract());
      default -> false;
    };
  }

  private Set<String> tuplesets(Userset rewrite) {
    Set<String> tuplesets = new HashSet<>();
    collectTuplesets(rewrite, tuplesets);
    return tuplesets;
  }

  private void collectTuplesets(Userset rewrite, Set<String> tuplesets) {
    switch (rewrite.getUsersetCase()) {
      case TUPLE_TO_USERSET -> tuplesets.add(rewrite.getTupleToUserset().getTupleset().getRelation());
      case UNION -> rewrite.getUnion().getChildList().forEach(child -> collectTuplesets(child, tuplesets));
      case INTERSECTION -> rewrite.getIntersection().getChildList()
          .forEach(child -> collectTuplesets(child, tuplesets));
      case DIFFERENCE -> {
        collectTuplesets(rewrite.getDifference().getBase(), tuplesets);
        collectTuplesets(rewrite.getDifference().getSubtract(), tuplesets);
      }
      default -> {
      }
    }
  }

  private boolean involves(String type, String relation, Set<String> visited, Userset.UsersetCase target) {
    if (!visited.add(type + "#" + relation)) {
      return false;
    }
    Optional<Relation> found = relation(type, relation);
    if (found.isEmpty()) {
      return false;
    }
    if (rewriteInvolves(type, found.get().getRewrite(), visited, target)) {
      return true;
    }
    for (RelationReference restriction : found.get().getTypeInfo().getDirectlyRelatedUserTypesList()) {
      if (!restriction.getRelation().isEmpty()
          && involves(restriction.getType(), restriction.getRelation(), visited, target)) {
        return true;
      }
    }
    return false;
  }

  private boolean rewriteInvolves(String type, Userset rewrite, Set<String> visited, Userset.UsersetCase target) {
    if (rewrite.getUsersetCase() == target) {
      return true;
    }
    return switch (rewrite.getUsersetCase()) {
      case COMPUTED_USERSET -> involves(type, rewrite.getComputedUserset().getRelation(), visited, target);
      case TUPLE_TO_USERSET -> {
        String computed = rewrite.getTupleToUserset().getComputedUserset().getRelation();
        yield directlyRelatedUserTypes(type, rewrite.getTupleToUserset().getTupleset().getRelation()).stream()
            .anyMatch(parent -> relation(parent.getType(), computed).isPresent()
                && involves(parent.getType(), computed, visited, target));
      }
      case UNION -> rewrite.getUnion().getChildList().stream()
          .anyMatch(child -> rewriteInvolves(type, child, visited, target));
      case INTERSECTION -> rewrite.getIntersection().getChildList().stream()
          .anyMatch(child -> rewriteInvolves(type, child, visited, target));
      case DIFFERENCE -> rewriteInvolves(type, rewrite.getDifference().getBase(), visited, target)
          || rewriteInvolves(type, rewrite.getDifference().getSubtract(), visited, target);
      default -> false;
    };
  }
}
