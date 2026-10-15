package dev.horizon.spectrum.storage.sql;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.endsWith;
import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.gte;
import static dev.horizon.basepoint.Filter.in;
import static dev.horizon.basepoint.Filter.or;
import static dev.horizon.basepoint.Filter.startsWith;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.basepoint.Filter;
import dev.horizon.basepoint.Query;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.common.ULID;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.Tuple;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import dev.horizon.spectrum.openfga.v1.TupleOperation;
import dev.horizon.spectrum.storage.PageQuery;
import dev.horizon.spectrum.storage.TupleBackend;
import dev.horizon.spectrum.tuple.TupleId;
import dev.horizon.spectrum.tuple.TupleObject;
import dev.horizon.spectrum.tuple.TupleUser;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The tuples of the {@code tuple} table. A write records its changes in the {@code changelog} table, in the same
 * transaction.
 */
public final class SqlTupleBackend implements TupleBackend {

  private static final String TUPLE = "spectrum_tuple";
  private static final String CHANGELOG = "spectrum_changelog";
  private static final String[] KEY = { "store", "object_type", "object_id", "relation", "_user" };

  private static final int TUPLES_PER_STATEMENT = 100;

  private final SqlDatabase database;
  private final TupleRows rows;

  public SqlTupleBackend(SqlDatabase database) {
    this.database = database;
    this.rows = new TupleRows(database.types());
  }

  @Override
  public Future<List<Tuple>> read(String store, ReadFilter filter) {
    return tuples(database.run(select(store, filter)));
  }

  @Override
  public Future<Page<Tuple>> readPage(String store, ReadFilter filter, PageQuery page) {
    Query query = select(store, filter).orderBy("ulid").limit(page.size() + 1L);
    if (!page.from().isEmpty()) {
      query = query.where(gte("ulid", page.from()));
    }
    return database.run(query).map(found -> {
      List<Tuple> tuples = new ArrayList<>();
      List<String> next = List.of();
      for (Row row : found) {
        if (tuples.size() == page.size()) {
          next = List.of(database.types().text(row, "ulid"));
          break;
        }
        tuples.add(rows.tuple(row));
      }
      return page.page(tuples, next);
    });
  }

  @Override
  public Future<Optional<Tuple>> readUserTuple(String store, TupleId tuple) {
    return tuples(database.run(database.table(TUPLE).query().where(and(eq("store", store), matching(tuple)))))
        .map(tuples -> tuples.stream().findFirst());
  }

  @Override
  public Future<List<Tuple>> readUsersetTuples(String store, UsersetFilter filter) {
    TupleObject object = TupleObject.parse(filter.object());
    Filter where = and(eq("store", store), eq("object_type", object.type()), eq("object_id", object.id()),
        eq("relation", filter.relation()), eq("user_type", TupleUser.USERSET));
    if (!filter.allowed().isEmpty()) {
      List<Filter> allowed = new ArrayList<>();
      for (RelationReference reference : filter.allowed()) {
        allowed.add(reference.hasWildcard() ? eq("_user", reference.getType() + ":" + TupleUser.WILDCARD)
            : and(startsWith("_user", reference.getType() + ":"), endsWith("_user", "#" + reference.getRelation())));
      }
      where = and(where, or(allowed.toArray(Filter[]::new)));
    }
    return tuples(database.run(database.table(TUPLE).query().where(where)));
  }

  @Override
  public Future<List<Tuple>> readStartingWithUser(String store, UserFilter filter) {
    if (filter.users().isEmpty()) {
      return Future.succeededFuture(List.of());
    }
    return tuples(database.run(database.table(TUPLE).query().where(and(eq("store", store),
        eq("object_type", filter.objectType()), eq("relation", filter.relation()), in("_user", filter.users())))));
  }

  @Override
  public Future<Void> write(String store, List<TupleKeyWithoutCondition> deletes, List<TupleKey> writes,
      WriteOptions options) {
    if (deletes.isEmpty() && writes.isEmpty()) {
      return Future.succeededFuture();
    }
    Set<TupleId> keys = new LinkedHashSet<>();
    deletes.forEach(delete -> keys.add(new TupleId(delete)));
    writes.forEach(write -> keys.add(new TupleId(write)));
    return database.transaction(transaction -> existing(transaction, store, List.copyOf(keys)).compose(existing -> {
      List<TupleId> deleted = new ArrayList<>();
      List<Map<String, Object>> changes = new ArrayList<>();
      for (TupleKeyWithoutCondition delete : deletes) {
        TupleId key = new TupleId(delete);
        if (!existing.containsKey(key)) {
          if (options.ignoreMissingDeletes()) {
            continue;
          }
          throw invalidWrite("cannot delete a tuple which does not exist", key);
        }
        deleted.add(key);
        changes.add(
            rows.change(store, key, Optional.empty(), TupleOperation.TUPLE_OPERATION_DELETE, ULID.randomULID().toString()));
      }
      List<Map<String, Object>> inserted = new ArrayList<>();
      for (TupleKey write : writes) {
        TupleId key = new TupleId(write);
        Tuple stored = existing.get(key);
        if (stored != null) {
          if (!options.ignoreDuplicateWrites()) {
            throw invalidWrite("cannot write a tuple which already exists", key);
          }
          if (!stored.getKey().getCondition().equals(write.getCondition())) {
            throw new OpenFgaException(GrpcStatus.ABORTED, "transactional write failed due to conflict: attempted"
                + " to write a tuple which already exists with a different condition: user: '" + key.user()
                + "', relation: '" + key.relation() + "', object: '" + key.object() + "'");
          }
          continue;
        }
        inserted.add(rows.tuple(store, write, ULID.randomULID().toString()));
        changes.add(rows.change(store, key, write.hasCondition() ? Optional.of(write.getCondition())
            : Optional.empty(), TupleOperation.TUPLE_OPERATION_WRITE, ULID.randomULID().toString()));
      }
      return delete(transaction, store, deleted).compose(ignored -> insert(transaction, inserted))
          .compose(ignored -> record(transaction, changes));
    }));
  }

