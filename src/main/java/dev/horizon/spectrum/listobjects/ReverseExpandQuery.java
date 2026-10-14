package dev.horizon.spectrum.listobjects;

import com.google.protobuf.Struct;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.model.RelationshipEdge;
import dev.horizon.spectrum.model.RelationshipGraph;
import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.storage.TupleReader;
import dev.horizon.spectrum.tuple.ObjectUser;
import dev.horizon.spectrum.tuple.RelationshipTuple;
import dev.horizon.spectrum.tuple.TupleFilter;
import dev.horizon.spectrum.tuple.TupleObject;
import dev.horizon.spectrum.tuple.TupleUser;
import dev.horizon.spectrum.tuple.UsersetUser;
import dev.horizon.spectrum.tuple.WildcardUser;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Finds the objects of a type that a user may have a relation to. It follows the edges of the relationship graph back
 * from the user, reading the tuples that name the user, then those that name the objects it found, and so on. An object
 * that it reaches through an intersection or an exclusion is a candidate that a check must confirm.
 */
public final class ReverseExpandQuery {

  private final TypeSystem types;
  private final TupleReader reader;
  private final TupleFilter filter;
  private final RelationshipGraph graph;
  private final int maxDepth;
  private final Set<String> visitedUsersets = ConcurrentHashMap.newKeySet();
  private final Set<String> candidates = ConcurrentHashMap.newKeySet();

  public ReverseExpandQuery(TypeSystem types, TupleReader reader, int maxDepth) {
    this.types = types;
    this.reader = reader;
    this.filter = new TupleFilter(types);
    this.graph = new RelationshipGraph(types);
    this.maxDepth = maxDepth;
  }

  /**
   * One step of the expansion.
   *
   * @param objectType the type of the objects to find
   * @param relation the relation to find them by
   * @param user the user, typed wildcard or userset that the step starts from
   * @param context the context of the request
   * @param edge the edge that led to this step, or null for the first
   */
  private record Request(String store, String objectType, String relation, TupleUser user, Struct context,
      RelationshipEdge edge) {

    Request from(TupleUser other, RelationshipEdge through) {
      return new Request(store, objectType, relation, other, context, through);
    }
  }

  /**
   * Finds the objects.
   *
   * @param objectType the type of the objects, such as {@code doc}
   * @param relation the relation, such as {@code viewer}
   * @param user the user, such as {@code user:anne}, {@code user:*} or {@code group:eng#member}
   * @param context the context of the request
   * @param found takes each object once, with true if a check must confirm it
   * @return a future that completes when the expansion has found every object
   */
  public Future<Void> execute(String store, String objectType, String relation, String user, Struct context,
      BiConsumer<String, Boolean> found) {
    return expand(new Request(store, objectType, relation, TupleUser.parse(user), context, null), false, 0, found);
  }

  private Future<Void> expand(Request request, boolean throughIntersectionOrExclusion, int depth,
      BiConsumer<String, Boolean> found) {
    if (depth >= maxDepth) {
      return Future.failedFuture(new OpenFgaException(ErrorCode.authorization_model_resolution_too_complex,
          "Authorization Model resolution required too many rewrite rules to be resolved. Check your authorization"
              + " model for infinite recursion or too much nesting"));
    }
    TupleUser user = request.user();
    RelationReference source = switch (user) {
      case UsersetUser userset -> types.directReference(userset.type(), userset.relation());
      case WildcardUser wildcard -> types.wildcardReference(wildcard.type());
      case ObjectUser object -> types.directReference(object.type(), "");
    };
    if (user instanceof UsersetUser userset) {
      if (userset.type().equals(request.objectType()) && userset.relation().equals(request.relation())) {
        candidate(userset.object().toString(), throughIntersectionOrExclusion, found);
      }
      if (request.edge() != null && !visitedUsersets.add(userset + " " + request.edge())) {
        return Future.succeededFuture();
      }
    }
    List<RelationshipEdge> edges;
    try {
      edges = graph.prunedEdges(types.directReference(request.objectType(), request.relation()), source);
    } catch (IllegalStateException undefined) {
      return Future.failedFuture(undefined);
    }
    List<Future<Void>> steps = new ArrayList<>();
    for (RelationshipEdge edge : edges) {
      boolean through = throughIntersectionOrExclusion || edge.involvesIntersectionOrExclusion();
      Request step = request.from(user, edge);
      steps.add(switch (edge.type()) {
        case DIRECT, TUPLE_TO_USERSET -> readTuplesAndExpand(step, through, depth, found);
        case COMPUTED_USERSET -> expand(step.from(objectOf(user).userset(edge.target().getRelation()), edge),
            through, depth + 1, found);
      });
    }
    return join(steps);
  }

  private Future<Void> readTuplesAndExpand(Request request, boolean through, int depth,
      BiConsumer<String, Boolean> found) {
    RelationshipEdge edge = request.edge();
    TupleUser user = request.user();
    List<String> users = new ArrayList<>();
    String relation;
    if (edge.type() == RelationshipEdge.Type.DIRECT) {
      relation = edge.target().getRelation();
      if (!(user instanceof UsersetUser) && types.isPubliclyAssignable(edge.target(), user.type())) {
        users.add(new WildcardUser(user.type()).toString());
      }
      if (!(user instanceof WildcardUser)) {
        users.add(user.toString());
      }
    } else {
      relation = edge.tuplesetRelation();
      users.add(objectOf(user).toString());
    }
    return reader.readStartingWithUser(request.store(), new TupleReader.UserFilter(edge.target().getType(),
        relation, users)).compose(tuples -> {
          List<Future<Void>> steps = new ArrayList<>();
          for (RelationshipTuple tuple : filter.valid(tuples)) {
            boolean met;
            try {
              met = filter.conditionMet(tuple, request.context());
            } catch (OpenFgaException failed) {
              steps.add(Future.failedFuture(failed));
              continue;
            }
            if (met) {
              String next = edge.type() == RelationshipEdge.Type.DIRECT ? tuple.relation()
                  : edge.target().getRelation();
              steps.add(expand(request.from(tuple.object().userset(next), edge), through, depth + 1, found));
            }
          }
          return join(steps);
        });
  }

  /** Gives the object of a user: the object itself, the object of a userset, or {@code type:*} for a wildcard. */
  private TupleObject objectOf(TupleUser user) {
    return switch (user) {
      case ObjectUser object -> object.object();
      case UsersetUser userset -> userset.object();
      case WildcardUser wildcard -> new TupleObject(wildcard.type(), TupleUser.WILDCARD);
    };
  }

  private void candidate(String object, boolean throughIntersectionOrExclusion, BiConsumer<String, Boolean> found) {
    if (candidates.add(object)) {
      found.accept(object, throughIntersectionOrExclusion);
    }
  }

  /** Waits for every step, and fails with the first failure. */
  private Future<Void> join(List<Future<Void>> steps) {
    return Future.join(steps).transform(done -> {
      for (Future<Void> step : steps) {
        if (step.failed()) {
          return Future.failedFuture(step.cause());
        }
      }
      return Future.succeededFuture();
    });
  }
}
