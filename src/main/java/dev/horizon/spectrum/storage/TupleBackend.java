package dev.horizon.spectrum.storage;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.Tuple;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import io.vertx.core.Future;
import java.util.List;

/**
 * Reads and writes the tuples of a store. A write records each tuple that it deletes or writes as a change.
 */
public interface TupleBackend extends TupleReader {

  /**
   * What a write does with a delete of a tuple that the store does not have, and with a write of one that it has.
   *
   * @param ignoreMissingDeletes true to skip a delete of a tuple that the store does not have, false to fail
   * @param ignoreDuplicateWrites true to skip a write of a tuple that the store has with the same condition, false to
   * fail
   */
  record WriteOptions(boolean ignoreMissingDeletes, boolean ignoreDuplicateWrites) {
  }

  /**
   * Reads a page of the tuples that a filter selects, in order of ULID.
   *
   * @param page the page, whose cursor is the ULID of its first tuple
   */
  Future<Page<Tuple>> readPage(String store, ReadFilter filter, PageQuery page);

  /**
   * Deletes and writes tuples in one transaction.
   *
   * @param deletes the tuples to delete
   * @param writes the tuples to write
   * @param options what to do with missing deletes and duplicate writes
   * @return a future that fails with an {@link OpenFgaException} if a delete is missing, a write is a duplicate, or
   * another transaction changed the same tuples
   */
  Future<Void> write(String store, List<TupleKeyWithoutCondition> deletes, List<TupleKey> writes,
      WriteOptions options);
}
