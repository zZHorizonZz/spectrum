package dev.horizon.spectrum.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.google.protobuf.Int32Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import dev.horizon.basepoint.Dialect;
import dev.horizon.spectrum.openfga.OpenFgaService;
import dev.horizon.spectrum.openfga.v1.Assertion;
import dev.horizon.spectrum.openfga.v1.AssertionTupleKey;
import dev.horizon.spectrum.openfga.v1.BatchCheckItem;
import dev.horizon.spectrum.openfga.v1.BatchCheckRequest;
import dev.horizon.spectrum.openfga.v1.BatchCheckResponse;
import dev.horizon.spectrum.openfga.v1.CheckRequest;
import dev.horizon.spectrum.openfga.v1.CheckRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.ContextualTupleKeys;
import dev.horizon.spectrum.openfga.v1.CreateStoreRequest;
import dev.horizon.spectrum.openfga.v1.CreateStoreResponse;
import dev.horizon.spectrum.openfga.v1.DeleteStoreRequest;
import dev.horizon.spectrum.openfga.v1.ExpandRequest;
import dev.horizon.spectrum.openfga.v1.GetStoreRequest;
import dev.horizon.spectrum.openfga.v1.ListObjectsRequest;
import dev.horizon.spectrum.openfga.v1.ListStoresRequest;
import dev.horizon.spectrum.openfga.v1.ListStoresResponse;
import dev.horizon.spectrum.openfga.v1.ListUsersRequest;
import dev.horizon.spectrum.openfga.v1.ListUsersResponse;
import dev.horizon.spectrum.openfga.v1.Object;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcService;
import dev.horizon.spectrum.openfga.v1.ReadAssertionsRequest;
import dev.horizon.spectrum.openfga.v1.ReadAssertionsResponse;
import dev.horizon.spectrum.openfga.v1.ReadAuthorizationModelRequest;
import dev.horizon.spectrum.openfga.v1.ReadAuthorizationModelsRequest;
import dev.horizon.spectrum.openfga.v1.ReadChangesRequest;
import dev.horizon.spectrum.openfga.v1.ReadChangesResponse;
import dev.horizon.spectrum.openfga.v1.ReadRequest;
import dev.horizon.spectrum.openfga.v1.ReadRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.ReadResponse;
import dev.horizon.spectrum.openfga.v1.RelationshipCondition;
import dev.horizon.spectrum.openfga.v1.StreamedListObjectsRequest;
import dev.horizon.spectrum.openfga.v1.StreamedListObjectsResponse;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import dev.horizon.spectrum.openfga.v1.TupleOperation;
import dev.horizon.spectrum.openfga.v1.UpdateStoreRequest;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import dev.horizon.spectrum.openfga.v1.WriteAssertionsRequest;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import dev.horizon.spectrum.openfga.v1.WriteRequest;
import dev.horizon.spectrum.openfga.v1.WriteRequestDeletes;
import dev.horizon.spectrum.openfga.v1.WriteRequestWrites;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.sqlclient.Pool;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
public abstract class OpenFgaContractTest {

  private static final String MODEL = """
      {
        "schema_version": "1.1",
        "type_definitions": [
          { "type": "user" },
          {
            "type": "group",
            "relations": { "member": { "this": {} } },
            "metadata": { "relations": { "member": { "directly_related_user_types": [
              { "type": "user" }, { "type": "group", "relation": "member" }
            ] } } }
          },
          {
            "type": "folder",
            "relations": { "viewer": { "this": {} } },
            "metadata": { "relations": { "viewer": { "directly_related_user_types": [
              { "type": "user" }, { "type": "group", "relation": "member" }
            ] } } }
          },
          {
            "type": "doc",
            "relations": {
              "parent": { "this": {} },
              "editor": { "this": {} },
              "blocked": { "this": {} },
              "viewer": { "union": { "child": [
                { "this": {} },
                { "computedUserset": { "relation": "editor" } },
                { "tupleToUserset": { "tupleset": { "relation": "parent" }, "computedUserset": { "relation": "viewer" } } }
              ] } },
              "reader": { "difference": {
                "base": { "computedUserset": { "relation": "viewer" } },
                "subtract": { "computedUserset": { "relation": "blocked" } }
              } }
            },
            "metadata": { "relations": {
              "parent": { "directly_related_user_types": [{ "type": "folder" }] },
              "editor": { "directly_related_user_types": [{ "type": "user" }] },
              "blocked": { "directly_related_user_types": [{ "type": "user" }] },
              "viewer": { "directly_related_user_types": [{ "type": "user" }, { "type": "user", "wildcard": {} }] },
              "reader": { "directly_related_user_types": [] }
            } }
          }
        ]
      }
      """;

