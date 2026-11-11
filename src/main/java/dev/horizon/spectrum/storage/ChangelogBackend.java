package dev.horizon.spectrum.storage;

import static dev.horizon.basepoint.filter.Filter.eq;
import static dev.horizon.basepoint.filter.Filter.gt;
import static dev.horizon.basepoint.filter.Filter.gte;

import com.google.protobuf.Timestamp;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.basepoint.Query;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.spectrum.openfga.v1.TupleChange;
import dev.horizon.spectrum.schema.v1.Tables;
import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;

/**
 * Reads the changes of the tuples of a store: each write and delete, in the order the database recorded them. The
 * triggers of the tuple table record them, in the transaction of the change.
 *
 * @param database the database of the {@code changelog} table
 * @param rows the rows of the changes
 */
public record ChangelogBackend(Database database, TupleRows rows) implements Backend<Tables.Changelog, Long> {

  @Override
  public Tables.Changelog prototype() {
    return Tables.Changelog.getDefaultInstance();
  }

  /**
   * Reads a page of the changes of a store, in the order the database recorded them.
   *
   * @param store the ID of the store
   * @param objectType the type of the objects whose changes to read, or empty for every type
   * @param startTime the time that the changes of the first page start at, or empty for every change
   * @param page the page, whose cursor is the ID of the last change before it and the object type
   * @return the page, with no next token if it has no change
   */
  public Future<Page<TupleChange>> read(String store, String objectType, Optional<Timestamp> startTime,
      PageQuery page) {
    Query query = query().where(eq("store", store)).orderBy("id").limit(page.size());
    if (!objectType.isEmpty()) {
      query = query.where(eq("object_type", objectType));
    }
    if (!page.from().isEmpty()) {
      query = query.where(gt("id", Long.parseLong(page.from())));
    } else if (startTime.isPresent()) {
      query = query.where(gte("inserted_at", startTime.get()));
    }
    return list(query).map(found -> page.page(found.stream().map(rows::change).toList(), found.isEmpty()
        ? List.of()
        : List.of(String.valueOf(found.getLast().getId()), objectType)));
  }
}
