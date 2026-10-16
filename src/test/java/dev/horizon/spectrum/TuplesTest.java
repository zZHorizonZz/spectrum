package dev.horizon.spectrum;

import static dev.horizon.spectrum.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Int32Value;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import dev.horizon.spectrum.openfga.v1.ReadRequest;
import dev.horizon.spectrum.openfga.v1.ReadRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.ReadResponse;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import dev.horizon.spectrum.openfga.v1.WriteRequest;
import dev.horizon.spectrum.openfga.v1.WriteRequestDeletes;
import dev.horizon.spectrum.openfga.v1.WriteRequestWrites;
import io.vertx.grpc.common.GrpcStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@SpectrumTest
class TuplesTest {

  private static final String CONDITIONS = """
      model
        schema 1.1

      type user

      type doc
        relations
          define viewer: [user, user with in_network]
          define owner: [user]

      condition in_network(ip: ipaddress, network: string) {
        ip.in_cidr(network)
      }
      """;

  private Spectrum spectrum;
  private OpenFGAServiceGrpcClient fga;
  private TestStore store;

  @BeforeEach
  void open(Spectrum spectrum) throws TimeoutException {
    this.spectrum = spectrum;
    this.fga = spectrum.client();
    this.store = spectrum.store(Documents.MODEL);
  }

  private List<String> read(String object, String relation, String user) throws TimeoutException {
    ReadRequest.Builder request = ReadRequest.newBuilder().setStoreId(store.id());
    if (object != null) {
      request.setTupleKey(ReadRequestTupleKey.newBuilder().setObject(object).setRelation(relation == null ? ""
          : relation).setUser(user == null ? "" : user));
    }
    return fga.read(request.build()).await(TIMEOUT).getTuplesList().stream()
        .map(tuple -> spectrum.text(tuple.getKey()))
        .sorted().toList();
  }

  private WriteRequest.Builder request() {
    return WriteRequest.newBuilder().setStoreId(store.id());
  }

  private WriteRequestDeletes deletes(String onMissing, String... tuples) {
    WriteRequestDeletes.Builder deletes = WriteRequestDeletes.newBuilder().setOnMissing(onMissing);
    for (String text : tuples) {
      TupleKey tuple = spectrum.tuple(text);
      deletes.addTupleKeys(TupleKeyWithoutCondition.newBuilder().setObject(tuple.getObject())
          .setRelation(tuple.getRelation()).setUser(tuple.getUser()));
    }
    return deletes.build();
  }

  private WriteRequestWrites writes(String onDuplicate, String... tuples) {
    return WriteRequestWrites.newBuilder().setOnDuplicate(onDuplicate).addAllTupleKeys(Stream.of(tuples)
        .map(spectrum::tuple).toList()).build();
  }

  @ParameterizedTest(name = "{0} {1} {2}: {3}")
  @CsvSource(delimiter = '|', textBlock = """
                |        |                  | doc:1#owner@user:anne doc:1#viewer@group:eng#member doc:1#viewer@user:anne doc:2#viewer@user:beth folder:1#viewer@user:anne
      doc:1     |        |                  | doc:1#owner@user:anne doc:1#viewer@group:eng#member doc:1#viewer@user:anne
      doc:1     | viewer |                  | doc:1#viewer@group:eng#member doc:1#viewer@user:anne
      doc:1     | viewer | user:anne        | doc:1#viewer@user:anne
      doc:      |        | user:anne        | doc:1#owner@user:anne doc:1#viewer@user:anne
      doc:      | viewer | group:eng#member | doc:1#viewer@group:eng#member
      """)
  void readsTheTuplesThatAFilterSelects(String object, String relation, String user, String tuples) throws TimeoutException {
    store.write("doc:1#viewer@user:anne", "doc:1#owner@user:anne", "doc:1#viewer@group:eng#member",
        "doc:2#viewer@user:beth", "folder:1#viewer@user:anne");
    assertThat(read(object, relation, user)).containsExactly(tuples.split(" "));
  }

  @Test
  void readsTheTuplesPageByPage() throws TimeoutException {
    List<TupleKey> written = IntStream.range(0, 5).mapToObj(i -> spectrum.tuple("doc:" + i + "#viewer@user:anne"))
        .toList();
    store.write(written);
    List<TupleKey> read = new ArrayList<>();
    String token = "";
    do {
      ReadResponse page = fga.read(ReadRequest.newBuilder().setStoreId(store.id()).setPageSize(Int32Value.of(2))
          .setContinuationToken(token).build()).await(TIMEOUT);
      page.getTuplesList().forEach(tuple -> read.add(tuple.getKey()));
      token = page.getContinuationToken();
    } while (!token.isEmpty());
    assertThat(read).containsExactlyElementsOf(written);
  }

  @Test
  void deletesTuplesAndWritesOthersInOneRequest() throws TimeoutException {
    store.write("doc:1#viewer@user:anne");
    fga.write(request().setDeletes(deletes("", "doc:1#viewer@user:anne")).setWrites(writes("",
        "doc:1#viewer@user:beth")).build()).await(TIMEOUT);
    assertThat(read("doc:1", "", "")).containsExactly("doc:1#viewer@user:beth");
  }

