package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.model.TypeSystemResolver;
import dev.horizon.spectrum.openfga.v1.Difference;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.ExpandRequest;
import dev.horizon.spectrum.openfga.v1.ExpandResponse;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleToUserset;
import dev.horizon.spectrum.openfga.v1.Userset;
import dev.horizon.spectrum.openfga.v1.UsersetTree;
import dev.horizon.spectrum.storage.CombinedTupleReader;
import dev.horizon.spectrum.storage.TupleReader;
import dev.horizon.spectrum.tuple.InvalidTupleException;
import dev.horizon.spectrum.tuple.ModelRule;
import dev.horizon.spectrum.tuple.ObjectUser;
import dev.horizon.spectrum.tuple.RelationshipTuple;
import dev.horizon.spectrum.tuple.TupleFilter;
import dev.horizon.spectrum.tuple.TupleObject;
import dev.horizon.spectrum.tuple.UsersetUser;
import dev.horizon.spectrum.tuple.WildcardUser;
import dev.horizon.spectrum.tuple.WriteValidator;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class ExpandHandler extends UnaryHandler<ExpandRequest, ExpandResponse> {

  public static final ServiceMethod<ExpandRequest, ExpandResponse> SERVICE_METHOD = method(
      "Expand", ExpandRequest.newBuilder());

  private final TypeSystemResolver resolver;
  private final TupleReader reader;

  public ExpandHandler(TypeSystemResolver resolver, TupleReader reader) {
    this.resolver = resolver;
    this.reader = reader;
  }

  @Override
  Future<ExpandResponse> execute(ExpandRequest request) {
    return resolver.resolve(request.getStoreId(), request.getAuthorizationModelId())
        .compose(types -> execute(request, types));
  }

  private Future<ExpandResponse> execute(ExpandRequest request, TypeSystem types) {
    String object = request.getTupleKey().getObject();
    String relation = request.getTupleKey().getRelation();
    if (object.isEmpty() || relation.isEmpty()) {
      throw new OpenFgaException(ErrorCode.invalid_expand_input, "Invalid input. Make sure you provide an object and"
          + " a relation");
    }
    WriteValidator validator = new WriteValidator(types);
    List<TupleKey> contextual = request.getContextualTuples().getTupleKeysList();
    contextual.forEach(tuple -> validator.validate(tuple));
    TupleObject expanded;
    try {
      expanded = TupleObject.parse(object);
      ModelRule model = new ModelRule(types);
      model.checkObject(expanded);
      model.checkRelation(expanded, relation);
    } catch (InvalidTupleException invalid) {
      throw new OpenFgaException(ErrorCode.validation_error, invalid.getMessage());
    }
    Userset rewrite = types.relation(expanded.type(), relation).orElseThrow().getRewrite();
    Expansion expansion = new Expansion(request.getStoreId(), new CombinedTupleReader(reader, contextual),
        new TupleFilter(types));
    return expansion.node(object, relation, rewrite).map(root -> ExpandResponse.newBuilder()
        .setTree(UsersetTree.newBuilder().setRoot(root)).build());
  }

  private record Expansion(String store, TupleReader reader, TupleFilter filter) {

    Future<UsersetTree.Node> node(String object, String relation, Userset rewrite) {
      String name = object + "#" + relation;
      return switch (rewrite.getUsersetCase()) {
        case THIS, USERSET_NOT_SET -> users(object, relation, name);
        case COMPUTED_USERSET -> Future.succeededFuture(leaf(name, UsersetTree.Leaf.newBuilder()
            .setComputed(UsersetTree.Computed.newBuilder()
                .setUserset(object + "#" + rewrite.getComputedUserset().getRelation()))
            .build()));
        case TUPLE_TO_USERSET -> tupleToUserset(object, name, rewrite.getTupleToUserset());
        case UNION -> nodes(object, relation, rewrite.getUnion().getChildList()).map(nodes -> UsersetTree.Node
            .newBuilder().setName(name).setUnion(UsersetTree.Nodes.newBuilder().addAllNodes(nodes)).build());
        case INTERSECTION -> nodes(object, relation, rewrite.getIntersection().getChildList())
            .map(nodes -> UsersetTree.Node.newBuilder().setName(name).setIntersection(UsersetTree.Nodes.newBuilder()
                .addAllNodes(nodes)).build());
        case DIFFERENCE -> {
          Difference difference = rewrite.getDifference();
          yield nodes(object, relation, List.of(difference.getBase(), difference.getSubtract()))
              .map(nodes -> UsersetTree.Node.newBuilder().setName(name).setDifference(UsersetTree.Difference.newBuilder()
                  .setBase(nodes.get(0)).setSubtract(nodes.get(1))).build());
        }
      };
    }

    private Future<List<UsersetTree.Node>> nodes(String object, String relation, List<Userset> rewrites) {
      List<Future<UsersetTree.Node>> nodes = new ArrayList<>();
      rewrites.forEach(child -> nodes.add(node(object, relation, child)));
      return Future.all(nodes).map(done -> nodes.stream().map(Future::result).toList());
    }

    private Future<UsersetTree.Node> users(String object, String relation, String name) {
      return reader.read(store, new TupleReader.ReadFilter(object, relation, "")).map(tuples -> {
        Set<String> users = new TreeSet<>();
        filter.valid(tuples).forEach(tuple -> users.add(tuple.user().toString()));
        return leaf(name, UsersetTree.Leaf.newBuilder().setUsers(UsersetTree.Users.newBuilder().addAllUsers(users))
            .build());
      });
    }

    private Future<UsersetTree.Node> tupleToUserset(String object, String name, TupleToUserset rewrite) {
      String tupleset = rewrite.getTupleset().getRelation();
      return reader.read(store, new TupleReader.ReadFilter(object, tupleset, "")).map(tuples -> {
        Set<String> computed = new LinkedHashSet<>();
        String relation = rewrite.getComputedUserset().getRelation();
        for (RelationshipTuple tuple : filter.valid(tuples)) {
          computed.add(switch (tuple.user()) {
            case UsersetUser user -> user.toString();
            case ObjectUser user -> user + "#" + relation;
            case WildcardUser user -> user + "#" + relation;
          });
        }
        UsersetTree.TupleToUserset.Builder leaf = UsersetTree.TupleToUserset.newBuilder()
            .setTupleset(object + "#" + tupleset);
        computed.forEach(userset -> leaf.addComputed(UsersetTree.Computed.newBuilder().setUserset(userset)));
        return leaf(name, UsersetTree.Leaf.newBuilder().setTupleToUserset(leaf).build());
      });
    }

    private UsersetTree.Node leaf(String name, UsersetTree.Leaf leaf) {
      return UsersetTree.Node.newBuilder().setName(name).setLeaf(leaf).build();
    }
  }
}
