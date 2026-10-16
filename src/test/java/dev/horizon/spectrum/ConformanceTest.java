package dev.horizon.spectrum;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.TextFormat;
import dev.horizon.spectrum.conformance.Assertion;
import dev.horizon.spectrum.conformance.CheckAssertion;
import dev.horizon.spectrum.conformance.ConformanceCase;
import dev.horizon.spectrum.conformance.ConformanceCases;
import dev.horizon.spectrum.conformance.ListObjectsAssertion;
import dev.horizon.spectrum.conformance.ListUsersAssertion;
import dev.horizon.spectrum.conformance.Stage;
import dev.horizon.spectrum.openfga.v1.CheckRequest;
import dev.horizon.spectrum.openfga.v1.CheckRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.ContextualTupleKeys;
import dev.horizon.spectrum.openfga.v1.ListObjectsRequest;
import dev.horizon.spectrum.openfga.v1.ListUsersRequest;
import dev.horizon.spectrum.openfga.v1.Object;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import dev.horizon.spectrum.openfga.v1.StreamedListObjectsRequest;
import dev.horizon.spectrum.openfga.v1.StreamedListObjectsResponse;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.User;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

@SpectrumTest
class ConformanceTest {

  private static final List<ConformanceCase> CASES = new ConformanceCases().read("consolidated_1_1_tests.yaml",
      "abac_tests.yaml");
  private static final int WRITE_CHUNK_SIZE = 40;
  private static final int MAX_CONTEXTUAL_TUPLES = 100;
  private static final int FIRST_VALIDATION_ERROR = 2000;
  private static final int FIRST_THROTTLING_ERROR = 3000;

  private Spectrum spectrum;
  private OpenFGAServiceGrpcClient fga;

  @BeforeEach
  void open(Spectrum spectrum) {
    this.spectrum = spectrum;
    this.fga = spectrum.client();
  }

  private record Ready(TestStore store, List<TupleKey> tuples, boolean contextual) {

    List<TupleKey> contextualTuples(Assertion assertion) {
      List<TupleKey> contextualTuples = new ArrayList<>(assertion.contextualTuples());
      if (contextual) {
        contextualTuples.addAll(tuples);
      }
      return contextualTuples;
    }
  }

  private final class Run {

    private final List<Stage> stages;
    private final boolean contextual;
    private final List<Ready> ready = new ArrayList<>();
    private TestStore store;

    Run(List<Stage> stages, boolean contextual) {
      this.stages = stages;
      this.contextual = contextual;
    }

    synchronized Ready stage(int index) {
      Assumptions.assumeFalse(stages.stream().limit(index + 1L).anyMatch(Stage::usesInlineConditions),
          "inline $expression conditions are not supported");
      if (store == null) {
        store = spectrum.store();
      }
      while (ready.size() <= index) {
        Stage stage = stages.get(ready.size());
        store.writeModel(stage.model());
        if (!contextual) {
          for (int start = 0; start < stage.tuples().size(); start += WRITE_CHUNK_SIZE) {
            store.write(stage.tuples().subList(start, Math.min(stage.tuples().size(), start + WRITE_CHUNK_SIZE)));
          }
        }
        ready.add(new Ready(store, stage.tuples(), contextual));
      }
      return ready.get(index);
    }
  }

  @TestFactory
  Stream<DynamicNode> check() {
    return suite(Stage::checkAssertions, this::check);
  }

  @TestFactory
  Stream<DynamicNode> listObjects() {
    return suite(Stage::listObjectsAssertions, this::listObjects);
  }

  @TestFactory
  Stream<DynamicNode> listUsers() {
    return suite(Stage::listUsersAssertions, this::listUsers);
  }

  private <A extends Assertion> Stream<DynamicNode> suite(Function<Stage, List<A>> assertions,
      BiConsumer<Ready, A> run) {
    return CASES.stream().flatMap(test -> Stream.of(false, true).flatMap(contextual -> {
      if (contextual && test.stages().size() > 1) {
        return Stream.empty();
      }
      Run cases = new Run(test.stages(), contextual);
      List<DynamicNode> stages = IntStream.range(0, test.stages().size()).filter(index -> {
        Stage stage = test.stages().get(index);
        return !assertions.apply(stage).isEmpty() && !(contextual && stage.tuples().size() > MAX_CONTEXTUAL_TUPLES);
      }).mapToObj(index -> stage(cases, index, assertions.apply(test.stages().get(index)), run)).toList();
      String name = test.name() + (contextual ? "_ctxTuples" : "");
      return stages.isEmpty() ? Stream.empty() : Stream.of(DynamicContainer.dynamicContainer(name, stages));
    }));
  }

  private <A extends Assertion> DynamicNode stage(Run cases, int index, List<A> assertions,
      BiConsumer<Ready, A> run) {
    return DynamicContainer.dynamicContainer("stage_" + index, IntStream.range(0, assertions.size())
        .mapToObj(number -> DynamicTest.dynamicTest("assertion_" + number, () -> run.accept(cases.stage(index),
            assertions.get(number)))));
  }

