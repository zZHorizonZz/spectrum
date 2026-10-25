package dev.horizon.spectrum.storage;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.endsWith;
import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.gte;
import static dev.horizon.basepoint.Filter.in;
import static dev.horizon.basepoint.Filter.or;
import static dev.horizon.basepoint.Filter.startsWith;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.basepoint.Filter;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Conflict;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.Query;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.common.ULID;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.Tuple;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import dev.horizon.spectrum.openfga.v1.TupleOperation;
import dev.horizon.spectrum.schema.v1.Tables;
import dev.horizon.spectrum.tuple.TupleId;
import dev.horizon.spectrum.tuple.TupleObject;
import dev.horizon.spectrum.tuple.TupleUser;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Reads and writes the tuples of a store. A write records each tuple that it deletes or writes as a change, in the
 * same transaction.
 *
 * @param database the database of the {@code tuple} table
 * @param changelog the changes that the writes record
 * @param rows the rows of the tuples and the changes
 */
public record TupleBackend(Database database, ChangelogBackend changelog, TupleRows rows)
    implements TupleReader, Backend<Tables.Tuple, Void> {

  private static final int TUPLES_PER_STATEMENT = 100;

  @Override
  public Tables.Tuple prototype() {
    return Tables.Tuple.getDefaultInstance();
  }

  @Override
  public Future<List<Tuple>> read(String store, ReadFilter filter) {
    return tuples(select(store, filter));
  }

  /**
   * Reads a page of the tuples that a filter selects, in order of ULID.
   *
   * @param store the ID of the store
   * @param filter the object, relation and user of the tuples
   * @param page the page, whose cursor is the ULID of its first tuple
   * @return the tuples of the page, with the token of the next page
   */
  public Future<Page<Tuple>> readPage(String store, ReadFilter filter, PageQuery page) {
    Query<Tables.Tuple> query = select(store, filter).orderBy("ulid")
        .limit(page.size() + 1L);
    if (!page.from().isEmpty()) {
      query = query.where(gte("ulid", page.from()));
    }
    return query.list().map(found -> found.size() > page.size()
        ? page.page(found.subList(0, page.size()).stream().map(rows::tuple).toList(), List.of(found.get(page.size())
            .getUlid()))
        : page.page(found.stream().map(rows::tuple).toList(), List.of()));
  }

  @Override
  public Future<Optional<Tuple>> readUserTuple(String store, TupleId tuple) {
    return find(key(store, tuple)).first().map(found -> found.map(rows::tuple));
  }

  @Override
  public Future<List<Tuple>> readUsersetTuples(String store, UsersetFilter filter) {
    TupleObject object = TupleObject.parse(filter.object());
    Filter where = and(eq("store", store), eq("object_type", object.type()), eq("object_id", object.id()),
        eq("relation", filter.relation()), eq("user_type", TupleUser.USERSET));
    if (!filter.allowed().isEmpty()) {
      List<Filter> allowed = new ArrayList<>();
      for (RelationReference reference : filter.allowed()) {
        allowed.add(reference.hasWildcard() ? eq("user", reference.getType() + ":" + TupleUser.WILDCARD)
            : and(startsWith("user", reference.getType() + ":"), endsWith("user", "#" + reference.getRelation())));
      }
      where = and(where, or(allowed.toArray(Filter[]::new)));
    }
    return tuples(find(where));
  }

  @Override
  public Future<List<Tuple>> readStartingWithUser(String store, UserFilter filter) {
    if (filter.users().isEmpty()) {
      return Future.succeededFuture(List.of());
    }
    return tuples(find(and(eq("store", store), eq("object_type", filter.objectType()), eq("relation",
        filter.relation()), in("user", filter.users()))));
  }

  /**
   * Deletes and writes tuples in one transaction, and records a change for each.
   *
   * @param store the ID of the store
   * @param deletes the tuples to delete
   * @param writes the tuples to write
   * @param options what to do with missing deletes and duplicate writes
   * @return a future that fails with an {@link OpenFgaException} if a delete is missing, a write is a duplicate, or
   * another transaction changed the same tuples
   */
  public Future<Void> write(String store, List<TupleKeyWithoutCondition> deletes, List<TupleKey> writes,
      WriteOptions options) {
    if (deletes.isEmpty() && writes.isEmpty()) {
      return Future.succeededFuture();
    }
    Set<TupleId> keys = new LinkedHashSet<>();
    deletes.forEach(delete -> keys.add(new TupleId(delete)));
    writes.forEach(write -> keys.add(new TupleId(write)));
    return database.transaction(transaction -> existing(store, List.copyOf(keys)).compose(existing -> {
      List<TupleId> deleted = new ArrayList<>();
      List<Tables.Changelog> changes = new ArrayList<>();
      for (TupleKeyWithoutCondition delete : deletes) {
        TupleId key = new TupleId(delete);
        if (!existing.containsKey(key)) {
          if (options.ignoreMissingDeletes()) {
            continue;
          }
          throw invalidWrite("cannot delete a tuple which does not exist", key);
        }
        deleted.add(key);
        changes.add(rows.change(store, key, Optional.empty(), TupleOperation.TUPLE_OPERATION_DELETE, ULID
            .randomULID().toString()));
      }
      List<Tables.Tuple> inserted = new ArrayList<>();
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
      return delete(store, deleted).compose(ignored -> insert(inserted)).compose(ignored -> record(changes));
    }));
  }

  private Query<Tables.Tuple> select(String store, ReadFilter filter) {
    Query<Tables.Tuple> query = find(eq("store", store));
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
      query = query.where(filter.everyUserOfType() ? startsWith("user", filter.user()) : eq("user", filter.user()));
    }
    return query;
  }

  private Future<Map<TupleId, Tuple>> existing(String store, List<TupleId> keys) {
    Map<TupleId, Tuple> existing = new LinkedHashMap<>();
    return inParts(keys, part -> tuples(find(byKeys(store, part))).map(tuples -> {
      tuples.forEach(tuple -> existing.put(new TupleId(tuple.getKey()), tuple));
      return null;
    })).map(existing);
  }

  private Future<Void> delete(String store, List<TupleId> keys) {
    return inParts(keys, part -> delete().where(byKeys(store, part)).execute().compose(deleted -> deleted == part
        .size() ? Future.succeededFuture()
            : Future.failedFuture(new OpenFgaException(GrpcStatus.ABORTED, "transactional write failed due to"
                + " conflict: one or more tuples to delete were deleted by another transaction"))));
  }

  /** Inserts tuples, and fails if another transaction inserted one of them first. */
  private Future<Void> insert(List<Tables.Tuple> tuples) {
    return inParts(tuples, part -> insert().values(part).onConflict(Conflict.ignore()).execute()
        .compose(inserted -> inserted == part.size() ? Future.succeededFuture()
            : Future.failedFuture(new OpenFgaException(GrpcStatus.ABORTED, "transactional write failed due to"
                + " conflict: one or more tuples to write were inserted by another transaction"))));
  }

  private Future<Void> record(List<Tables.Changelog> changes) {
    return inParts(changes, part -> changelog.insert().values(part).execute().mapEmpty());
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

  private Filter byKeys(String store, List<TupleId> keys) {
    return or(keys.stream().map(key -> key(store, key)).toArray(Filter[]::new));
  }

  private Filter key(String store, TupleId tuple) {
    TupleObject object = TupleObject.parse(tuple.object());
    return and(eq("store", store), eq("object_type", object.type()), eq("object_id", object.id()),
        eq("relation", tuple.relation()), eq("user", tuple.user()));
  }

  private Future<List<Tuple>> tuples(Query<Tables.Tuple> query) {
    return query.list().map(found -> found.stream().map(rows::tuple).toList());
  }

  private OpenFgaException invalidWrite(String reason, TupleId key) {
    return new OpenFgaException(ErrorCode.write_failed_due_to_invalid_input, reason + ": user: '" + key.user()
        + "', relation: '" + key.relation() + "', object: '" + key.object() + "': tuple to be written already"
        + " existed or the tuple to be deleted did not exist");
  }

  /**
   * What a write does with a delete of a tuple that the store does not have, and with a write of one that it has.
   *
   * @param ignoreMissingDeletes true to skip a delete of a tuple that the store does not have, false to fail
   * @param ignoreDuplicateWrites true to skip a write of a tuple that the store has with the same condition, false to
   * fail
   */
  public record WriteOptions(boolean ignoreMissingDeletes, boolean ignoreDuplicateWrites) {
  }
}
