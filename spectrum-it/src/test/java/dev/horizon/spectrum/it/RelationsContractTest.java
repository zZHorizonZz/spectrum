package dev.horizon.spectrum.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.google.protobuf.TextFormat;
import dev.horizon.basepoint.Dialect;
import dev.horizon.spectrum.Page;
import dev.horizon.spectrum.Relations;
import dev.horizon.spectrum.v1.Change;
import dev.horizon.spectrum.v1.Model;
import dev.horizon.spectrum.v1.Operation;
import dev.horizon.spectrum.v1.Relationship;
import dev.horizon.spectrum.v1.SubjectSet;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
public abstract class RelationsContractTest {

  private static final Model COMPANY = model("""
      types { name: "user" }
      types {
        name: "group"
        relations { name: "member" rewrite { direct { subjects: ["user", "group#member"] } } }
      }
      types {
        name: "folder"
        relations { name: "parent" rewrite { direct { subjects: "folder" } } }
        relations { name: "owner" rewrite { direct { subjects: "user" } } }
        relations { name: "viewer" rewrite { union {
          rewrites { direct { subjects: ["user", "group#member"] } }
          rewrites { computed { relation: "owner" } }
          rewrites { from { tupleset: "parent" relation: "viewer" } }
        } } }
      }
      types {
        name: "doc"
        relations { name: "parent" rewrite { direct { subjects: "folder" } } }
        relations { name: "owner" rewrite { direct { subjects: "user" } } }
        relations { name: "editor" rewrite { union {
          rewrites { direct { subjects: ["user", "group#member"] } }
          rewrites { computed { relation: "owner" } }
        } } }
        relations { name: "viewer" rewrite { union {
          rewrites { direct { subjects: ["user", "user:*", "group#member"] } }
          rewrites { computed { relation: "editor" } }
          rewrites { from { tupleset: "parent" relation: "viewer" } }
        } } }
        relations { name: "blocked" rewrite { direct { subjects: "user" } } }
        relations { name: "reader" rewrite { difference {
          base { computed { relation: "viewer" } }
          subtract { computed { relation: "blocked" } }
        } } }
        relations { name: "approver" rewrite { direct { subjects: "user" } } }
        relations { name: "publisher" rewrite { intersection {
          rewrites { computed { relation: "editor" } }
          rewrites { computed { relation: "approver" } }
        } } }
      }
      """);

  private static final List<Relationship> COMPANY_RELATIONSHIPS = List.of(
      relationship("group:eng", "member", "user:ana"),
      relationship("group:eng", "member", "group:sre#member"),
      relationship("group:sre", "member", "user:bob"),
      relationship("folder:root", "owner", "user:carl"),
      relationship("folder:sub", "parent", "folder:root"),
      relationship("folder:sub", "viewer", "group:eng#member"),
      relationship("doc:plan", "parent", "folder:sub"),
      relationship("doc:plan", "editor", "user:dan"),
      relationship("doc:plan", "blocked", "user:bob"),
      relationship("doc:plan", "approver", "user:dan"),
      relationship("doc:plan", "approver", "user:ana"),
      relationship("doc:memo", "viewer", "user:*"),
      relationship("doc:memo", "owner", "user:erin"),
      relationship("doc:secret", "owner", "user:erin"));

  private static final SubjectSet NONE = SubjectSet.getDefaultInstance();

  protected Pool pool;
  protected Dialect dialect;
  protected Relations relations;

  @BeforeEach
  void open(Pool pool, Dialect dialect) {
    this.pool = pool;
    this.dialect = dialect;
    this.relations = Relations.open(pool, dialect, "company", COMPANY);
  }

  private static Model model(String text) {
    try {
      return TextFormat.parse(text, Model.class);
    } catch (TextFormat.ParseException invalid) {
      throw new IllegalArgumentException(invalid);
    }
  }

  private static Relationship relationship(String object, String relation, String subject) {
    return Relationship.newBuilder().setObject(object).setRelation(relation).setSubject(subject).build();
  }

  private static List<String> keys(List<Relationship> relationships) {
    return relationships.stream().map(r -> r.getObject() + "#" + r.getRelation() + "@" + r.getSubject()).toList();
  }

  private Relations open(String model) {
    return Relations.open(pool, dialect, "company", model(model));
  }

  @Nested
  @DisplayName("write, read and delete")
  class Writes {

    @Test
    @DisplayName("writes a relationship once")
    void writesOnce() {
      assertThat(relations.write(relationship("doc:plan", "viewer", "user:ana")).await()).isTrue();
      assertThat(relations.write(relationship("doc:plan", "viewer", "user:ana")).await()).isFalse();
    }

