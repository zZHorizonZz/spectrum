package dev.horizon.spectrum.storage;

import static dev.horizon.basepoint.filter.Filter.and;
import static dev.horizon.basepoint.filter.Filter.endsWith;
import static dev.horizon.basepoint.filter.Filter.eq;
import static dev.horizon.basepoint.filter.Filter.gte;
import static dev.horizon.basepoint.filter.Filter.in;
import static dev.horizon.basepoint.filter.Filter.or;
import static dev.horizon.basepoint.filter.Filter.startsWith;

import com.google.common.collect.Lists;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.basepoint.Query;
import dev.horizon.basepoint.filter.Filter;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Conflict;
import dev.horizon.cinder.Database;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.Tuple;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.schema.v1.Tables;
import dev.horizon.spectrum.tuple.TupleId;
import dev.horizon.spectrum.tuple.TupleObject;
import dev.horizon.spectrum.tuple.TupleUser;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes the tuples of a store. The triggers of the tuple table record each tuple that a write deletes or
 * writes as a change, in the transaction of the write.
 *
 * @param database the database of the {@code tuple} table
 * @param rows the rows of the tuples
 */
public record TupleBackend(Database database, TupleRows rows)
    implements TupleReader, Backend<Tables.Tuple, Void> {

  private static final int KEYS_PER_QUERY = 100;
  private static final String[] KEY = { "store", "object_type", "object_id", "relation", "user" };

  @Override
  public Tables.Tuple prototype() {
    return Tables.Tuple.getDefaultInstance();
  }

  @Override
  public Future<List<Tuple>> read(String store, ReadFilter filter) {
    return list(select(store, filter)).map(rows::tuples);
  }

  /**
   * Reads a page of the tuples that a filter selects, in the order the database wrote them.
   *
   * @param store the ID of the store
   * @param filter the object, relation and user of the tuples
   * @param page the page, whose cursor is the ID of its first tuple
   * @return the tuples of the page, with the token of the next page
   */
  public Future<Page<Tuple>> readPage(String store, ReadFilter filter, PageQuery page) {
    Query query = select(store, filter).orderBy("id").limit(page.size() + 1L);
    if (!page.from().isEmpty()) {
      query = query.where(gte("id", Long.parseLong(page.from())));
    }
    return list(query).map(found -> found.size() > page.size()
        ? page.page(rows.tuples(found.subList(0, page.size())), List.of(String.valueOf(found.get(page
            .size()).getId())))
        : page.page(rows.tuples(found), List.of()));
  }

  @Override
  public Future<Optional<Tuple>> readUserTuple(String store, TupleId tuple) {
    return first(query().where(rows.filter(store, tuple))).map(found -> found.map(rows::tuple));
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
    return list(query().where(where)).map(rows::tuples);
  }

  @Override
  public Future<List<Tuple>> readStartingWithUser(String store, UserFilter filter) {
    if (filter.users().isEmpty()) {
      return Future.succeededFuture(List.of());
    }
    return list(query().where(and(eq("store", store), eq("object_type", filter.objectType()), eq("relation",
        filter.relation()), in("user", filter.users())))).map(rows::tuples);
  }

  /**
   * Runs a write in one transaction: it reads the tuples that the write deletes or writes, then deletes and inserts
   * one tuple at a time, in the order of the request, so that the triggers of the table record the changes in that
   * order.
   *
   * @param write the write
   * @return a future that fails with an {@link OpenFgaException} if a delete is missing, a write is a duplicate, or
   * another transaction changed the same tuples
   */
  public Future<Void> write(TupleWrite write) {
    if (write.isEmpty()) {
      return Future.succeededFuture();
    }
    String store = write.store();
    return database.transaction(transaction -> current(store, write.keys()).compose(current -> {
      Future<Void> statements = Future.succeededFuture();
      for (TupleId key : write.tuplesToDelete(current)) {
        statements = statements.compose(ignored -> deleteTuple(store, key));
      }
      for (TupleKey key : write.tuplesToInsert(current)) {
        Tables.Tuple row = rows.tuple(store, key);
        statements = statements.compose(ignored -> insertTuple(row));
      }
      return statements;
    }));
  }

  private Query select(String store, ReadFilter filter) {
    Query query = query().where(eq("store", store));
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

  /**
   * Reads the tuples of keys that the store has, {@link #KEYS_PER_QUERY} keys at a time, so that a read binds no more
   * values than SQL Server takes.
   */
  private Future<Map<TupleId, Tuple>> current(String store, List<TupleId> keys) {
    Map<TupleId, Tuple> current = new LinkedHashMap<>();
    Future<Void> reads = Future.succeededFuture();
    for (List<TupleId> part : Lists.partition(keys, KEYS_PER_QUERY)) {
      reads = reads.compose(ignored -> list(query().where(rows.filter(store, part))).map(found -> {
        rows.tuples(found).forEach(tuple -> current.put(new TupleId(tuple.getKey()), tuple));
        return null;
      }));
    }
    return reads.map(current);
  }

  /** Deletes a tuple, and fails if another transaction deleted it first. */
  private Future<Void> deleteTuple(String store, TupleId key) {
    return execute(delete().where(rows.filter(store, key))).compose(count -> count == 1 ? Future.succeededFuture()
        : Future.failedFuture(new OpenFgaException(GrpcStatus.ABORTED, "transactional write failed due to conflict:"
            + " one or more tuples to delete were deleted by another transaction")));
  }

  /** Inserts a tuple, and fails if another transaction inserted it first. */
  private Future<Void> insertTuple(Tables.Tuple row) {
    return table().execute(List.of(row), Conflict.ignore().on(KEY)).compose(count -> count == 1 ? Future.succeededFuture()
        : Future.failedFuture(new OpenFgaException(GrpcStatus.ABORTED, "transactional write failed due to conflict:"
            + " one or more tuples to write were inserted by another transaction")));
  }
}
