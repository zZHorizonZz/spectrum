package dev.horizon.spectrum.storage;

import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.Tuple;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import dev.horizon.spectrum.tuple.TupleId;
import io.vertx.grpc.common.GrpcStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A write of the tuples of a store: the tuples that it deletes, those that it writes, and what it does with a delete of
 * a tuple that the store does not have and a write of one that it has. Against the tuples that the store has, it
 * gives the tuples to delete and to insert, or fails as OpenFGA does.
 */
public final class TupleWrite {

  private final String store;
  private final List<TupleKeyWithoutCondition> deletes;
  private final List<TupleKey> writes;
  private final boolean ignoreMissingDeletes;
  private final boolean ignoreDuplicateWrites;

  /**
   * Starts a write.
   *
   * @param store the ID of the store
   * @param deletes the tuples to delete
   * @param writes the tuples to write
   * @param ignoreMissingDeletes true to skip a delete of a tuple that the store does not have, false to fail
   * @param ignoreDuplicateWrites true to skip a write of a tuple that the store has with the same condition, false to
   * fail
   */
  public TupleWrite(String store, List<TupleKeyWithoutCondition> deletes, List<TupleKey> writes,
      boolean ignoreMissingDeletes, boolean ignoreDuplicateWrites) {
    this.store = store;
    this.deletes = List.copyOf(deletes);
    this.writes = List.copyOf(writes);
    this.ignoreMissingDeletes = ignoreMissingDeletes;
    this.ignoreDuplicateWrites = ignoreDuplicateWrites;
  }

  String store() {
    return store;
  }

  boolean isEmpty() {
    return deletes.isEmpty() && writes.isEmpty();
  }

  /**
   * Gives the tuples that the write deletes or writes.
   */
  List<TupleId> keys() {
    Set<TupleId> keys = new LinkedHashSet<>();
    deletes.forEach(delete -> keys.add(new TupleId(delete)));
    writes.forEach(write -> keys.add(new TupleId(write)));
    return List.copyOf(keys);
  }

  /**
   * Gives the tuples to delete: those of the deletes that the store has, in the order of the request.
   *
   * @param current the tuples of the keys that the store has
   * @throws OpenFgaException if the store does not have one, and the write does not skip such deletes
   */
  List<TupleId> tuplesToDelete(Map<TupleId, Tuple> current) {
    List<TupleId> tuples = new ArrayList<>();
    for (TupleKeyWithoutCondition delete : deletes) {
      TupleId key = new TupleId(delete);
      if (current.containsKey(key)) {
        tuples.add(key);
      } else if (!ignoreMissingDeletes) {
        throw invalid("cannot delete a tuple which does not exist", key);
      }
    }
    return tuples;
  }

  /**
   * Gives the tuples to insert: those of the writes that the store does not have, in the order of the request.
   *
   * @param current the tuples of the keys that the store has
   * @throws OpenFgaException if the store has one, and the write does not skip such writes or the store has it with
   * another condition
   */
  List<TupleKey> tuplesToInsert(Map<TupleId, Tuple> current) {
    List<TupleKey> tuples = new ArrayList<>();
    for (TupleKey write : writes) {
      TupleId key = new TupleId(write);
      Tuple tuple = current.get(key);
      if (tuple == null) {
        tuples.add(write);
      } else if (!ignoreDuplicateWrites) {
        throw invalid("cannot write a tuple which already exists", key);
      } else if (!tuple.getKey().getCondition().equals(write.getCondition())) {
        throw new OpenFgaException(GrpcStatus.ABORTED, "transactional write failed due to conflict: attempted to"
            + " write a tuple which already exists with a different condition: user: '" + key.user()
            + "', relation: '" + key.relation() + "', object: '" + key.object() + "'");
      }
    }
    return tuples;
  }

  private static OpenFgaException invalid(String reason, TupleId key) {
    return new OpenFgaException(ErrorCode.write_failed_due_to_invalid_input, reason + ": user: '" + key.user()
        + "', relation: '" + key.relation() + "', object: '" + key.object() + "': tuple to be written already"
        + " existed or the tuple to be deleted did not exist");
  }
}