    @Test
    @DisplayName("counts only the relationships of a list that were not there, and each once")
    void writesList() {
      relations.write(relationship("doc:plan", "viewer", "user:ana")).await();
      assertThat(relations.write(List.of(relationship("doc:plan", "viewer", "user:ana"),
          relationship("doc:plan", "owner", "user:erin"), relationship("doc:plan", "owner", "user:erin"),
          relationship("doc:plan", "viewer", "group:eng#member"))).await()).isEqualTo(2);
      assertThat(relations.write(List.of()).await()).isZero();
    }

    @Test
    @DisplayName("writes more relationships than one statement takes")
    void writesMany() {
      List<Relationship> many = IntStream.range(0, 1201)
          .mapToObj(i -> relationship("doc:d" + i, "viewer", "user:ana")).toList();
      assertThat(relations.write(many).await()).isEqualTo(1201);
      assertThat(relations.listObjects("user:ana", "viewer", "doc").await()).hasSize(1201);
    }

    @Test
    @DisplayName("reads the relationships of an object by relation and subject, with the id and time the database"
        + " sets")
    void readsObject() {
      relations.write(List.of(relationship("doc:plan", "viewer", "user:ana"),
          relationship("doc:plan", "owner", "user:erin"), relationship("doc:plan", "viewer", "group:eng#member"),
          relationship("doc:memo", "owner", "user:erin"))).await();
      List<Relationship> read = relations.read("doc:plan").await();
      assertThat(keys(read)).containsExactly("doc:plan#owner@user:erin", "doc:plan#viewer@group:eng#member",
          "doc:plan#viewer@user:ana");
      assertThat(read).allSatisfy(relationship -> {
        assertThat(relationship.getId()).isNotEmpty();
        assertThat(relationship.getCreateTime().getSeconds()).isPositive();
      });
      assertThat(keys(relations.read("doc:plan", "viewer").await())).containsExactly(
          "doc:plan#viewer@group:eng#member", "doc:plan#viewer@user:ana");
    }

    @Test
    @DisplayName("deletes a relationship once, and counts only the relationships of a list that were there")
    void deletes() {
      relations.write(List.of(relationship("doc:plan", "viewer", "user:ana"),
          relationship("doc:plan", "owner", "user:erin"), relationship("doc:plan", "viewer", "user:dan"))).await();
      assertThat(relations.delete(relationship("doc:plan", "owner", "user:erin")).await()).isTrue();
      assertThat(relations.delete(relationship("doc:plan", "owner", "user:erin")).await()).isFalse();
      assertThat(relations.delete(List.of(relationship("doc:plan", "viewer", "user:ana"),
          relationship("doc:plan", "viewer", "user:bob"))).await()).isEqualTo(1);
      assertThat(keys(relations.read("doc:plan").await())).containsExactly("doc:plan#viewer@user:dan");
    }

    @Test
    @DisplayName("tells objects and subjects apart by case")
    void caseSensitive() {
      relations.write(relationship("doc:a", "viewer", "user:ana")).await();
      assertAll(
          () -> assertThat(relations.check("user:ana", "viewer", "doc:a").await()).isTrue(),
          () -> assertThat(relations.check("user:ana", "viewer", "doc:A").await()).isFalse(),
          () -> assertThat(relations.check("user:Ana", "viewer", "doc:a").await()).isFalse(),
          () -> assertThat(relations.write(relationship("doc:A", "viewer", "user:ana")).await()).isTrue(),
          () -> assertThat(relations.listObjects("user:ana", "viewer", "doc").await()).containsExactly("doc:A",
              "doc:a"));
    }
  }

  @Nested
  @DisplayName("stores")
  class Stores {