  private static final List<TupleKey> TUPLES = List.of(
      tuple("user:ana", "member", "group:eng"),
      tuple("group:eng#member", "viewer", "folder:root"),
      tuple("folder:root", "parent", "doc:plan"),
      tuple("user:dan", "editor", "doc:plan"),
      tuple("user:dan", "blocked", "doc:plan"),
      tuple("user:*", "viewer", "doc:memo"));

  protected Pool pool;
  protected OpenFGAServiceGrpcClient fga;
  private EventBusGrpcServer server;
  private EventBusGrpcClient client;

  @BeforeEach
  void open(Vertx vertx, Pool pool, Dialect dialect) {
    this.pool = pool;
    server = EventBusGrpcServer.server(vertx).await();
    server.addService(OpenFGAServiceGrpcService.of(new OpenFgaService(pool, dialect)));
    client = EventBusGrpcClient.client(vertx).await();
    fga = OpenFGAServiceGrpcClient.create(client);
  }

  @AfterEach
  void close() {
    client.close().await();
    server.close().await();
  }

  private static TupleKey tuple(String user, String relation, String object) {
    return TupleKey.newBuilder().setUser(user).setRelation(relation).setObject(object).build();
  }

  private static WriteAuthorizationModelRequest model(String store, String json) {
    WriteAuthorizationModelRequest.Builder request = WriteAuthorizationModelRequest.newBuilder();
    try {
      JsonFormat.parser().merge(json, request);
    } catch (InvalidProtocolBufferException invalid) {
      throw new IllegalArgumentException(invalid);
    }
    return request.setStoreId(store).build();
  }

  private static void assertStatus(GrpcStatus status, Supplier<Future<?>> call) {
    assertThatExceptionOfType(InvalidStatusException.class).isThrownBy(() -> call.get().await())
        .satisfies(failed -> assertThat(failed.actualStatus()).isEqualTo(status));
  }

  private String store() {
    return fga.createStore(CreateStoreRequest.newBuilder().setName("documents").build()).await().getId();
  }

  @Nested
  @DisplayName("stores")
  class Stores {

    @Test
    @DisplayName("creates, gets, lists and deletes stores")
    void lifecycle() {
      CreateStoreResponse created = fga.createStore(CreateStoreRequest.newBuilder().setName("documents").build())
          .await();
      String other = store();
      ListStoresResponse first = fga.listStores(ListStoresRequest.newBuilder().setPageSize(Int32Value.of(1)).build())
          .await();
      assertAll(
          () -> assertThat(created.getId()).matches("[0-9A-HJKMNP-TV-Z]{26}"),
          () -> assertThat(created.getUpdatedAt()).isEqualTo(created.getCreatedAt()),
          () -> assertThat(fga.getStore(GetStoreRequest.newBuilder().setStoreId(created.getId()).build()).await()
              .getName()).isEqualTo("documents"),
          () -> assertThat(first.getStoresList()).extracting(store -> store.getId()).containsExactly(created.getId()),
          () -> assertThat(fga.listStores(ListStoresRequest.newBuilder().setPageSize(Int32Value.of(1))
              .setContinuationToken(first.getContinuationToken()).build()).await().getStoresList())
              .extracting(store -> store.getId()).containsExactly(other));
      fga.deleteStore(DeleteStoreRequest.newBuilder().setStoreId(created.getId()).build()).await();
      assertAll(
          () -> assertStatus(GrpcStatus.NOT_FOUND, () -> fga.getStore(GetStoreRequest.newBuilder()
              .setStoreId(created.getId()).build())),
          () -> assertStatus(GrpcStatus.NOT_FOUND, () -> fga.deleteStore(DeleteStoreRequest.newBuilder()
              .setStoreId(created.getId()).build())));
    }

