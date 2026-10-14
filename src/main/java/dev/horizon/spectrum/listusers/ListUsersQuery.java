package dev.horizon.spectrum.listusers;

import com.google.protobuf.Struct;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.OpenFgaServiceOptions;
import dev.horizon.spectrum.model.RelationshipGraph;
import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.ListUsersRequest;
import dev.horizon.spectrum.openfga.v1.Object;
import dev.horizon.spectrum.openfga.v1.TupleToUserset;
import dev.horizon.spectrum.openfga.v1.TypedWildcard;
import dev.horizon.spectrum.openfga.v1.User;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import dev.horizon.spectrum.openfga.v1.Userset;
import dev.horizon.spectrum.storage.CombinedTupleReader;
import dev.horizon.spectrum.storage.TupleReader;
import dev.horizon.spectrum.tuple.ObjectUser;
import dev.horizon.spectrum.tuple.RelationshipTuple;
import dev.horizon.spectrum.tuple.TupleFilter;
import dev.horizon.spectrum.tuple.TupleObject;
import dev.horizon.spectrum.tuple.TupleUser;
import dev.horizon.spectrum.tuple.UsersetUser;
import dev.horizon.spectrum.tuple.WildcardUser;
import dev.horizon.spectrum.tuple.WriteValidator;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Lists the users of the types of some filters that have a relation to an object. The query expands the rewrite of the
 * relation down to the users. An exclusion from a typed wildcard gives the wildcard, and leaves out the users that it
 * excludes. At the deadline, the list ends with the users found so far.
 */
public final class ListUsersQuery {

  private final Vertx vertx;
  private final TupleReader reader;
  private final OpenFgaServiceOptions options;

  public ListUsersQuery(Vertx vertx, TupleReader reader, OpenFgaServiceOptions options) {
    this.vertx = vertx;
    this.reader = reader;
    this.options = options;
  }

  /**
   * A user that an expansion found.
   *
   * @param user the user, such as {@code user:anne}, {@code user:*} or {@code group:eng#member}
   * @param related false if an exclusion found that the user does not have the relation
   * @param excluded the users that an exclusion leaves out of a typed wildcard
   */
  private record Found(String user, boolean related, Set<String> excluded) {

    Found(String user) {
      this(user, true, Set.of());
    }
  }

  /**
   * One step of the expansion.
   *
   * @param object the object, such as {@code doc:1}
   * @param visited the usersets that the path to this step expanded, to find cycles
   * @param depth the number of expansions on the path to this step
   */
  private record Step(String object, String relation, Set<String> visited, int depth) {

    Step to(String other, String otherRelation) {
      return new Step(other, otherRelation, visited, depth);
    }
  }

  private final class Expansion {

    private final String store;
    private final List<UserTypeFilter> filters;
    private final Struct context;
    private final TupleReader tuples;
    private final TypeSystem types;
    private final TupleFilter filter;

    Expansion(ListUsersRequest request, TypeSystem types) {
      this.store = request.getStoreId();
      this.filters = request.getUserFiltersList();
      this.context = request.getContext();
      this.tuples = new CombinedTupleReader(reader, request.getContextualTuplesList());
      this.types = types;
      this.filter = new TupleFilter(types);
    }

    /** Expands a relation of an object, and gives true if a path of the expansion went around a cycle. */
    Future<Boolean> expand(Step step, Consumer<Found> found) {
      if (step.depth() >= options.resolveNodeLimit()) {
        return Future.failedFuture(new OpenFgaException(ErrorCode.authorization_model_resolution_too_complex,
            "Authorization Model resolution required too many rewrite rules to be resolved. Check your"
                + " authorization model for infinite recursion or too much nesting"));
      }
      String key = step.object() + "#" + step.relation();
      if (step.visited().contains(key)) {
        return Future.succeededFuture(true);
      }
      Set<String> visited = new HashSet<>(step.visited());
      visited.add(key);
      Step entered = new Step(step.object(), step.relation(), visited, step.depth() + 1);
      TupleObject object = TupleObject.parse(step.object());
      for (UserTypeFilter userFilter : filters) {
        if (object.type().equals(userFilter.getType()) && step.relation().equals(userFilter.getRelation())) {
          found.accept(new Found(key));
        }
      }
      return types.relation(object.type(), step.relation())
          .map(relation -> rewrite(entered, relation.getRewrite(), found))
          .orElse(Future.succeededFuture(false));
    }

