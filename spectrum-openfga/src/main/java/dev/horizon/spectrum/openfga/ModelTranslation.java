package dev.horizon.spectrum.openfga;

import dev.horizon.spectrum.openfga.v1.AuthorizationModel;
import dev.horizon.spectrum.openfga.v1.DirectUserset;
import dev.horizon.spectrum.openfga.v1.Metadata;
import dev.horizon.spectrum.openfga.v1.ObjectRelation;
import dev.horizon.spectrum.openfga.v1.RelationMetadata;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.TupleToUserset;
import dev.horizon.spectrum.openfga.v1.TypeDefinition;
import dev.horizon.spectrum.openfga.v1.Userset;
import dev.horizon.spectrum.openfga.v1.Usersets;
import dev.horizon.spectrum.openfga.v1.Wildcard;
import dev.horizon.spectrum.v1.Computed;
import dev.horizon.spectrum.v1.Difference;
import dev.horizon.spectrum.v1.Direct;
import dev.horizon.spectrum.v1.From;
import dev.horizon.spectrum.v1.Model;
import dev.horizon.spectrum.v1.Relation;
import dev.horizon.spectrum.v1.Rewrite;
import dev.horizon.spectrum.v1.Rewrites;
import dev.horizon.spectrum.v1.Type;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translates the models of OpenFGA, schema 1.1, to Spectrum models and back.
 *
 * <p>
 * The rewrites map one to one: {@code this} is {@code direct}, with the subjects that the metadata of the relation
 * names; {@code computed_userset} is {@code computed}; {@code tuple_to_userset} is {@code from}; and {@code union},
 * {@code intersection} and {@code difference} keep their names. Spectrum has no conditions, so a model with one is
 * refused.
 */
final class ModelTranslation {

  /** The schema versions of OpenFGA models that Spectrum takes. */
  private static final Set<String> SCHEMAS = Set.of("1.1", "1.2");

  /** The schema version of the models that Spectrum gives. */
  private static final String SCHEMA = "1.1";

  /**
   * Translates the types of an OpenFGA model.
   *
   * @throws IllegalArgumentException if the schema version is not 1.1 or 1.2
   * @throws UnsupportedOperationException if the model has conditions
   */
  Model toSpectrum(String schema, List<TypeDefinition> definitions, Map<String, ?> conditions) {
    if (!SCHEMAS.contains(schema)) {
      throw new IllegalArgumentException("the schema version '" + schema + "' is not supported: use 1.1");
    }
    if (!conditions.isEmpty()) {
      throw new UnsupportedOperationException("conditions are not supported: " + conditions.keySet());
    }
    Model.Builder model = Model.newBuilder();
    for (TypeDefinition definition : definitions) {
      Type.Builder type = Type.newBuilder().setName(definition.getType());
      Map<String, RelationMetadata> metadata = definition.getMetadata().getRelationsMap();
      definition.getRelationsMap().forEach((name, userset) -> type.addRelations(Relation.newBuilder().setName(name)
          .setRewrite(rewrite(userset, metadata.getOrDefault(name, RelationMetadata.getDefaultInstance())))));
      model.addTypes(type);
    }
    return model.build();
  }

  /** Translates a Spectrum model with an id to an OpenFGA model. */
  AuthorizationModel toOpenFga(Model model) {
    AuthorizationModel.Builder translated = AuthorizationModel.newBuilder().setId(model.getId())
        .setSchemaVersion(SCHEMA);
    for (Type type : model.getTypesList()) {
      TypeDefinition.Builder definition = TypeDefinition.newBuilder().setType(type.getName());
      if (type.getRelationsCount() > 0) {
        Metadata.Builder metadata = Metadata.newBuilder();
        for (Relation relation : type.getRelationsList()) {
          Set<String> direct = new LinkedHashSet<>();
          definition.putRelations(relation.getName(), userset(relation.getRewrite(), direct));
          metadata.putRelations(relation.getName(), RelationMetadata.newBuilder()
              .addAllDirectlyRelatedUserTypes(direct.stream().map(this::reference).toList()).build());
        }
        definition.setMetadata(metadata);
      }
      translated.addTypeDefinitions(definition);
    }
    return translated.build();
  }