    @Test
    @DisplayName("does not update a store, as OpenFGA does not")
    void update() {
      assertStatus(GrpcStatus.UNIMPLEMENTED, () -> fga.updateStore(UpdateStoreRequest.newBuilder()
          .setStoreId(store()).setName("renamed").build()));
    }
  }

  @Nested
  @DisplayName("authorization models")
  class Models {

    private String store;

    @BeforeEach
    void create() {
      store = store();
    }

    @Test
    @DisplayName("reads a model as it was written, with its id")
    void roundTrip() {
      WriteAuthorizationModelRequest written = model(store, MODEL);
      String id = fga.writeAuthorizationModel(written).await().getAuthorizationModelId();
      var read = fga.readAuthorizationModel(ReadAuthorizationModelRequest.newBuilder().setStoreId(store).setId(id)
          .build()).await().getAuthorizationModel();
      assertAll(
          () -> assertThat(read.getId()).isEqualTo(id),
          () -> assertThat(read.getSchemaVersion()).isEqualTo("1.1"),
          () -> assertThat(read.getTypeDefinitionsList()).isEqualTo(written.getTypeDefinitionsList()));
    }

    @Test
    @DisplayName("lists the models, the latest first")
    void lists() {
      List<String> ids = IntStream.range(0, 3).mapToObj(i -> fga.writeAuthorizationModel(model(store, MODEL)).await()
          .getAuthorizationModelId()).toList();
      assertThat(fga.readAuthorizationModels(ReadAuthorizationModelsRequest.newBuilder().setStoreId(store).build())
          .await().getAuthorizationModelsList()).extracting(model -> model.getId())
          .containsExactlyElementsOf(ids.reversed());
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(delimiter = '|', textBlock = """
        INVALID_ARGUMENT | { "schema_version": "1.0", "type_definitions": [{ "type": "user" }] }
        INVALID_ARGUMENT | { "schema_version": "1.1", "type_definitions": [{ "type": "doc", "relations": { "viewer": { "computedUserset": { "relation": "owner" } } } }] }
        INVALID_ARGUMENT | { "schema_version": "1.1", "type_definitions": [{ "type": "Doc" }] }
        UNIMPLEMENTED    | { "schema_version": "1.1", "type_definitions": [{ "type": "user" }], "conditions": { "office": { "name": "office", "expression": "true" } } }
        """)
    void refuses(GrpcStatus status, String json) {
      assertStatus(status, () -> fga.writeAuthorizationModel(model(store, json)));
    }

    @Test
    @DisplayName("fails for a model or a store that does not exist")
    void missing() {
      assertAll(
          () -> assertStatus(GrpcStatus.NOT_FOUND, () -> fga.readAuthorizationModel(ReadAuthorizationModelRequest
              .newBuilder().setStoreId(store).setId("01JAZ6S3N6T3Y5W1Z8PKV9B4CD").build())),
          () -> assertStatus(GrpcStatus.NOT_FOUND, () -> fga.writeAuthorizationModel(model(
              "01JAZ6RE1H6ZQ4WKQ2M0R0S8XQ", MODEL))),
          () -> assertStatus(GrpcStatus.NOT_FOUND, () -> fga.check(CheckRequest.newBuilder().setStoreId(store)
              .setTupleKey(CheckRequestTupleKey.newBuilder().setUser("user:ana").setRelation("viewer")
                  .setObject("doc:plan"))
              .build())));
    }
  }

  @Nested
  @DisplayName("with the tuples of a team")
  class Team {

    private String store;
    private String model;

    @BeforeEach
    void write() {
      store = store();
      model = fga.writeAuthorizationModel(model(store, MODEL)).await().getAuthorizationModelId();
      fga.write(WriteRequest.newBuilder().setStoreId(store).setWrites(WriteRequestWrites.newBuilder()
          .addAllTupleKeys(TUPLES)).build()).await();
    }

