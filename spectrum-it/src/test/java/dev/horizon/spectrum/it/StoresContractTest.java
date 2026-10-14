package dev.horizon.spectrum.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.google.protobuf.TextFormat;
import dev.horizon.basepoint.Dialect;
import dev.horizon.spectrum.Page;
import dev.horizon.spectrum.Relations;
import dev.horizon.spectrum.Stores;
import dev.horizon.spectrum.v1.Assertion;
import dev.horizon.spectrum.v1.Model;
import dev.horizon.spectrum.v1.Relationship;
import dev.horizon.spectrum.v1.Store;
import io.vertx.sqlclient.Pool;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
public abstract class StoresContractTest {

  private static final String ULID = "[0-9A-HJKMNP-TV-Z]{26}";

  private static final Model DOCS = model("""
      types { name: "user" }
      types { name: "doc" relations { name: "viewer" rewrite { direct { subjects: "user" } } } }
      """);

  private static final Model GROUPS = model("""
      types { name: "user" }
      types { name: "group" relations { name: "member" rewrite { direct { subjects: "user" } } } }
      """);

  protected Pool pool;
  protected Dialect dialect;
  protected Stores stores;

  @BeforeEach
  void open(Pool pool, Dialect dialect) {
    this.pool = pool;
    this.dialect = dialect;
    this.stores = Stores.open(pool, dialect);
  }

  private static Model model(String text) {
    try {
      return TextFormat.parse(text, Model.class);
    } catch (TextFormat.ParseException invalid) {
      throw new IllegalArgumentException(invalid);
    }
  }

  @Nested
  @DisplayName("stores")
  class Lifecycle {

    @Test
    @DisplayName("creates a store with a ULID and the time of the database, and gets it")
    void creates() {
      Store store = stores.create("documents").await();
      assertAll(
          () -> assertThat(store.getId()).matches(ULID),
          () -> assertThat(store.getName()).isEqualTo("documents"),
          () -> assertThat(store.getCreateTime().getSeconds()).isPositive(),
          () -> assertThat(stores.get(store.getId()).await()).isEqualTo(store));
    }

    @Test
    @DisplayName("gives each new store a greater id")
    void grows() {
      List<String> ids = IntStream.range(0, 5).mapToObj(i -> stores.create("s" + i).await().getId()).toList();
      assertThat(ids).isSorted().doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("fails to get a store that does not exist")
    void missing() {
      assertThatExceptionOfType(NoSuchElementException.class)
          .isThrownBy(() -> stores.get("01JAZ6RE1H6ZQ4WKQ2M0R0S8XQ").await())
          .withMessage("the store '01JAZ6RE1H6ZQ4WKQ2M0R0S8XQ' does not exist");
    }

    @ParameterizedTest(name = "{0} characters")
    @ValueSource(ints = { 0, 256 })
    void refusesName(int length) {
      assertThatIllegalArgumentException().isThrownBy(() -> stores.create("n".repeat(length)))
          .withMessage("the name of a store has 1 to 255 characters, not " + length);
    }

    @Test
    @DisplayName("lists the stores in pages, in order of id, all or by name")
    void lists() {
      List<Store> created = List.of(stores.create("a").await(), stores.create("b").await(),
          stores.create("b").await());
      Page<Store> first = stores.list("", 2, "").await();
      Page<Store> second = stores.list("", 2, first.next()).await();
      assertAll(
          () -> assertThat(first.items()).containsExactlyElementsOf(created.subList(0, 2)),
          () -> assertThat(first.hasNext()).isTrue(),
          () -> assertThat(second.items()).containsExactly(created.get(2)),
          () -> assertThat(second.hasNext()).isFalse(),
          () -> assertThat(stores.list("b", 10, "").await().items()).containsExactlyElementsOf(created.subList(1, 3)));
    }

    @Test
    @DisplayName("deletes a store with its models and relationships, and only that store")
    void deletes() {
      Store doomed = stores.create("doomed").await();
      Store kept = stores.create("kept").await();
      Model model = stores.writeModel(doomed.getId(), DOCS).await();
      Relationship ana = Relationship.newBuilder().setObject("doc:1").setRelation("viewer").setSubject("user:ana")
          .build();
      Relations.open(pool, dialect, doomed.getId(), model).write(ana).await();
      Relations.open(pool, dialect, kept.getId(), model).write(ana).await();
      assertThat(stores.delete(doomed.getId()).await()).isTrue();
      assertAll(
          () -> assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> stores.get(doomed.getId())
              .await()),
          () -> assertThat(Relations.open(pool, dialect, doomed.getId(), model).read("doc:1").await()).isEmpty(),
          () -> assertThat(Relations.open(pool, dialect, kept.getId(), model).read("doc:1").await()).hasSize(1),
          () -> assertThat(stores.delete(doomed.getId()).await()).isFalse());
    }
  }

  @Nested
  @DisplayName("models")
  class Models {

    private Store store;

    @BeforeEach
    void create() {
      store = stores.create("documents").await();
    }

    @Test
    @DisplayName("keeps a model with a ULID, and reads it")
    void keeps() {
      Model kept = stores.writeModel(store.getId(), DOCS).await();
      assertAll(
          () -> assertThat(kept.getId()).matches(ULID),
          () -> assertThat(kept.toBuilder().clearId().build()).isEqualTo(DOCS),
          () -> assertThat(stores.readModel(store.getId(), kept.getId()).await()).isEqualTo(kept));
    }