    @Test
    @DisplayName("keeps the relationships of each store apart")
    void apart() {
      Relations other = Relations.open(pool, dialect, "other", COMPANY);
      relations.write(relationship("doc:plan", "viewer", "user:ana")).await();
      assertAll(
          () -> assertThat(other.check("user:ana", "viewer", "doc:plan").await()).isFalse(),
          () -> assertThat(other.read("doc:plan").await()).isEmpty(),
          () -> assertThat(other.write(relationship("doc:plan", "viewer", "user:ana")).await()).isTrue(),
          () -> assertThat(keys(relations.read("doc:plan").await())).containsExactly("doc:plan#viewer@user:ana"),
          () -> assertThat(relations.read("doc:plan").await().getFirst().getStoreId()).isEqualTo("company"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
        my store                          | the store 'my store' is not valid: a store has 1 to 32 letters, digits, '_' and '-'
        ""                                | the store '' is not valid: a store has 1 to 32 letters, digits, '_' and '-'
        012345678901234567890123456789012 | the store '012345678901234567890123456789012' is not valid: a store has 1 to 32 letters, digits, '_' and '-'
        """)
    void refusesStore(String store, String message) {
      assertThatIllegalArgumentException().isThrownBy(() -> Relations.open(pool, dialect, store, COMPANY))
          .withMessage(message);
    }
  }

  @Nested
  @DisplayName("contextual relationships")
  class Contextual {

    @BeforeEach
    void write() {
      relations.write(COMPANY_RELATIONSHIPS).await();
    }

    @Test
    @DisplayName("counts contextual relationships in checks and lists, and writes none of them")
    void counts() {
      Relations zed = relations.with(List.of(relationship("group:eng", "member", "user:zed")));
      assertAll(
          () -> assertThat(zed.check("user:zed", "viewer", "doc:plan").await()).isTrue(),
          () -> assertThat(zed.listObjects("user:zed", "viewer", "doc").await()).containsExactly("doc:memo",
              "doc:plan"),
          () -> assertThat(zed.listSubjects("doc:plan", "viewer", "user").await().getSubjectsList())
              .containsExactly("user:ana", "user:bob", "user:carl", "user:dan", "user:zed"),
          () -> assertThat(relations.check("user:zed", "viewer", "doc:plan").await()).isFalse(),
          () -> assertThat(keys(zed.read("group:eng").await())).containsExactly("group:eng#member@group:sre#member",
              "group:eng#member@user:ana"));
    }

    @Test
    @DisplayName("refuses a contextual relationship that the model does not allow")
    void refuses() {
      assertThatIllegalArgumentException().isThrownBy(() -> relations.with(List.of(relationship("doc:plan", "reader",
          "user:zed")))).withMessage("the model does not allow doc:plan#reader@user:zed: no direct rewrite of"
              + " doc#reader allows user");
    }
  }

  @Nested
  @DisplayName("pages")
  class Pages {

    @BeforeEach
    void write() {
      relations.write(COMPANY_RELATIONSHIPS).await();
    }

    private List<String> all(Relationship filter, int size) {
      List<String> all = new ArrayList<>();
      String token = "";
      do {
        Page<Relationship> page = relations.read(filter, size, token).await();
        assertThat(page.items()).hasSizeLessThanOrEqualTo(size);
        all.addAll(keys(page.items()));
        token = page.next();
      } while (!token.isEmpty());
      return all;
    }

    @ParameterizedTest(name = "{0} | {1} | {2}, {3} per page")
    @CsvSource(delimiter = '|', textBlock = """
        ''        | ''       | ''               | 4 | doc:memo#owner@user:erin doc:memo#viewer@user:* doc:plan#approver@user:ana doc:plan#approver@user:dan doc:plan#blocked@user:bob doc:plan#editor@user:dan doc:plan#parent@folder:sub doc:secret#owner@user:erin folder:root#owner@user:carl folder:sub#parent@folder:root folder:sub#viewer@group:eng#member group:eng#member@group:sre#member group:eng#member@user:ana group:sre#member@user:bob
        doc:      | ''       | ''               | 3 | doc:memo#owner@user:erin doc:memo#viewer@user:* doc:plan#approver@user:ana doc:plan#approver@user:dan doc:plan#blocked@user:bob doc:plan#editor@user:dan doc:plan#parent@folder:sub doc:secret#owner@user:erin
        doc:plan  | approver | ''               | 1 | doc:plan#approver@user:ana doc:plan#approver@user:dan
        ''        | owner    | user:erin        | 1 | doc:memo#owner@user:erin doc:secret#owner@user:erin
        folder:   | ''       | group:eng#member | 5 | folder:sub#viewer@group:eng#member
        group:    | viewer   | ''               | 5 |
        """)
    void reads(String object, String relation, String subject, int size,
        @ConvertWith(SpaceSeparated.class) List<String> expected) {
      assertThat(all(Relationship.newBuilder().setObject(object).setRelation(relation).setSubject(subject).build(),
          size)).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("ends with an empty token on the last page")
    void lastPage() {
      Page<Relationship> page = relations.read(Relationship.newBuilder().setObject("doc:plan").setRelation("editor")
          .build(), 5, "").await();
      assertThat(page.next()).isEmpty();
      assertThat(page.hasNext()).isFalse();
    }

    @ParameterizedTest(name = "{0}, {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
        0 | ''    | the size of a page is 0, not at least 1
        2 | x!y   | the page token 'x!y' is not valid
        2 | YWJj  | the page token 'YWJj' is not valid
        """)
    void refuses(int size, String token, String message) {
      assertThatIllegalArgumentException().isThrownBy(() -> relations.read(Relationship.getDefaultInstance(), size,
          token)).withMessage(message);
    }
  }

  @Nested
  @DisplayName("changes")
  class Changes {

    private List<String> changes(Relations of, String type, Instant since) {
      List<String> all = new ArrayList<>();
      String token = "";
      while (true) {
        Page<Change> page = of.changes(type, since, 2, token).await();
        if (page.items().isEmpty()) {
          assertThat(page.next()).isEqualTo(token);
          return all;
        }
        page.items().forEach(change -> all.add((change.getOperation() == Operation.OPERATION_WRITE ? "+" : "-")
            + change.getObject() + "#" + change.getRelation() + "@" + change.getSubject()));
        token = page.next();
      }
    }

    @Test
    @DisplayName("records each write and delete that changed the store, in order, and nothing else")
    void records() {
      relations.write(List.of(relationship("doc:plan", "viewer", "user:ana"), relationship("doc:plan", "owner",
          "user:erin"))).await();
      relations.write(relationship("doc:plan", "viewer", "user:ana")).await();
      relations.delete(List.of(relationship("doc:plan", "viewer", "user:ana"), relationship("doc:plan", "viewer",
          "user:bob"))).await();
      relations.with(List.of(relationship("doc:plan", "viewer", "user:zed"))).check("user:zed", "viewer", "doc:plan")
          .await();
      relations.write(relationship("doc:plan", "viewer", "user:ana")).await();
      assertThat(changes(relations, "", Instant.EPOCH)).containsExactly("+doc:plan#viewer@user:ana",
          "+doc:plan#owner@user:erin", "-doc:plan#viewer@user:ana", "+doc:plan#viewer@user:ana");
    }

    @Test
    @DisplayName("gives each change a growing ULID, the store and the time of the database")
    void identifies() {
      relations.write(List.of(relationship("doc:a", "viewer", "user:ana"), relationship("doc:b", "viewer",
          "user:ana"))).await();
      List<Change> page = relations.changes("", Instant.EPOCH, 10, "").await().items();
      assertAll(
          () -> assertThat(page).extracting(Change::getId).isSorted().allMatch(id -> id.matches(
              "[0-9A-HJKMNP-TV-Z]{26}")),
          () -> assertThat(page).extracting(Change::getStoreId).containsOnly("company"),
          () -> assertThat(page).allSatisfy(change -> assertThat(change.getCreateTime().getSeconds()).isPositive()));
    }

    @Test
    @DisplayName("gives the same token when no change follows, and the changes that follow it later")
    void polls() {
      assertThat(relations.changes("", Instant.EPOCH, 10, "").await()).isEqualTo(new Page<Change>(List.of(), ""));
      relations.write(relationship("doc:a", "viewer", "user:ana")).await();
      Page<Change> first = relations.changes("", Instant.EPOCH, 10, "").await();
      Page<Change> none = relations.changes("", Instant.EPOCH, 10, first.next()).await();
      relations.delete(relationship("doc:a", "viewer", "user:ana")).await();
      Page<Change> later = relations.changes("", Instant.EPOCH, 10, none.next()).await();
      assertAll(
          () -> assertThat(first.items()).hasSize(1),
          () -> assertThat(none.items()).isEmpty(),
          () -> assertThat(none.next()).isEqualTo(first.next()),
          () -> assertThat(later.items()).extracting(Change::getOperation).containsExactly(Operation.OPERATION_DELETE));
    }

    @Test
    @DisplayName("reads the changes of one type, of one store, and since a time")
    void filters() {
      relations.write(List.of(relationship("doc:a", "viewer", "user:ana"), relationship("folder:f", "owner",
          "user:ana"), relationship("doc:b", "viewer", "user:bob"))).await();
      Relations other = Relations.open(pool, dialect, "other", COMPANY);
      other.write(relationship("doc:z", "viewer", "user:zed")).await();
      assertAll(
          () -> assertThat(changes(relations, "doc", Instant.EPOCH)).containsExactly("+doc:a#viewer@user:ana",
              "+doc:b#viewer@user:bob"),
          () -> assertThat(changes(relations, "folder", Instant.EPOCH)).containsExactly("+folder:f#owner@user:ana"),
          () -> assertThat(changes(other, "", Instant.EPOCH)).containsExactly("+doc:z#viewer@user:zed"),
          () -> assertThat(changes(relations, "", Instant.now().plusSeconds(3600))).isEmpty(),
          () -> assertThat(changes(relations, "", Instant.now().minusSeconds(3600))).hasSize(3));
    }

    @Test
    @DisplayName("records no change of a transaction that rolls back")
    void rollsBack() {
      assertThatIllegalStateException().isThrownBy(() -> pool.withTransaction(tx -> relations.on(tx)
          .write(relationship("doc:a", "viewer", "user:ana"))
          .compose(written -> Future.failedFuture(new IllegalStateException("rolled back")))).await());
      assertThat(changes(relations, "", Instant.EPOCH)).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
        Doc | 2 | ""                                       | the type 'Doc' is not a name: at most 64 lower case letters, digits and '_', starting with a letter
        doc | 0 | ""                                       | the size of a page is 0, not at least 1
        ""  | 2 | MDFNNFhOWVc1U0s0REZDUFJQRTdTSFc1SDAAZG9j | the page token is one of the changes of the type 'doc', not ''
        doc | 2 | YWJj                                     | the page token 'YWJj' is not valid
        """)
    void refuses(String type, int size, String token, String message) {
      assertThatIllegalArgumentException().isThrownBy(() -> relations.changes(type, Instant.EPOCH, size, token))
          .withMessage(message);
    }
  }

  @Nested
  @DisplayName("refusals")
  class Refusals {

    @ParameterizedTest(name = "{0}#{1}@{2}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
        Doc:1  | viewer | user:ana      | the object 'Doc:1' is not valid: a type and a relation have at most 64 lower case letters, digits and '_', and an id has no spaces and no '#'
        doc:1  | Viewer | user:ana      | the relation 'Viewer' is not a name: at most 64 lower case letters, digits and '_', starting with a letter
        doc:1  | viewer | user:*#member | the subject 'user:*#member' is not valid: a subject is type:id, type:* or type:id#relation
        doc:1  | viewer | group:eng     | the model does not allow doc:1#viewer@group:eng: no direct rewrite of doc#viewer allows group
        doc:1  | owner  | user:*        | the model does not allow doc:1#owner@user:*: no direct rewrite of doc#owner allows user:*
        doc:1  | reader | user:ana      | the model does not allow doc:1#reader@user:ana: no direct rewrite of doc#reader allows user
        doc:1  | viewer | page:1        | the model has no type 'page'
        page:1 | viewer | user:ana      | the model has no type 'page'
        doc:1  | viewr  | user:ana      | the type 'doc' has no relation 'viewr'
        """)
    void refusesWrite(String object, String relation, String subject, String message) {
      assertThatIllegalArgumentException().isThrownBy(() -> relations.write(relationship(object, relation, subject)))
          .withMessage(message);
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
        user:ana        | viewr  | doc:1  | the type 'doc' has no relation 'viewr'
        user:ana        | viewer | doc:*  | the object 'doc:*' is not valid: an object is type:id
        user:ana        | viewer | page:1 | the model has no type 'page'
        group:eng#owner | viewer | doc:1  | the type 'group' has no relation 'owner'
        """)
    void refusesCheck(String subject, String relation, String object, String message) {
      assertThatIllegalArgumentException().isThrownBy(() -> relations.check(subject, relation, object))
          .withMessage(message);
    }
  }

  @Nested
  @DisplayName("with the relationships of a company")
  class Company {

    @BeforeEach
    void write() {
      relations.write(COMPANY_RELATIONSHIPS).await();
    }

    @ParameterizedTest(name = "{0} {1} {2}: {3}")
    @CsvSource(delimiter = '|', textBlock = """
        user:ana         | viewer    | doc:plan    | true
        user:bob         | viewer    | doc:plan    | true
        user:carl        | viewer    | doc:plan    | true
        user:dan         | viewer    | doc:plan    | true
        user:erin        | viewer    | doc:plan    | false
        user:ana         | reader    | doc:plan    | true
        user:bob         | reader    | doc:plan    | false
        user:dan         | publisher | doc:plan    | true
        user:ana         | publisher | doc:plan    | false
        user:zed         | viewer    | doc:memo    | true
        user:zed         | viewer    | doc:secret  | false
        user:*           | viewer    | doc:memo    | true
        user:*           | viewer    | doc:plan    | false
        group:sre#member | member    | group:eng   | true
        group:eng#member | viewer    | folder:sub  | true
        group:eng#member | viewer    | folder:root | false
        """)
    void checks(String subject, String relation, String object, boolean expected) {
      assertThat(relations.check(subject, relation, object).await()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} {1} {2}: {3}")
    @CsvSource(delimiter = '|', textBlock = """
        user:ana  | viewer    | doc    | doc:memo doc:plan
        user:carl | viewer    | folder | folder:root folder:sub
        user:bob  | reader    | doc    | doc:memo
        user:erin | viewer    | doc    | doc:memo doc:secret
        user:dan  | publisher | doc    | doc:plan
        user:erin | publisher | doc    |
        user:bob  | member    | group  | group:eng group:sre
        user:*    | viewer    | doc    | doc:memo
        """)
    void listsObjects(String subject, String relation, String type,
        @ConvertWith(SpaceSeparated.class) List<String> expected) {
      assertThat(relations.listObjects(subject, relation, type).await()).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest(name = "{0} {1} {2}: {3}")
    @CsvSource(delimiter = '|', textBlock = """
        doc:plan   | viewer    | user  | subjects: ["user:ana", "user:bob", "user:carl", "user:dan"]
        doc:plan   | reader    | user  | subjects: ["user:ana", "user:carl", "user:dan"]
        doc:plan   | publisher | user  | subjects: "user:dan"
        doc:memo   | viewer    | user  | all: true
        doc:memo   | reader    | user  | all: true
        doc:secret | editor    | user  | subjects: "user:erin"
        doc:secret | viewer    | group |
        group:eng  | member    | user  | subjects: ["user:ana", "user:bob"]
        """)
    void listsSubjects(String object, String relation, String type, @ConvertWith(ProtoText.class) SubjectSet expected) {
      assertThat(relations.listSubjects(object, relation, type).await()).isEqualTo(expected);
    }

    @TestFactory
    @DisplayName("gives the same answer by a check, a list of objects and a list of subjects")
    Stream<DynamicNode> agrees() {
      List<String> users = List.of("user:ana", "user:bob", "user:carl", "user:dan", "user:erin", "user:zed");
      List<String> docs = List.of("doc:memo", "doc:plan", "doc:secret");
      return Stream.of("viewer", "editor", "reader", "publisher").map(relation -> dynamicContainer(relation,
          docs.stream().map(doc -> dynamicContainer(doc, Stream.concat(
              users.stream().map(user -> dynamicTest(user, () -> {
                boolean checked = relations.check(user, relation, doc).await();
                SubjectSet subjects = relations.listSubjects(doc, relation, "user").await();
                assertAll(
                    () -> assertThat(relations.listObjects(user, relation, "doc").await().contains(doc))
                        .isEqualTo(checked),
                    () -> assertThat(subjects.getAll() ? !subjects.getExceptList().contains(user)
                        : subjects.getSubjectsList().contains(user)).isEqualTo(checked));
              })),
              Stream.of(dynamicTest("user:*", () -> {
                SubjectSet subjects = relations.listSubjects(doc, relation, "user").await();
                assertThat(relations.check("user:*", relation, doc).await())
                    .isEqualTo(subjects.getAll() && subjects.getExceptCount() == 0);
              })))))));
    }
  }

  @Nested
  @DisplayName("cycles")
  class Cycles {

    @Test
    @DisplayName("stops at a cycle of groups")
    void groups() {
      relations.write(List.of(relationship("group:a", "member", "group:b#member"),
          relationship("group:b", "member", "group:a#member"), relationship("group:a", "member", "user:x"))).await();
      assertAll(
          () -> assertThat(relations.check("user:x", "member", "group:b").await()).isTrue(),
          () -> assertThat(relations.check("user:y", "member", "group:a").await()).isFalse(),
          () -> assertThat(relations.listObjects("user:x", "member", "group").await()).containsExactly("group:a",
              "group:b"),
          () -> assertThat(relations.listSubjects("group:b", "member", "user").await().getSubjectsList())
              .containsExactly("user:x"));
    }

    @Test
    @DisplayName("denies a difference that a cycle back to its own relation leaves undecided")
    void throughDifference() {
      Relations loop = open("""
          types { name: "user" }
          types {
            name: "doc"
            relations { name: "viewer" rewrite { direct { subjects: "user" } } }
            relations { name: "blocked" rewrite { computed { relation: "reader" } } }
            relations { name: "reader" rewrite { difference {
              base { computed { relation: "viewer" } }
              subtract { computed { relation: "blocked" } }
            } } }
          }
          """);
      loop.write(relationship("doc:1", "viewer", "user:ana")).await();
      assertAll(
          () -> assertThat(loop.check("user:ana", "reader", "doc:1").await()).isFalse(),
          () -> assertThat(loop.listObjects("user:ana", "reader", "doc").await()).isEmpty(),
          () -> assertThat(loop.listSubjects("doc:1", "reader", "user").await()).isEqualTo(NONE));
    }

    @Test
    @DisplayName("keeps a relation that a cycle leaves undecided undecided, also as what a difference leaves out")
    void undecidedLeftOut() {
      Relations loop = open("""
          types { name: "user" }
          types {
            name: "doc"
            relations { name: "viewer" rewrite { direct { subjects: "user" } } }
            relations { name: "again" rewrite { computed { relation: "paradox" } } }
            relations { name: "paradox" rewrite { difference {
              base { computed { relation: "viewer" } }
              subtract { computed { relation: "again" } }
            } } }
            relations { name: "reader" rewrite { difference {
              base { computed { relation: "viewer" } }
              subtract { computed { relation: "paradox" } }
            } } }
          }
          """);
      loop.write(relationship("doc:1", "viewer", "user:ana")).await();
      assertAll(
          () -> assertThat(loop.check("user:ana", "paradox", "doc:1").await()).isFalse(),
          () -> assertThat(loop.check("user:ana", "reader", "doc:1").await()).isFalse(),
          () -> assertThat(loop.listObjects("user:ana", "reader", "doc").await()).isEmpty(),
          () -> assertThat(loop.listSubjects("doc:1", "reader", "user").await()).isEqualTo(NONE),
          () -> assertThat(loop.listSubjects("doc:1", "paradox", "user").await()).isEqualTo(NONE));
    }

    @Test
    @DisplayName("decides a difference whose part to leave out has a cycle of its own")
    void insideDifference() {
      Relations groups = open("""
          types { name: "user" }
          types {
            name: "group"
            relations { name: "member" rewrite { direct { subjects: ["user", "group#member"] } } }
          }
          types {
            name: "doc"
            relations { name: "viewer" rewrite { direct { subjects: "user" } } }
            relations { name: "blocked" rewrite { direct { subjects: ["user", "group#member"] } } }
            relations { name: "reader" rewrite { difference {
              base { computed { relation: "viewer" } }
              subtract { computed { relation: "blocked" } }
            } } }
          }
          """);
      groups.write(List.of(relationship("group:a", "member", "group:b#member"),
          relationship("group:b", "member", "group:a#member"), relationship("group:b", "member", "user:y"),
          relationship("doc:1", "blocked", "group:a#member"), relationship("doc:1", "viewer", "user:x"),
          relationship("doc:1", "viewer", "user:y"))).await();
      assertAll(
          () -> assertThat(groups.check("user:x", "reader", "doc:1").await()).isTrue(),
          () -> assertThat(groups.check("user:y", "reader", "doc:1").await()).isFalse(),
          () -> assertThat(groups.listObjects("user:x", "reader", "doc").await()).containsExactly("doc:1"),
          () -> assertThat(groups.listObjects("user:y", "reader", "doc").await()).isEmpty(),
          () -> assertThat(groups.listSubjects("doc:1", "reader", "user").await().getSubjectsList())
              .containsExactly("user:x"));
    }
  }

  @Nested
  @DisplayName("every object of a type")
  class Wildcards {

    private Relations open;

    @BeforeEach
    void write() {
      open = open("""
          types { name: "user" }
          types {
            name: "doc"
            relations { name: "viewer" rewrite { direct { subjects: ["user", "user:*"] } } }
            relations { name: "blocked" rewrite { direct { subjects: "user" } } }
            relations { name: "reader" rewrite { difference {
              base { computed { relation: "viewer" } }
              subtract { computed { relation: "blocked" } }
            } } }
            relations { name: "outer" rewrite { difference {
              base { computed { relation: "viewer" } }
              subtract { computed { relation: "reader" } }
            } } }
          }
          """);
      open.write(List.of(relationship("doc:1", "viewer", "user:*"), relationship("doc:1", "blocked", "user:bob"),
          relationship("doc:2", "viewer", "user:*"))).await();
    }

    @ParameterizedTest(name = "{0} {1} {2}: {3}")
    @CsvSource(delimiter = '|', textBlock = """
        user:bob | reader | doc:1 | false
        user:ana | reader | doc:1 | true
        user:*   | reader | doc:1 | false
        user:*   | reader | doc:2 | true
        user:bob | outer  | doc:1 | true
        user:ana | outer  | doc:1 | false
        user:*   | outer  | doc:2 | false
        """)
    void checks(String subject, String relation, String object, boolean expected) {
      assertThat(open.check(subject, relation, object).await()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} {1} {2}: {3}")
    @CsvSource(delimiter = '|', textBlock = """
        user:*   | reader | doc | doc:2
        user:bob | reader | doc | doc:2
        user:ana | reader | doc | doc:1 doc:2
        user:bob | outer  | doc | doc:1
        user:ana | outer  | doc |
        """)
    void listsObjects(String subject, String relation, String type,
        @ConvertWith(SpaceSeparated.class) List<String> expected) {
      assertThat(open.listObjects(subject, relation, type).await()).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest(name = "{0} {1} {2}: {3}")
    @CsvSource(delimiter = '|', textBlock = """
        doc:1 | reader | user | all: true except: "user:bob"
        doc:2 | reader | user | all: true
        doc:1 | outer  | user | subjects: "user:bob"
        doc:2 | outer  | user |
        """)
    void listsSubjects(String object, String relation, String type, @ConvertWith(ProtoText.class) SubjectSet expected) {
      assertThat(open.listSubjects(object, relation, type).await()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("a model that no longer allows relationships")
  class Narrowed {

    private static final String WIDE = """
        types { name: "user" }
        types { name: "folder" relations { name: "viewer" rewrite { direct { subjects: "user" } } } }
        types {
          name: "doc"
          relations { name: "parent" rewrite { direct { subjects: ["folder", "doc"] } } }
          relations { name: "viewer" rewrite { union {
            rewrites { direct { subjects: "user" } }
            rewrites { from { tupleset: "parent" relation: "viewer" } }
          } } }
        }
        """;

    private Relations wide;
    private Relations narrow;

    @BeforeEach
    void write() {
      wide = open(WIDE);
      narrow = open(WIDE.replace("subjects: [\"folder\", \"doc\"]", "subjects: \"folder\""));
      wide.write(List.of(relationship("doc:a", "viewer", "user:ana"), relationship("doc:b", "parent", "doc:a"),
          relationship("folder:f", "viewer", "user:bob"), relationship("doc:b", "parent", "folder:f"))).await();
    }

    @Test
    @DisplayName("follows only the relationships of a tupleset that the model still allows")
    void ignores() {
      assertAll(
          () -> assertThat(wide.check("user:ana", "viewer", "doc:b").await()).isTrue(),
          () -> assertThat(narrow.check("user:ana", "viewer", "doc:b").await()).isFalse(),
          () -> assertThat(narrow.check("user:bob", "viewer", "doc:b").await()).isTrue(),
          () -> assertThat(wide.listSubjects("doc:b", "viewer", "user").await().getSubjectsList())
              .containsExactly("user:ana", "user:bob"),
          () -> assertThat(narrow.listSubjects("doc:b", "viewer", "user").await().getSubjectsList())
              .containsExactly("user:bob"),
          () -> assertThat(narrow.listObjects("user:ana", "viewer", "doc").await()).containsExactly("doc:a"));
    }
  }

  @Nested
  @DisplayName("depth")
  class Depth {

    @BeforeEach
    void write() {
      relations.write(Stream.concat(Stream.of(relationship("folder:f0", "owner", "user:ana")),
          IntStream.rangeClosed(1, 30).mapToObj(i -> relationship("folder:f" + i, "parent", "folder:f" + (i - 1))))
          .toList()).await();
    }

    @ParameterizedTest(name = "follows a chain of {0} parents")
    @CsvSource({ "1", "10", "20" })
    void followsChain(int depth) {
      assertThat(relations.check("user:ana", "viewer", "folder:f" + depth).await()).isTrue();
    }

    @Test
    @DisplayName("fails a check and a list that go deeper than 25 levels")
    void failsTooDeep() {
      String message = "the relations go more than 25 levels deep; check the model and the tuples for a chain that"
          + " long";
      assertAll(
          () -> assertThatIllegalStateException().isThrownBy(() -> relations.check("user:ana", "viewer", "folder:f30")
              .await()).withMessage(message),
          () -> assertThatIllegalStateException().isThrownBy(() -> relations.listObjects("user:ana", "viewer",
              "folder").await()).withMessage(message));
    }
  }

  @Nested
  @DisplayName("transactions")
  class Transactions {

    private final Relationship relationship = relationship("doc:plan", "viewer", "user:ana");

    @Test
    @DisplayName("sees a relationship in the transaction that wrote it, and forgets it on a rollback")
    void rollsBack() {
      assertThatIllegalStateException().isThrownBy(() -> pool.withTransaction(tx -> relations.on(tx)
          .write(relationship).compose(written -> relations.on(tx).check("user:ana", "viewer", "doc:plan"))
          .compose(seen -> Future.failedFuture(new IllegalStateException("seen " + seen)))).await())
          .withMessage("seen true");
      assertThat(relations.check("user:ana", "viewer", "doc:plan").await()).isFalse();
    }

    @Test
    @DisplayName("keeps a relationship that a committed transaction wrote")
    void commits() {
      pool.withTransaction(tx -> relations.on(tx).write(relationship)).await();
      assertThat(relations.check("user:ana", "viewer", "doc:plan").await()).isTrue();
    }

    @Test
    @DisplayName("writes a list in the transaction of the caller")
    void joinsTransaction() {
      assertThatIllegalStateException().isThrownBy(() -> pool.withTransaction(tx -> relations.on(tx)
          .write(List.of(relationship, relationship("doc:plan", "owner", "user:erin")))
          .compose(written -> Future.failedFuture(new IllegalStateException("written " + written)))).await())
          .withMessage("written 2");
      assertThat(relations.read("doc:plan").await()).isEmpty();
    }

    @Test
    @DisplayName("writes a list on a connection with no transaction in a transaction of its own")
    void beginsTransaction() {
      SqlConnection connection = pool.getConnection().await();
      try {
        assertThat(relations.on(connection).write(List.of(relationship, relationship("doc:plan", "owner",
            "user:erin"))).await()).isEqualTo(2);
        assertThat(connection.transaction()).isNull();
      } finally {
        connection.close().await();
      }
      assertThat(keys(relations.read("doc:plan").await())).containsExactly("doc:plan#owner@user:erin",
          "doc:plan#viewer@user:ana");
    }
  }
}
