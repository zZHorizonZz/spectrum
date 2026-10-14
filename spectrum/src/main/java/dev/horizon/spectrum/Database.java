package dev.horizon.spectrum;

import com.google.protobuf.Timestamp;
import dev.horizon.basepoint.Buildable;
import dev.horizon.basepoint.CollectionReference;
import dev.horizon.basepoint.Dialect;
import dev.horizon.basepoint.RenderedStatement;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.Function;

/**
 * The tables of Spectrum in a database: basepoint writes the statements in the SQL of a dialect, and a Vert.x client
 * runs them.
 */
final class Database {

  private final SqlClient client;
  private final Dialect dialect;

  Database(SqlClient client, Dialect dialect) {
    this.client = client;
    this.dialect = dialect;
  }

  /** Gives the same database through another client. */
  Database on(SqlClient other) {
    return new Database(other, dialect);
  }

  /** Starts a statement of a table. */
  CollectionReference table(String name) {
    return dialect.collection(name);
  }

  /** Runs a statement, and gives its rows. */
  Future<RowSet<Row>> rows(Buildable statement) {
    RenderedStatement rendered = statement.build();
    return client.preparedQuery(rendered.text()).execute(Tuple.from(rendered.params()));
  }

  /** Runs a write, and gives the number of rows it wrote. */
  Future<Long> count(Buildable statement) {
    return rows(statement).map(rows -> (long) rows.rowCount());
  }

  /**
   * Runs work in one transaction: a new one on a connection of a pool, the one a connection is in, or a new one on the
   * connection.
   */
  <T> Future<T> inTransaction(Function<Database, Future<T>> work) {
    if (client instanceof Pool pool) {
      return pool.withTransaction(connection -> work.apply(on(connection)));
    }
    if (client instanceof SqlConnection connection && connection.transaction() == null) {
      return connection.begin().compose(transaction -> work.apply(this).compose(
          done -> transaction.commit().map(done),
          failed -> transaction.rollback().transform(ignored -> Future.<T> failedFuture(failed))));
    }
    return work.apply(this);
  }

  /** Reads the value of a column, or null if the row has no such column. */
  Object value(Row row, String column) {
    int index = row.getColumnIndex(column);
    return index < 0 ? null : row.getValue(index);
  }

  /** Reads a text column, or the empty text. */
  String text(Row row, String column) {
    Object value = value(row, column);
    return value == null ? "" : value.toString();
  }

  /**
   * Reads a time column: a database without a time zone keeps it in UTC, and SQLite as ISO 8601 text.
   *
   * @return the time, or null if the row has no such column
   */
  Timestamp timestamp(Row row, String column) {
    Object value = value(row, column);
    if (value == null) {
      return null;
    }
    Instant instant = switch (value) {
      case OffsetDateTime time -> time.toInstant();
      case LocalDateTime time -> time.toInstant(ZoneOffset.UTC);
      case String text -> Instant.parse(text);
      default -> throw new IllegalStateException("the column " + column + " is a " + value.getClass().getName());
    };
    return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
  }
}
