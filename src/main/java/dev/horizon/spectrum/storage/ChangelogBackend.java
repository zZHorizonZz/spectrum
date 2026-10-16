package dev.horizon.spectrum.storage;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.spectrum.openfga.v1.TupleChange;
import io.vertx.core.Future;

/**
 * Reads the changes of the tuples of a store: each write and delete, in the order the writes made them.
 */
public interface ChangelogBackend {

  /**
   * Reads a page of the changes of a store, in order of ULID.
   *
   * @param store the ID of the store
   * @param objectType the type of the objects whose changes to read, or empty for every type
   * @param after the ULID that the changes of the page come after, or empty for the first page
   * @param page the page, whose cursor is the ULID of the last change before it and the object type
   * @return the page, with no next token if it has no change
   */
  Future<Page<TupleChange>> read(String store, String objectType, String after, PageQuery page);
}