    private Future<Boolean> rewrite(Step step, Userset rewrite, Consumer<Found> found) {
      return switch (rewrite.getUsersetCase()) {
        case THIS -> direct(step, found);
        case COMPUTED_USERSET -> expand(step.to(step.object(), rewrite.getComputedUserset().getRelation()), found);
        case TUPLE_TO_USERSET -> tupleToUserset(step, rewrite.getTupleToUserset(), found);
        case UNION -> union(step, rewrite.getUnion().getChildList(), found);
        case INTERSECTION -> intersection(step, rewrite.getIntersection().getChildList(), found);
        case DIFFERENCE -> exclusion(step, rewrite.getDifference().getBase(), rewrite.getDifference().getSubtract(),
            found);
        default -> Future.failedFuture(new IllegalStateException("unexpected userset rewrite "
            + rewrite.getUsersetCase()));
      };
    }

    private Future<Boolean> direct(Step step, Consumer<Found> found) {
      return tuples.read(store, new TupleReader.ReadFilter(step.object(), step.relation(), "")).compose(read -> {
        List<Future<Boolean>> dispatches = new ArrayList<>();
        for (RelationshipTuple tuple : filter.valid(read)) {
          if (!met(tuple, dispatches)) {
            continue;
          }
          if (tuple.user() instanceof UsersetUser userset) {
            dispatches.add(expand(step.to(userset.object().toString(), userset.relation()), found));
            continue;
          }
          filters.stream().filter(userFilter -> userFilter.getType().equals(tuple.user().type()))
              .forEach(userFilter -> found.accept(new Found(tuple.user().toString())));
        }
        return anyCycle(dispatches);
      });
    }

    private Future<Boolean> tupleToUserset(Step step, TupleToUserset rewrite, Consumer<Found> found) {
      String computed = rewrite.getComputedUserset().getRelation();
      return tuples.read(store, new TupleReader.ReadFilter(step.object(), rewrite.getTupleset().getRelation(), ""))
          .compose(read -> {
            List<Future<Boolean>> dispatches = new ArrayList<>();
            for (RelationshipTuple tuple : filter.valid(read)) {
              if (met(tuple, dispatches) && tuple.user() instanceof ObjectUser parent) {
                dispatches.add(expand(step.to(parent.object().toString(), computed), found));
              }
            }
            return anyCycle(dispatches).map(false);
          });
    }

    /** Tells if a tuple meets its condition. A failure to evaluate it joins the dispatches as a failed one. */
    private boolean met(RelationshipTuple tuple, List<Future<Boolean>> dispatches) {
      try {
        return filter.conditionMet(tuple, context);
      } catch (OpenFgaException failed) {
        dispatches.add(Future.failedFuture(failed));
        return false;
      }
    }

    private Future<Boolean> union(Step step, List<Userset> children, Consumer<Found> found) {
      return collect(step, children).map(results -> {
        Map<String, Integer> excludedCounts = new LinkedHashMap<>();
        Set<String> users = new LinkedHashSet<>();
        for (List<Found> result : results) {
          for (Found user : result) {
            user.excluded().forEach(excluded -> excludedCounts.merge(excluded, 1, Integer::sum));
            if (user.related()) {
              users.add(user.user());
            }
          }
        }
        Set<String> excluded = new LinkedHashSet<>();
        excludedCounts.forEach((user, count) -> {
          if (count == children.size()) {
            excluded.add(user);
          }
        });
        users.forEach(user -> found.accept(new Found(user, true, excluded)));
        return false;
      });
    }