  private void check(Ready ready, CheckAssertion assertion) {
    CheckRequest request = CheckRequest.newBuilder().setStoreId(ready.store().id())
        .setAuthorizationModelId(ready.store().model()).setTupleKey(assertion.tuple())
        .setContextualTuples(ContextualTupleKeys.newBuilder().addAllTupleKeys(ready.contextualTuples(assertion)))
        .setContext(assertion.context()).build();
    if (failsWith(assertion, () -> fga.check(request))) {
      return;
    }
    assertThat(fga.check(request).await().getAllowed()).as(assertion.toString()).isEqualTo(assertion.expectation());
  }

  private void listObjects(Ready ready, ListObjectsAssertion assertion) {
    List<TupleKey> contextual = ready.contextualTuples(assertion);
    ListObjectsRequest request = ListObjectsRequest.newBuilder().setStoreId(ready.store().id())
        .setAuthorizationModelId(ready.store().model()).setType(assertion.request().type())
        .setRelation(assertion.request().relation()).setUser(assertion.request().user())
        .setContextualTuples(ContextualTupleKeys.newBuilder().addAllTupleKeys(contextual))
        .setContext(assertion.context()).build();
    StreamedListObjectsRequest streamed = StreamedListObjectsRequest.newBuilder().setStoreId(request.getStoreId())
        .setAuthorizationModelId(request.getAuthorizationModelId()).setType(request.getType())
        .setRelation(request.getRelation()).setUser(request.getUser())
        .setContextualTuples(request.getContextualTuples()).setContext(request.getContext()).build();
    Supplier<Future<List<String>>> stream = () -> Spectrum.deadline(fga.streamedListObjects(streamed)
        .compose(objects -> objects.collect(Collectors.mapping(StreamedListObjectsResponse::getObject,
            Collectors.toList()))),
        "streamedListObjects " + TextFormat.shortDebugString(streamed));
    if (failsWith(assertion, () -> fga.listObjects(request))) {
      return;
    }
    List<String> objects = fga.listObjects(request).await().getObjectsList();
    assertThat(objects).as(assertion.toString()).containsExactlyInAnyOrderElementsOf(assertion.expectation());
    assertThat(stream.get().await()).as(assertion.toString())
        .containsExactlyInAnyOrderElementsOf(assertion.expectation());
    for (String object : objects) {
      assertAllowed(ready, assertion, contextual, object, request.getRelation(), request.getUser());
    }
  }

  private void listUsers(Ready ready, ListUsersAssertion assertion) {
    String object = assertion.request().object();
    int colon = object.indexOf(':');
    List<TupleKey> contextual = ready.contextualTuples(assertion);
    ListUsersRequest.Builder request = ListUsersRequest.newBuilder().setStoreId(ready.store().id())
        .setAuthorizationModelId(ready.store().model()).setObject(Object.newBuilder()
            .setType(object.substring(0, colon)).setId(object.substring(colon + 1)))
        .setRelation(assertion.request().relation()).addAllContextualTuples(contextual)
        .setContext(assertion.context());
    for (String filter : assertion.request().filters()) {
      int hash = filter.indexOf('#');
      request.addUserFilters(hash < 0 ? UserTypeFilter.newBuilder().setType(filter)
          : UserTypeFilter.newBuilder().setType(filter.substring(0, hash)).setRelation(filter.substring(hash + 1)));
    }
    if (failsWith(assertion, () -> fga.listUsers(request.build()))) {
      return;
    }
    List<String> users = fga.listUsers(request.build()).await().getUsersList().stream().map(this::text).toList();
    assertThat(users).as(assertion.toString()).containsExactlyInAnyOrderElementsOf(assertion.expectation());
    for (String user : users) {
      assertAllowed(ready, assertion, contextual, object, request.getRelation(), user);
    }
  }

  private void assertAllowed(Ready ready, Assertion assertion, List<TupleKey> contextual, String object,
      String relation, String user) {
    assertThat(fga.check(CheckRequest.newBuilder().setStoreId(ready.store().id())
        .setAuthorizationModelId(ready.store().model()).setTupleKey(CheckRequestTupleKey.newBuilder()
            .setObject(object).setRelation(relation).setUser(user))
        .setContextualTuples(ContextualTupleKeys.newBuilder().addAllTupleKeys(contextual))
        .setContext(assertion.context()).build()).await().getAllowed()).as(object + "#" + relation + "@" + user)
        .isTrue();
  }

  private boolean failsWith(Assertion assertion, Supplier<Future<?>> call) {
    if (!assertion.fails()) {
      return false;
    }
    assertThat(assertion.errorCode()).as("an OpenFGA validation error code").isBetween(FIRST_VALIDATION_ERROR,
        FIRST_THROTTLING_ERROR - 1);
    try {
      spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, call);
    } catch (AssertionError passed) {
      throw new AssertionError(assertion + ": " + passed.getMessage().strip(), passed);
    }
    return true;
  }

  private String text(User user) {
    return switch (user.getUserCase()) {
      case OBJECT -> user.getObject().getType() + ":" + user.getObject().getId();
      case WILDCARD -> user.getWildcard().getType() + ":*";
      case USERSET -> user.getUserset().getType() + ":" + user.getUserset().getId() + "#"
          + user.getUserset().getRelation();
      default -> throw new IllegalArgumentException(user.toString());
    };
  }
}