  @ParameterizedTest(name = "on_duplicate {0}")
  @CsvSource(delimiter = '|', textBlock = """
      ignore | true
      error  | false
             | false
      """)
  void writesATupleThatTheStoreHasByTheDuplicatePolicy(String policy, boolean succeeds) throws TimeoutException {
    store.write("doc:1#viewer@user:anne");
    WriteRequest request = request().setWrites(writes(policy == null ? "" : policy, "doc:1#viewer@user:anne",
        "doc:2#viewer@user:anne")).build();
    if (succeeds) {
      fga.write(request).await(TIMEOUT);
      assertThat(read(null, null, null)).containsExactly("doc:1#viewer@user:anne", "doc:2#viewer@user:anne");
    } else {
      spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.write(request));
      assertThat(read(null, null, null)).containsExactly("doc:1#viewer@user:anne");
    }
  }

  @ParameterizedTest(name = "on_missing {0}")
  @CsvSource(delimiter = '|', textBlock = """
      ignore | true
      error  | false
             | false
      """)
  void deletesATupleThatTheStoreDoesNotHaveByTheMissingPolicy(String policy, boolean succeeds) throws TimeoutException {
    store.write("doc:1#viewer@user:anne");
    WriteRequest request = request().setDeletes(deletes(policy == null ? "" : policy, "doc:1#viewer@user:anne",
        "doc:2#viewer@user:anne")).build();
    if (succeeds) {
      fga.write(request).await(TIMEOUT);
      assertThat(read(null, null, null)).isEmpty();
    } else {
      spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.write(request));
      assertThat(read(null, null, null)).containsExactly("doc:1#viewer@user:anne");
    }
  }

  @Test
  void refusesATupleThatIsInTheRequestTwice() {
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.write(request().setWrites(writes("",
        "doc:1#viewer@user:anne", "doc:1#viewer@user:anne")).build()));
  }

  @Test
  void refusesMoreTuplesThanTheLimit() throws TimeoutException {
    fga = spectrum.serve(new OpenFgaServiceOptions().withMaxTuplesPerWrite(1));
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.write(request().setWrites(writes("",
        "doc:1#viewer@user:anne", "doc:2#viewer@user:anne")).build()));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "doc:1#viewer@folder:1",
      "doc:1#reader@user:anne",
      "doc:1#viewers@user:anne",
      "document:1#viewer@user:anne",
      "doc:1#owner@user:*",
      "doc:1#parent@folder:1#viewer",
      "group:1#member@group:1#member" })
  void refusesATupleThatTheModelDoesNotAllow(String tuple) throws TimeoutException {
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.write(request().setWrites(writes("", tuple))
        .build()));
    assertThat(read(null, null, null)).isEmpty();
  }

  @Test
  void refusesAnEmptyWrite() {
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.write(request().build()));
  }

  @Test
  void readsTheConditionOfATuple() throws TimeoutException {
    TestStore conditional = spectrum.store(CONDITIONS);
    TupleKey tuple = spectrum.tuple("doc:1#viewer@user:anne", "in_network", "{\"network\": \"10.0.0.0/8\"}");
    conditional.write(List.of(tuple));
    assertThat(fga.read(ReadRequest.newBuilder().setStoreId(conditional.id()).build()).await(TIMEOUT).getTuples(0)
        .getKey()).isEqualTo(tuple);
  }

  @Test
  void refusesAWriteOfAStoredTupleWithAnotherCondition() throws TimeoutException {
    TestStore conditional = spectrum.store(CONDITIONS);
    conditional.write("doc:1#viewer@user:anne");
    spectrum.assertStatus(GrpcStatus.ABORTED, () -> fga.write(WriteRequest.newBuilder().setStoreId(conditional.id())
        .setWrites(WriteRequestWrites.newBuilder().setOnDuplicate("ignore").addTupleKeys(spectrum.tuple(
            "doc:1#viewer@user:anne", "in_network", "{\"network\": \"10.0.0.0/8\"}")))
        .build()));
  }

  @ParameterizedTest(name = "{0} with {1} {2}")
  @CsvSource(delimiter = '|', textBlock = """
      doc:1#viewer@user:anne | unknown    | {}
      doc:1#owner@user:anne  | in_network | {}
      doc:1#viewer@user:anne | in_network | {"network": 5}
      doc:1#viewer@user:anne | in_network | {"region": "eu"}
      """)
  void refusesAConditionThatTheModelDoesNotAllow(String tuple, String condition, String context) throws TimeoutException {
    TestStore conditional = spectrum.store(CONDITIONS);
    spectrum.assertStatus(GrpcStatus.INVALID_ARGUMENT, () -> fga.write(WriteRequest.newBuilder()
        .setStoreId(conditional.id()).setWrites(WriteRequestWrites.newBuilder().addTupleKeys(spectrum.tuple(tuple,
            condition, context)))
        .build()));
  }
}
