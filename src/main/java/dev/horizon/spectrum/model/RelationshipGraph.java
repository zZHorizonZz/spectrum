package dev.horizon.spectrum.model;

import dev.horizon.spectrum.openfga.v1.Relation;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.Userset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The relationship graph of an authorization model: the edges by which the tuples of a user type lead to a relation.
 */
public final class RelationshipGraph {

  private final TypeSystem types;

  public RelationshipGraph(TypeSystem types) {
    this.types = types;
  }

  /**
   * Gives the edges from a user type to a relation, pruned for a reverse expansion: of an intersection only the first
   * operand, and of an exclusion only the base, each marked as needing a check.
   *
   * @param target the relation, such as {@code doc#viewer}
   * @param source the user type: a type, a typed wildcard or a userset, such as {@code group#member}
   * @return the edges, in the order of the rewrite, or none if the source cannot have the relation
   */
  public List<RelationshipEdge> prunedEdges(RelationReference target, RelationReference source) {
    return edges(target, source, new HashSet<>());
  }

  private List<RelationshipEdge> edges(RelationReference target, RelationReference source, Set<String> visited) {
    if (!visited.add(target.getType() + "#" + target.getRelation())) {
      return List.of();
    }
    Optional<Relation> relation = types.relation(target.getType(), target.getRelation());
    if (relation.isEmpty()) {
      throw new IllegalStateException("'" + target.getType() + "#" + target.getRelation()
          + "' relation is undefined");
    }
    return edges(target, source, relation.get().getRewrite(), visited);
  }

  private List<RelationshipEdge> edges(RelationReference target, RelationReference source, Userset rewrite,
      Set<String> visited) {
    RelationReference direct = types.directReference(target.getType(), target.getRelation());
    List<RelationshipEdge> edges = new ArrayList<>();
    switch (rewrite.getUsersetCase()) {
      case THIS -> {
        if (types.isDirectlyRelated(target, source) || types.isPubliclyAssignable(target, source.getType())) {
          edges.add(new RelationshipEdge(RelationshipEdge.Type.DIRECT, direct, "", false));
        }
        for (RelationReference restriction : types.directlyRelatedUserTypes(target.getType(), target.getRelation())) {
          if (!restriction.getRelation().isEmpty()) {
            edges.addAll(edges(restriction, source, visited));
          }
        }
      }
      case COMPUTED_USERSET -> {
        String computed = rewrite.getComputedUserset().getRelation();
        if (target.getType().equals(source.getType()) && computed.equals(source.getRelation())) {
          edges.add(new RelationshipEdge(RelationshipEdge.Type.COMPUTED_USERSET, direct, "", false));
        }
        edges.addAll(edges(types.directReference(target.getType(), computed), source, visited));
      }
      case TUPLE_TO_USERSET -> {
        String tupleset = rewrite.getTupleToUserset().getTupleset().getRelation();
        String computed = rewrite.getTupleToUserset().getComputedUserset().getRelation();
        for (RelationReference restriction : types.directlyRelatedUserTypes(target.getType(), tupleset)) {
          if (types.relation(restriction.getType(), computed).isEmpty()) {
            continue;
          }
          if (restriction.getType().equals(source.getType()) && computed.equals(source.getRelation())) {
            edges.add(new RelationshipEdge(RelationshipEdge.Type.TUPLE_TO_USERSET, direct, tupleset,
                types.involvesIntersection(restriction.getType(), computed)
                    || types.involvesExclusion(restriction.getType(), computed)));
          }
          edges.addAll(edges(types.directReference(restriction.getType(), computed), source, visited));
        }
      }
      case UNION -> rewrite.getUnion().getChildList()
          .forEach(child -> edges.addAll(edges(target, source, child, visited)));
      case INTERSECTION -> edges(target, source, rewrite.getIntersection().getChild(0), visited)
          .forEach(edge -> edges.add(edge.throughIntersectionOrExclusion()));
      case DIFFERENCE -> edges(target, source, rewrite.getDifference().getBase(), visited)
          .forEach(edge -> edges.add(edge.throughIntersectionOrExclusion()));
      default -> throw new IllegalStateException("unexpected userset rewrite " + rewrite.getUsersetCase());
    }
    return edges;
  }
}