    private CheckRequest.Builder check(String user, String relation, String object) {
      return CheckRequest.newBuilder().setStoreId(store).setTupleKey(CheckRequestTupleKey.newBuilder().setUser(user)
          .setRelation(relation).setObject(object));
    }

    @ParameterizedTest(name = "{0} {1} {2}: {3}")
    @CsvSource(delimiter = '|', textBlock = """
        user:ana | viewer | doc:plan | true
        user:dan | viewer | doc:plan | true
        user:dan | reader | doc:plan | false
        user:ana | reader | doc:plan | true
        user:zed | viewer | doc:plan | false
        user:zed | viewer | doc:memo | true
        user:zed | reader | doc:memo | true
        """)
    void checks(String user, String relation, String object, boolean expected) {
      assertAll(
          () -> assertThat(fga.check(check(user, relation, object).build()).await().getAllowed()).isEqualTo(expected),
          () -> assertThat(fga.check(check(user, relation, object).setAuthorizationModelId(model).build()).await()
              .getAllowed()).isEqualTo(expected));
    }

    @ParameterizedTest(name = "{0} {1} {2}: {3}")
    @CsvSource(delimiter = '|', textBlock = """
        user:ana | viewer | doc    | doc:memo doc:plan
        user:dan | reader | doc    | doc:memo
        user:ana | viewer | folder | folder:root
        user:zed | viewer | folder |
        """)
    void listsObjects(String user, String relation, String type,
        @ConvertWith(SpaceSeparated.class) List<String> expected) {
      assertThat(fga.listObjects(ListObjectsRequest.newBuilder().setStoreId(store).setUser(user)
          .setRelation(relation).setType(type).build()).await().getObjectsList()).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest(name = "{0}:{1} {2}: {3}")
    @CsvSource(delimiter = '|', textBlock = """
        doc    | plan | viewer | users { object { type: "user" id: "ana" } } users { object { type: "user" id: "dan" } }
        doc    | plan | reader | users { object { type: "user" id: "ana" } }
        doc    | memo | viewer | users { wildcard { type: "user" } }
        folder | root | viewer | users { object { type: "user" id: "ana" } }
        """)
    void listsUsers(String type, String id, String relation, @ConvertWith(ProtoText.class) ListUsersResponse expected) {
      assertThat(fga.listUsers(ListUsersRequest.newBuilder().setStoreId(store).setObject(Object.newBuilder()
          .setType(type).setId(id)).setRelation(relation).addUserFilters(UserTypeFilter.newBuilder().setType("user"))
          .build()).await()).isEqualTo(expected);
    }

    @Test
    @DisplayName("counts contextual tuples in a check and in lists, and writes none of them")
    void contextual() {
      TupleKey zed = tuple("user:zed", "member", "group:eng");
      assertAll(
          () -> assertThat(fga.check(check("user:zed", "viewer", "doc:plan").setContextualTuples(ContextualTupleKeys
              .newBuilder().addTupleKeys(zed)).build()).await().getAllowed()).isTrue(),
          () -> assertThat(fga.listObjects(ListObjectsRequest.newBuilder().setStoreId(store).setUser("user:zed")
              .setRelation("viewer").setType("doc").setContextualTuples(ContextualTupleKeys.newBuilder()
                  .addTupleKeys(zed))
              .build()).await().getObjectsList()).containsExactly("doc:memo", "doc:plan"),
          () -> assertThat(fga.listUsers(ListUsersRequest.newBuilder().setStoreId(store).setObject(Object.newBuilder()
              .setType("folder").setId("root")).setRelation("viewer").addUserFilters(UserTypeFilter.newBuilder()
                  .setType("user"))
              .addContextualTuples(zed).build()).await().getUsersCount()).isEqualTo(2),
          () -> assertThat(fga.check(check("user:zed", "viewer", "doc:plan").build()).await().getAllowed()).isFalse());
    }

    @Test
    @DisplayName("answers each check of a batch by its correlation id, with an error for a check that is not valid")
    void batchCheck() {
      BatchCheckResponse expected = new ProtoText().parse(BatchCheckResponse.class,
          """
              result { key: "a" value { allowed: true } }
              result { key: "b" value { allowed: false } }
              result { key: "c" value { allowed: true } }
              result { key: "d" value { error { input_error: validation_error message: "the type 'doc' has no relation 'viewr'" } } }
              """);
      assertThat(fga.batchCheck(BatchCheckRequest.newBuilder().setStoreId(store)
          .addChecks(item("a", "user:ana", "viewer", "doc:plan"))
          .addChecks(item("b", "user:zed", "viewer", "doc:plan"))
          .addChecks(item("c", "user:zed", "viewer", "doc:plan").setContextualTuples(ContextualTupleKeys.newBuilder()
              .addTupleKeys(tuple("user:zed", "member", "group:eng"))))
          .addChecks(item("d", "user:ana", "viewr", "doc:plan")).build()).await()).isEqualTo(expected);
    }

    private BatchCheckItem.Builder item(String id, String user, String relation, String object) {
      return BatchCheckItem.newBuilder().setCorrelationId(id).setTupleKey(CheckRequestTupleKey.newBuilder()
          .setUser(user).setRelation(relation).setObject(object));
    }

    @ParameterizedTest(name = "{0} {1} {2}, {3} per page")
    @CsvSource(delimiter = '|', textBlock = """
        ''       | ''     | ''       | 2   | doc:memo#viewer@user:* doc:plan#blocked@user:dan doc:plan#editor@user:dan doc:plan#parent@folder:root folder:root#viewer@group:eng#member group:eng#member@user:ana
        doc:     | ''     | ''       | 100 | doc:memo#viewer@user:* doc:plan#blocked@user:dan doc:plan#editor@user:dan doc:plan#parent@folder:root
        doc:plan | ''     | user:dan | 1   | doc:plan#blocked@user:dan doc:plan#editor@user:dan
        ''       | viewer | ''       | 1   | doc:memo#viewer@user:* folder:root#viewer@group:eng#member
        """)
    void reads(String object, String relation, String user, int size,
        @ConvertWith(SpaceSeparated.class) List<String> expected) {
      List<String> read = new ArrayList<>();
      String token = "";
      do {
        ReadResponse page = fga.read(ReadRequest.newBuilder().setStoreId(store).setTupleKey(ReadRequestTupleKey
            .newBuilder().setObject(object).setRelation(relation).setUser(user)).setPageSize(Int32Value.of(size))
            .setContinuationToken(token).build()).await();
        page.getTuplesList().forEach(tuple -> {
          assertThat(tuple.getTimestamp().getSeconds()).isPositive();
          read.add(tuple.getKey().getObject() + "#" + tuple.getKey().getRelation() + "@" + tuple.getKey().getUser());
        });
        token = page.getContinuationToken();
      } while (!token.isEmpty());
      assertThat(read).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("writes and deletes in one transaction, and fails for a tuple that exists or is missing")
    void writes() {
      WriteRequest.Builder both = WriteRequest.newBuilder().setStoreId(store)
          .setWrites(WriteRequestWrites.newBuilder().addTupleKeys(tuple("user:zed", "editor", "doc:plan"))
              .addTupleKeys(tuple("user:dan", "editor", "doc:plan")));
      assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.write(both.build()));
      assertThat(fga.check(check("user:zed", "editor", "doc:plan").build()).await().getAllowed()).isFalse();
      fga.write(both.setWrites(both.getWrites().toBuilder().setOnDuplicate("ignore")).build()).await();
      assertThat(fga.check(check("user:zed", "editor", "doc:plan").build()).await().getAllowed()).isTrue();

      WriteRequest.Builder delete = WriteRequest.newBuilder().setStoreId(store)
          .setDeletes(WriteRequestDeletes.newBuilder().addTupleKeys(TupleKeyWithoutCondition.newBuilder()
              .setUser("user:zed").setRelation("editor").setObject("doc:plan"))
              .addTupleKeys(TupleKeyWithoutCondition.newBuilder().setUser("user:bob").setRelation("editor")
                  .setObject("doc:plan")));
      assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.write(delete.build()));
      assertThat(fga.check(check("user:zed", "editor", "doc:plan").build()).await().getAllowed()).isTrue();
      fga.write(delete.setDeletes(delete.getDeletes().toBuilder().setOnMissing("ignore")).build()).await();
      assertThat(fga.check(check("user:zed", "editor", "doc:plan").build()).await().getAllowed()).isFalse();
    }

