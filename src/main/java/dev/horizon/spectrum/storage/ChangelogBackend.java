package dev.horizon.spectrum.storage;

import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.gt;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.Query;
import dev.horizon.spectrum.openfga.v1.TupleChange;
import dev.horizon.spectrum.schema.v1.Tables;
import io.vertx.core.Future;
import java.util.List;

/**
 * Reads the changes of the tuples of a store: each write and delete, in the order the writes made them. A write of the
 * {@link TupleBackend} records them in its transaction.
 *
 * @param database the database of the {@code changelog} table
 * @param rows the rows of the changes
 */
public record ChangelogBackend(Database database, TupleRows rows)
    implements Backend<Tables.Changelog, Void> {

  @Override
  public Tables.Changelog prototype() {
    return Tables.Changelog.getDefaultInstance();
  }

  /**
   * Reads a page of the changes of a store, in order of ULID.
   *
   * @param store the ID of the store
   * @param objectType the type of the objects whose changes to read, or empty for every type
   * @param after the ULID that the changes of the page come after, or empty for the first page
   * @param page the page, whose cursor is the ULID of the last change before it and the object type
   * @return the page, with no next token if it has no change
   */
  public Future<Page<TupleChange>> read(String store, String objectType, String after, PageQuery page) {
    Query<Tables.Changelog> query = find(eq("store", store)).orderBy("ulid").limit(page.size());
    if (!objectType.isEmpty()) {
      query = query.where(eq("object_type", objectType));
    }
    if (!after.isEmpty()) {
      query = query.where(gt("ulid", after));
    }
    return query.list().map(found -> page.page(found.stream().map(rows::change).toList(), found.isEmpty()
        ? List.of()
        : List.of(found.getLast().getUlid(), objectType)));
  }
}
