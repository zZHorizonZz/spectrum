package dev.horizon.spectrum;

import static dev.horizon.spectrum.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.spectrum.openfga.v1.ExpandRequest;
import dev.horizon.spectrum.openfga.v1.ExpandRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import dev.horizon.spectrum.openfga.v1.UsersetTree;
import io.vertx.grpc.common.GrpcStatus;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SpectrumTest
class ExpandTest {

  private Spectrum spectrum;
  private OpenFGAServiceGrpcClient fga;
  private TestStore store;

  @BeforeEach
  void open(Spectrum spectrum) throws TimeoutException {
    this.spectrum = spectrum;
    this.fga = spectrum.client();
    this.store = spectrum.documents();
  }

  private ExpandRequest request(String object, String relation) {
    return ExpandRequest.newBuilder().setStoreId(store.id()).setTupleKey(ExpandRequestTupleKey.newBuilder()
        .setObject(object).setRelation(relation)).build();
  }

  private UsersetTree.Node expand(String object, String relation) throws TimeoutException {
    return fga.expand(request(object, relation)).await(TIMEOUT).getTree().getRoot();
  }

  @Test
  void expandsAUnionOneLevel() throws TimeoutException {
    UsersetTree.Node root = expand("doc:plan", "viewer");
    assertThat(root.getName()).isEqualTo("doc:plan#viewer");
    assertThat(root.getUnion().getNodesList()).extracting(UsersetTree.Node::getLeaf).containsExactly(
        UsersetTree.Leaf.newBuilder().setUsers(UsersetTree.Users.getDefaultInstance()).build(),
        UsersetTree.Leaf.newBuilder().setComputed(UsersetTree.Computed.newBuilder().setUserset("doc:plan#editor"))
            .build(),
        UsersetTree.Leaf.newBuilder().setTupleToUserset(UsersetTree.TupleToUserset.newBuilder()
            .setTupleset("doc:plan#parent").addComputed(UsersetTree.Computed.newBuilder()
                .setUserset("folder:sub#viewer")))
            .build());
  }

  @Test
  void expandsTheUsersOfADirectRelation() throws TimeoutException {
    assertThat(expand("group:eng", "member").getLeaf().getUsers().getUsersList())
        .containsExactly("group:design#member", "user:anne");
  }

  @Test
  void expandsAnExclusion() throws TimeoutException {
    UsersetTree.Difference difference = expand("doc:plan", "reader").getDifference();
    assertThat(difference.getBase().getLeaf().getComputed().getUserset()).isEqualTo("doc:plan#viewer");
    assertThat(difference.getSubtract().getLeaf().getComputed().getUserset()).isEqualTo("doc:plan#blocked");
  }

  @Test
  void refusesARelationThatTheModelDoesNotHave() {
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.expand(request("doc:plan", "viewers")));
  }
}