    @Test
    @DisplayName("refuses writes that are too large, repeat a tuple, have a condition or break the model")
    void refusesWrites() {
      WriteRequestWrites many = WriteRequestWrites.newBuilder().addAllTupleKeys(IntStream.range(0, 101)
          .mapToObj(i -> tuple("user:u" + i, "editor", "doc:plan")).toList()).build();
      assertAll(
          () -> assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.write(WriteRequest.newBuilder().setStoreId(store)
              .setWrites(many).build())),
          () -> assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.write(WriteRequest.newBuilder().setStoreId(store)
              .setWrites(WriteRequestWrites.newBuilder().addTupleKeys(tuple("user:zed", "editor", "doc:plan"))
                  .addTupleKeys(tuple("user:zed", "editor", "doc:plan")))
              .build())),
          () -> assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.write(WriteRequest.newBuilder().setStoreId(store)
              .setWrites(WriteRequestWrites.newBuilder().addTupleKeys(tuple("group:eng", "editor", "doc:plan")))
              .build())),
          () -> assertStatus(GrpcStatus.UNIMPLEMENTED, () -> fga.write(WriteRequest.newBuilder().setStoreId(store)
              .setWrites(WriteRequestWrites.newBuilder().addTupleKeys(tuple("user:zed", "editor", "doc:plan")
                  .toBuilder().setCondition(RelationshipCondition.newBuilder().setName("office"))))
              .build())));
    }

    private List<String> changes(String type, ReadChangesRequest.Builder request) {
      List<String> all = new ArrayList<>();
      String token = request.getContinuationToken();
      while (true) {
        ReadChangesResponse page = fga.readChanges(request.setStoreId(store).setType(type)
            .setPageSize(Int32Value.of(4)).setContinuationToken(token).build()).await();
        if (page.getChangesCount() == 0) {
          assertThat(page.getContinuationToken()).isEqualTo(token);
          return all;
        }
        page.getChangesList().forEach(change -> {
          assertThat(change.getTimestamp().getSeconds()).isPositive();
          all.add((change.getOperation() == TupleOperation.TUPLE_OPERATION_WRITE ? "+" : "-")
              + change.getTupleKey().getObject() + "#" + change.getTupleKey().getRelation() + "@"
              + change.getTupleKey().getUser());
        });
        token = page.getContinuationToken();
      }
    }

    @Test
    @DisplayName("reads the changes of the store in order, by type and since a time")
    void readsChanges() {
      fga.write(WriteRequest.newBuilder().setStoreId(store).setDeletes(WriteRequestDeletes.newBuilder()
          .addTupleKeys(TupleKeyWithoutCondition.newBuilder().setUser("user:dan").setRelation("blocked")
              .setObject("doc:plan")))
          .build()).await();
      assertAll(
          () -> assertThat(changes("", ReadChangesRequest.newBuilder())).containsExactly(
              "+group:eng#member@user:ana", "+folder:root#viewer@group:eng#member", "+doc:plan#parent@folder:root",
              "+doc:plan#editor@user:dan", "+doc:plan#blocked@user:dan", "+doc:memo#viewer@user:*",
              "-doc:plan#blocked@user:dan"),
          () -> assertThat(changes("folder", ReadChangesRequest.newBuilder())).containsExactly(
              "+folder:root#viewer@group:eng#member"),
          () -> assertThat(changes("", ReadChangesRequest.newBuilder().setStartTime(Timestamp.newBuilder()
              .setSeconds(Instant.now().plusSeconds(3600).getEpochSecond())))).isEmpty());
    }

    @Test
    @DisplayName("keeps the token of the last change until another change follows")
    void pollsChanges() {
      ReadChangesResponse all = fga.readChanges(ReadChangesRequest.newBuilder().setStoreId(store).build()).await();
      ReadChangesResponse none = fga.readChanges(ReadChangesRequest.newBuilder().setStoreId(store)
          .setContinuationToken(all.getContinuationToken()).build()).await();
      fga.write(WriteRequest.newBuilder().setStoreId(store).setWrites(WriteRequestWrites.newBuilder()
          .addTupleKeys(tuple("user:zed", "editor", "doc:plan"))).build()).await();
      ReadChangesResponse next = fga.readChanges(ReadChangesRequest.newBuilder().setStoreId(store)
          .setContinuationToken(none.getContinuationToken()).build()).await();
      assertAll(
          () -> assertThat(all.getChangesCount()).isEqualTo(6),
          () -> assertThat(none.getChangesList()).isEmpty(),
          () -> assertThat(none.getContinuationToken()).isEqualTo(all.getContinuationToken()),
          () -> assertThat(next.getChangesList()).extracting(change -> change.getTupleKey().getUser())
              .containsExactly("user:zed"),
          () -> assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.readChanges(ReadChangesRequest.newBuilder()
              .setStoreId(store).setType("doc").setContinuationToken(all.getContinuationToken()).build())));
    }

    @Test
    @DisplayName("keeps the assertions of a model in place of those it had")
    void assertions() {
      WriteAssertionsRequest.Builder request = WriteAssertionsRequest.newBuilder().setStoreId(store)
          .setAuthorizationModelId(model)
          .addAssertions(Assertion.newBuilder().setTupleKey(AssertionTupleKey.newBuilder().setUser("user:ana")
              .setRelation("viewer").setObject("doc:plan")).setExpectation(true))
          .addAssertions(Assertion.newBuilder().setTupleKey(AssertionTupleKey.newBuilder().setUser("user:zed")
              .setRelation("viewer").setObject("doc:plan")).setExpectation(true)
              .addContextualTuples(tuple("user:zed", "member", "group:eng")));
      fga.writeAssertions(request.build()).await();
      ReadAssertionsResponse read = fga.readAssertions(ReadAssertionsRequest.newBuilder().setStoreId(store)
          .setAuthorizationModelId(model).build()).await();
      assertAll(
          () -> assertThat(read.getAuthorizationModelId()).isEqualTo(model),
          () -> assertThat(read.getAssertionsList()).isEqualTo(request.getAssertionsList()),
          () -> assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.writeAssertions(WriteAssertionsRequest
              .newBuilder().setStoreId(store).setAuthorizationModelId(model).addAssertions(Assertion.newBuilder()
                  .setTupleKey(AssertionTupleKey.newBuilder().setUser("user:ana").setRelation("owner")
                      .setObject("doc:plan")))
              .build())),
          () -> assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.writeAssertions(WriteAssertionsRequest
              .newBuilder().setStoreId(store).setAuthorizationModelId(model).addAllAssertions(IntStream.range(0, 101)
                  .mapToObj(i -> request.getAssertions(0)).toList())
              .build())),
          () -> assertStatus(GrpcStatus.NOT_FOUND, () -> fga.readAssertions(ReadAssertionsRequest.newBuilder()
              .setStoreId(store).setAuthorizationModelId("01JAZ6S3N6T3Y5W1Z8PKV9B4CD").build())));
      fga.writeAssertions(WriteAssertionsRequest.newBuilder().setStoreId(store).setAuthorizationModelId(model).build())
          .await();
      assertThat(fga.readAssertions(ReadAssertionsRequest.newBuilder().setStoreId(store)
          .setAuthorizationModelId(model).build()).await().getAssertionsList()).isEmpty();
    }

    @Test
    @DisplayName("streams the objects of a list of objects")
    void streamsObjects() {
      assertThat(fga.streamedListObjects(StreamedListObjectsRequest.newBuilder().setStoreId(store).setUser("user:ana")
          .setRelation("viewer").setType("doc").build())
          .compose(stream -> stream.collect(Collectors.mapping(StreamedListObjectsResponse::getObject,
              Collectors.toList())))
          .await()).containsExactly("doc:memo", "doc:plan");
    }

    @Test
    @DisplayName("does not expand")
    void unimplemented() {
      assertStatus(GrpcStatus.UNIMPLEMENTED, () -> fga.expand(ExpandRequest.newBuilder().setStoreId(store).build()));
    }
  }
}
