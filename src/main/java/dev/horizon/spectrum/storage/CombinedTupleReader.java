package dev.horizon.spectrum.storage;

import dev.horizon.spectrum.openfga.v1.Tuple;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.tuple.TupleId;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Reads the tuples of a store together with the contextual tuples of a request. A contextual tuple counts as if the
 * store had it, and comes before the stored tuples.
 */
public final class CombinedTupleReader implements TupleReader {

  private final TupleReader stored;
  private final List<Tuple> contextual;

  public CombinedTupleReader(TupleReader stored, List<TupleKey> contextual) {
    this.stored = stored;
    this.contextual = contextual.stream().map(key -> Tuple.newBuilder().setKey(key).build()).toList();
  }

  @Override
  public Future<List<Tuple>> read(String store, ReadFilter filter) {
    return combine(stored.read(store, filter), filter::matches);
  }

  @Override
  public Future<Optional<Tuple>> readUserTuple(String store, TupleId tuple) {
    Optional<Tuple> found = contextual.stream().filter(candidate -> new TupleId(candidate.getKey()).equals(tuple))
        .findFirst();
    return found.isPresent() ? Future.succeededFuture(found) : stored.readUserTuple(store, tuple);
  }

  @Override
  public Future<List<Tuple>> readUsersetTuples(String store, UsersetFilter filter) {
    return combine(stored.readUsersetTuples(store, filter), filter::matches);
  }

  @Override
  public Future<List<Tuple>> readStartingWithUser(String store, UserFilter filter) {
    return combine(stored.readStartingWithUser(store, filter), filter::matches);
  }

  private Future<List<Tuple>> combine(Future<List<Tuple>> tuples, Predicate<TupleKey> filter) {
    return tuples.map(read -> {
      List<Tuple> combined = new ArrayList<>(contextual.stream().filter(tuple -> filter.test(tuple.getKey())).toList());
      combined.addAll(read);
      return combined;
    });
  }
}