  private Query select(String store, ReadFilter filter) {
    Query query = database.table(TUPLE).query().where(eq("store", store));
    if (!filter.objectType().isEmpty()) {
      query = query.where(eq("object_type", filter.objectType()));
    }
    if (!filter.objectId().isEmpty()) {
      query = query.where(eq("object_id", filter.objectId()));
    }
    if (!filter.relation().isEmpty()) {
      query = query.where(eq("relation", filter.relation()));
    }
    if (!filter.user().isEmpty()) {
      query = query.where(filter.everyUserOfType() ? startsWith("_user", filter.user())
          : eq("_user", filter.user()));
    }
    return query;
  }

  private Future<Map<TupleId, Tuple>> existing(SqlClient transaction, String store, List<TupleId> keys) {
    Map<TupleId, Tuple> existing = new LinkedHashMap<>();
    return inParts(keys, part -> tuples(database.run(transaction, database.table(TUPLE).query()
        .where(and(eq("store", store), or(part.stream().map(this::matching).toArray(Filter[]::new))))))
        .map(tuples -> {
          tuples.forEach(tuple -> existing.put(new TupleId(tuple.getKey()), tuple));
          return null;
        })).map(existing);
  }

  private Future<Void> delete(SqlClient transaction, String store, List<TupleId> keys) {
    return inParts(keys, part -> database.run(transaction, database.table(TUPLE).delete().where(and(eq("store",
        store), or(part.stream().map(this::matching).toArray(Filter[]::new))))).compose(
            deleted -> deleted
                .rowCount() == part.size() ? Future.succeededFuture()
                    : Future.failedFuture(new OpenFgaException(GrpcStatus.ABORTED, "transactional write failed due to"
                        + " conflict: one or more tuples to delete were deleted by another transaction"))));
  }

  /**
   * Inserts tuples, and fails if another transaction inserted one of them first. Tuples with a condition and those
   * without go in separate statements, as do those with a context and those without, so that no statement binds a null
   * to a binary column.
   */
  private Future<Void> insert(SqlClient transaction, List<Map<String, Object>> tuples) {
    return inGroups(tuples, part -> database.run(transaction, database.table(TUPLE).insert(part).ignore(KEY))
        .compose(inserted -> inserted.rowCount() == part.size() ? Future.succeededFuture()
            : Future.failedFuture(new OpenFgaException(GrpcStatus.ABORTED, "transactional write failed due to"
                + " conflict: one or more tuples to write were inserted by another transaction"))));
  }

  private Future<Void> record(SqlClient transaction, List<Map<String, Object>> changes) {
    return inGroups(changes, part -> database.run(transaction, database.table(CHANGELOG).insert(part)).mapEmpty());
  }

  /** Runs a step on each group of rows with the same columns, in parts of {@link #TUPLES_PER_STATEMENT}. */
  private Future<Void> inGroups(List<Map<String, Object>> rows,
      Function<List<Map<String, Object>>, Future<Void>> step) {
    Map<Set<String>, List<Map<String, Object>>> groups = new LinkedHashMap<>();
    rows.forEach(row -> groups.computeIfAbsent(row.keySet(), ignored -> new ArrayList<>()).add(row));
    Future<Void> done = Future.succeededFuture();
    for (List<Map<String, Object>> group : groups.values()) {
      done = done.compose(ignored -> inParts(group, step));
    }
    return done;
  }

  /** Runs a step on each part of {@link #TUPLES_PER_STATEMENT} items, one after another. */
  private <T> Future<Void> inParts(List<T> items, Function<List<T>, Future<Void>> step) {
    Future<Void> done = Future.succeededFuture();
    for (int start = 0; start < items.size(); start += TUPLES_PER_STATEMENT) {
      List<T> part = items.subList(start, Math.min(items.size(), start + TUPLES_PER_STATEMENT));
      done = done.compose(ignored -> step.apply(part));
    }
    return done;
  }

  private Filter matching(TupleId key) {
    TupleObject object = TupleObject.parse(key.object());
    return and(eq("object_type", object.type()), eq("object_id", object.id()), eq("relation", key.relation()),
        eq("_user", key.user()));
  }

  private Future<List<Tuple>> tuples(Future<RowSet<Row>> found) {
    return found.map(set -> {
      List<Tuple> tuples = new ArrayList<>(set.size());
      set.forEach(row -> tuples.add(rows.tuple(row)));
      return tuples;
    });
  }

  private OpenFgaException invalidWrite(String reason, TupleId key) {
    return new OpenFgaException(ErrorCode.write_failed_due_to_invalid_input, reason + ": user: '" + key.user()
        + "', relation: '" + key.relation() + "', object: '" + key.object() + "': tuple to be written already"
        + " existed or the tuple to be deleted did not exist");
  }
}