    @Test
    @DisplayName("reads the model that it kept last as the latest")
    void latest() {
      stores.writeModel(store.getId(), DOCS).await();
      Model last = stores.writeModel(store.getId(), GROUPS).await();
      assertThat(stores.latestModel(store.getId()).await()).isEqualTo(last);
    }

    @Test
    @DisplayName("lists the models in pages, the latest first")
    void lists() {
      List<Model> kept = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        kept.add(stores.writeModel(store.getId(), i % 2 == 0 ? DOCS : GROUPS).await());
      }
      List<String> listed = new ArrayList<>();
      String token = "";
      do {
        Page<Model> page = stores.listModels(store.getId(), 2, token).await();
        page.items().forEach(model -> listed.add(model.getId()));
        token = page.next();
      } while (!token.isEmpty());
      assertThat(listed).containsExactlyElementsOf(kept.reversed().stream().map(Model::getId).toList());
    }

    @Test
    @DisplayName("refuses a model that is not valid, before it reaches the database")
    void refuses() {
      assertThatIllegalArgumentException().isThrownBy(() -> stores.writeModel(store.getId(), model("""
          types { name: "doc" relations { name: "viewer" rewrite { computed { relation: "owner" } } } }
          """))).withMessage("the relation doc#viewer is computed from 'owner', which the type 'doc' does not have");
    }

    @Test
    @DisplayName("fails for a store or a model that does not exist")
    void missing() {
      assertAll(
          () -> assertThatExceptionOfType(NoSuchElementException.class)
              .isThrownBy(() -> stores.writeModel("01JAZ6RE1H6ZQ4WKQ2M0R0S8XQ", DOCS).await())
              .withMessage("the store '01JAZ6RE1H6ZQ4WKQ2M0R0S8XQ' does not exist"),
          () -> assertThatExceptionOfType(NoSuchElementException.class)
              .isThrownBy(() -> stores.readModel(store.getId(), "01JAZ6S3N6T3Y5W1Z8PKV9B4CD").await())
              .withMessage("the store '" + store.getId() + "' has no model '01JAZ6S3N6T3Y5W1Z8PKV9B4CD'"),
          () -> assertThatExceptionOfType(NoSuchElementException.class)
              .isThrownBy(() -> stores.latestModel(store.getId()).await())
              .withMessage("the store '" + store.getId() + "' has no model"));
    }
  }

  @Nested
  @DisplayName("assertions")
  class Assertions {

    private Store store;
    private Model model;

    @BeforeEach
    void create() {
      store = stores.create("documents").await();
      model = stores.writeModel(store.getId(), DOCS).await();
    }

    private Assertion assertion(String subject, boolean expectation, Relationship... contextual) {
      return Assertion.newBuilder().setSubject(subject).setRelation("viewer").setObject("doc:1")
          .setExpectation(expectation).addAllContextual(List.of(contextual)).build();
    }

    @Test
    @DisplayName("keeps the assertions of a model in place of those it had, and reads them in order")
    void keeps() {
      Relationship ana = Relationship.newBuilder().setObject("doc:1").setRelation("viewer").setSubject("user:ana")
          .build();
      List<Assertion> first = List.of(assertion("user:ana", true, ana), assertion("user:bob", false));
      stores.writeAssertions(store.getId(), model.getId(), first).await();
      assertThat(stores.readAssertions(store.getId(), model.getId()).await()).isEqualTo(first);
      stores.writeAssertions(store.getId(), model.getId(), List.of(assertion("user:zed", false))).await();
      assertThat(stores.readAssertions(store.getId(), model.getId()).await())
          .containsExactly(assertion("user:zed", false));
    }

    @Test
    @DisplayName("reads no assertions of a model that has none, and keeps them apart per model")
    void apart() {
      Model other = stores.writeModel(store.getId(), DOCS).await();
      stores.writeAssertions(store.getId(), model.getId(), List.of(assertion("user:ana", true))).await();
      assertThat(stores.readAssertions(store.getId(), other.getId()).await()).isEmpty();
    }

    @Test
    @DisplayName("refuses an assertion that the model cannot check, and a model that does not exist")
    void refuses() {
      assertAll(
          () -> assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> stores.writeAssertions(
              store.getId(), model.getId(), List.of(Assertion.newBuilder().setSubject("user:ana").setRelation("editor")
                  .setObject("doc:1").build()))
              .await())
              .withMessage("the type 'doc' has no relation 'editor'"),
          () -> assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> stores.readAssertions(
              store.getId(), "01JAZ6S3N6T3Y5W1Z8PKV9B4CD").await())
              .withMessage("the store '" + store.getId() + "' has no model '01JAZ6S3N6T3Y5W1Z8PKV9B4CD'"));
    }

    @Test
    @DisplayName("deletes the assertions and changes of a store with it")
    void deletes() {
      stores.writeAssertions(store.getId(), model.getId(), List.of(assertion("user:ana", true))).await();
      Relations relations = Relations.open(pool, dialect, store.getId(), model);
      relations.write(Relationship.newBuilder().setObject("doc:1").setRelation("viewer").setSubject("user:ana")
          .build()).await();
      stores.delete(store.getId()).await();
      assertThat(relations.changes("", Instant.EPOCH, 10, "").await().items()).isEmpty();
    }
  }
}