  private Rewrite rewrite(Userset userset, RelationMetadata metadata) {
    return switch (userset.getUsersetCase()) {
      case THIS -> Rewrite.newBuilder().setDirect(Direct.newBuilder()
          .addAllSubjects(metadata.getDirectlyRelatedUserTypesList().stream().map(this::subject).toList())).build();
      case COMPUTED_USERSET -> Rewrite.newBuilder().setComputed(Computed.newBuilder()
          .setRelation(userset.getComputedUserset().getRelation())).build();
      case TUPLE_TO_USERSET -> Rewrite.newBuilder().setFrom(From.newBuilder()
          .setTupleset(userset.getTupleToUserset().getTupleset().getRelation())
          .setRelation(userset.getTupleToUserset().getComputedUserset().getRelation())).build();
      case UNION -> Rewrite.newBuilder().setUnion(rewrites(userset.getUnion(), metadata)).build();
      case INTERSECTION -> Rewrite.newBuilder().setIntersection(rewrites(userset.getIntersection(), metadata)).build();
      case DIFFERENCE -> Rewrite.newBuilder().setDifference(Difference.newBuilder()
          .setBase(rewrite(userset.getDifference().getBase(), metadata))
          .setSubtract(rewrite(userset.getDifference().getSubtract(), metadata))).build();
      case USERSET_NOT_SET -> Rewrite.getDefaultInstance();
    };
  }

  private Rewrites rewrites(Usersets usersets, RelationMetadata metadata) {
    return Rewrites.newBuilder().addAllRewrites(usersets.getChildList().stream()
        .map(child -> rewrite(child, metadata)).toList()).build();
  }

  /** Reads a directly related user type: {@code user}, {@code user:*} or {@code group#member}. */
  private String subject(RelationReference reference) {
    if (!reference.getCondition().isEmpty()) {
      throw new UnsupportedOperationException("conditions are not supported: " + reference.getCondition());
    }
    return switch (reference.getRelationOrWildcardCase()) {
      case RELATION -> reference.getType() + "#" + reference.getRelation();
      case WILDCARD -> reference.getType() + ":*";
      case RELATIONORWILDCARD_NOT_SET -> reference.getType();
    };
  }

  private RelationReference reference(String subject) {
    if (subject.endsWith(":*")) {
      return RelationReference.newBuilder().setType(subject.substring(0, subject.length() - 2))
          .setWildcard(Wildcard.getDefaultInstance()).build();
    }
    int hash = subject.indexOf('#');
    return hash < 0 ? RelationReference.newBuilder().setType(subject).build()
        : RelationReference.newBuilder().setType(subject.substring(0, hash)).setRelation(subject.substring(hash + 1))
            .build();
  }

  private Userset userset(Rewrite rewrite, Set<String> direct) {
    return switch (rewrite.getRewriteCase()) {
      case DIRECT -> {
        direct.addAll(rewrite.getDirect().getSubjectsList());
        yield Userset.newBuilder().setThis(DirectUserset.getDefaultInstance()).build();
      }
      case COMPUTED -> Userset.newBuilder().setComputedUserset(ObjectRelation.newBuilder()
          .setRelation(rewrite.getComputed().getRelation())).build();
      case FROM -> Userset.newBuilder().setTupleToUserset(TupleToUserset.newBuilder()
          .setTupleset(ObjectRelation.newBuilder().setRelation(rewrite.getFrom().getTupleset()))
          .setComputedUserset(ObjectRelation.newBuilder().setRelation(rewrite.getFrom().getRelation()))).build();
      case UNION -> Userset.newBuilder().setUnion(usersets(rewrite.getUnion(), direct)).build();
      case INTERSECTION -> Userset.newBuilder().setIntersection(usersets(rewrite.getIntersection(), direct)).build();
      case DIFFERENCE -> Userset.newBuilder().setDifference(dev.horizon.spectrum.openfga.v1.Difference.newBuilder()
          .setBase(userset(rewrite.getDifference().getBase(), direct))
          .setSubtract(userset(rewrite.getDifference().getSubtract(), direct))).build();
      case REWRITE_NOT_SET -> Userset.getDefaultInstance();
    };
  }

  private Usersets usersets(Rewrites rewrites, Set<String> direct) {
    return Usersets.newBuilder().addAllChild(rewrites.getRewritesList().stream()
        .map(child -> userset(child, direct)).toList()).build();
  }
}