    private Future<Boolean> intersection(Step step, List<Userset> children, Consumer<Found> found) {
      String wildcard = new WildcardUser(filters.getFirst().getType()).toString();
      return collect(step, children).map(results -> {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> excluded = new LinkedHashSet<>();
        int wildcards = 0;
        for (List<Found> result : results) {
          Map<String, Integer> users = new LinkedHashMap<>();
          for (Found user : result) {
            excluded.addAll(user.excluded());
            if (user.related()) {
              users.merge(user.user(), 1, Integer::sum);
            }
          }
          boolean hasWildcard = users.containsKey(wildcard);
          if (hasWildcard) {
            wildcards++;
          }
          for (String user : users.keySet()) {
            counts.merge(user, hasWildcard ? 0 : 1, Integer::sum);
          }
        }
        for (Map.Entry<String, Integer> user : counts.entrySet()) {
          if (!excluded.contains(user.getKey()) && user.getValue() + wildcards == children.size()) {
            found.accept(new Found(user.getKey(), true, excluded));
          }
        }
        return false;
      });
    }

    private Future<Boolean> exclusion(Step step, Userset base, Userset subtract, Consumer<Found> found) {
      List<Found> baseUsers = new ArrayList<>();
      List<Found> subtractUsers = new ArrayList<>();
      Future<Boolean> baseDone = rewrite(step, base, user -> add(baseUsers, user));
      Future<Boolean> subtractDone = rewrite(step, subtract, user -> add(subtractUsers, user));
      return Future.join(baseDone, subtractDone).transform(done -> {
        if (baseDone.failed()) {
          return Future.failedFuture(baseDone.cause());
        }
        if (subtractDone.failed()) {
          return Future.failedFuture(subtractDone.cause());
        }
        if (subtractDone.result()) {
          return Future.succeededFuture(false);
        }
        exclude(byUser(baseUsers), byUser(subtractUsers), found);
        return Future.succeededFuture(false);
      });
    }

    /** Gives the users of a base that a subtracted set does not exclude. */
    private void exclude(Map<String, Found> base, Map<String, Found> subtract, Consumer<Found> found) {
      String wildcard = new WildcardUser(filters.getFirst().getType()).toString();
      boolean baseWildcard = base.containsKey(wildcard);
      boolean subtractWildcard = subtract.containsKey(wildcard);
      for (Map.Entry<String, Found> entry : base.entrySet()) {
        String user = entry.getKey();
        Found subtracted = subtract.get(user);
        if (baseWildcard) {
          if (subtracted == null && !subtractWildcard) {
            found.accept(new Found(user, entry.getValue().related(), entry.getValue().excluded()));
          }
          for (Map.Entry<String, Found> other : subtract.entrySet()) {
            if (TupleUser.parse(other.getKey()) instanceof WildcardUser) {
              if (subtracted == null) {
                found.accept(new Found(user, false, Set.of()));
              }
              continue;
            }
            found.accept(other.getValue().related() ? new Found(other.getKey(), false, Set.of(other.getKey()))
                : new Found(other.getKey(), true, Set.of()));
          }
        } else if (subtractWildcard || subtracted != null) {
          found.accept(new Found(user, subtracted != null && !subtracted.related(), Set.of()));
        } else {
          found.accept(new Found(user, entry.getValue().related(), Set.of()));
        }
      }
    }

    private Future<List<List<Found>>> collect(Step step, List<Userset> children) {
      List<List<Found>> results = new ArrayList<>();
      List<Future<Boolean>> expansions = new ArrayList<>();
      for (Userset child : children) {
        List<Found> result = new ArrayList<>();
        results.add(result);
        expansions.add(rewrite(step, child, user -> add(result, user)));
      }
      return Future.join(expansions).transform(done -> {
        for (Future<Boolean> expansion : expansions) {
          if (expansion.failed()) {
            return Future.failedFuture(expansion.cause());
          }
        }
        return Future.succeededFuture(results);
      });
    }
  }

  /**
   * Lists the users, up to the most results of the options.
   *
   * @throws OpenFgaException if the request does not fit the model
   */
  public Future<List<User>> list(ListUsersRequest request, TypeSystem types) {
    validate(request, types);
    String object = request.getObject().getType() + ":" + request.getObject().getId();
    UserTypeFilter first = request.getUserFilters(0);
    if (!(request.getObject().getType().equals(first.getType()) && request.getRelation().equals(first.getRelation()))
        && new RelationshipGraph(types).prunedEdges(types.directReference(request.getObject().getType(),
            request.getRelation()), types.directReference(first.getType(), first.getRelation())).isEmpty()) {
      return Future.succeededFuture(List.of());
    }
    Map<String, Found> users = new LinkedHashMap<>();
    Promise<List<User>> answer = Promise.promise();
    int max = options.listUsersMaxResults();
    long deadline = vertx.setTimer(options.listUsersDeadline().toMillis(), expired -> answer.tryComplete(
        users(users)));
    new Expansion(request, types).expand(new Step(object, request.getRelation(), Set.of(), 0), user -> {
      synchronized (users) {
        if (users.size() < max || users.containsKey(user.user())) {
          users.put(user.user(), user);
        }
        if (users.size() >= max) {
          answer.tryComplete(users(users));
        }
      }
    }).onComplete(done -> {
      vertx.cancelTimer(deadline);
      if (done.failed()) {
        answer.tryFail(done.cause());
      } else {
        answer.tryComplete(users(users));
      }
    });
    return answer.future();
  }

  private void validate(ListUsersRequest request, TypeSystem types) {
    WriteValidator validator = new WriteValidator(types);
    request.getContextualTuplesList().forEach(tuple -> validator.validate(tuple));
    for (UserTypeFilter userFilter : request.getUserFiltersList()) {
      if (types.typeDefinition(userFilter.getType()).isEmpty()) {
        throw new OpenFgaException(ErrorCode.type_not_found, "type '" + userFilter.getType() + "' not found");
      }
      if (!userFilter.getRelation().isEmpty() && types.relation(userFilter.getType(), userFilter.getRelation())
          .isEmpty()) {
        throw new OpenFgaException(ErrorCode.relation_not_found, "relation '" + userFilter.getType() + "#"
            + userFilter.getRelation() + "' not found");
      }
    }
    String type = request.getObject().getType();
    if (types.typeDefinition(type).isEmpty()) {
      throw new OpenFgaException(ErrorCode.type_not_found, "type '" + type + "' not found");
    }
    if (types.relation(type, request.getRelation()).isEmpty()) {
      throw new OpenFgaException(ErrorCode.relation_not_found, "relation '" + type + "#" + request.getRelation()
          + "' not found");
    }
  }

  private List<User> users(Map<String, Found> found) {
    synchronized (found) {
      return found.values().stream().filter(Found::related).map(user -> user(user.user())).toList();
    }
  }

  private User user(String user) {
    return switch (TupleUser.parse(user)) {
      case WildcardUser wildcard -> User.newBuilder().setWildcard(TypedWildcard.newBuilder().setType(wildcard.type()))
          .build();
      case UsersetUser userset -> User.newBuilder().setUserset(dev.horizon.spectrum.openfga.v1.UsersetUser.newBuilder()
          .setType(userset.type()).setId(userset.object().id()).setRelation(userset.relation())).build();
      case ObjectUser object -> User.newBuilder().setObject(Object.newBuilder().setType(object.type())
          .setId(object.object().id())).build();
    };
  }

  private void add(List<Found> users, Found user) {
    synchronized (users) {
      users.add(user);
    }
  }

  private Map<String, Found> byUser(List<Found> users) {
    Map<String, Found> byUser = new LinkedHashMap<>();
    synchronized (users) {
      users.forEach(user -> byUser.put(user.user(), user));
    }
    return byUser;
  }

  private Future<Boolean> anyCycle(List<Future<Boolean>> dispatches) {
    return Future.join(dispatches).transform(done -> {
      boolean cycle = false;
      for (Future<Boolean> dispatch : dispatches) {
        if (dispatch.failed()) {
          return Future.failedFuture(dispatch.cause());
        }
        cycle = cycle || dispatch.result();
      }
      return Future.succeededFuture(cycle);
    });
  }
}
