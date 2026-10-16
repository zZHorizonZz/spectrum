package dev.horizon.spectrum.storage.sql;

import dev.horizon.basepoint.Buildable;
import dev.horizon.basepoint.CollectionReference;
import dev.horizon.basepoint.Dialect;
import dev.horizon.basepoint.RenderedStatement;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;

/**
 * The database of the tables that the migrations in {@code spectrum/migration} create. Basepoint writes the statements
 * in the SQL of a dialect, and a Vert.x pool runs them.
 */
public final class SqlDatabase {

  private final Pool pool;
  private final Dialect dialect;
  private final SqlTypes types;
  private final Clock clock;

  public SqlDatabase(Pool pool, Dialect dialect, SqlTypes types, Clock clock) {
    this.pool = pool;
    this.dialect = dialect;
    this.types = types;
    this.clock = clock;
  }

  /**
   * Gives a table, to build its statements on.
   *
   * @param name the name of the table, such as {@code spectrum_tuple}
   * @return the table, in the SQL of the dialect
   */
  public CollectionReference table(String name) {
    return dialect.collection(name);
  }

  /**
   * Gives the types of the database.
   *
   * @return the values that its client binds to binary and time columns, and reads from them
   */
  public SqlTypes types() {
    return types;
  }

  /**
   * Gives the time now, to the millisecond that every database keeps.
   *
   * @return the time of the clock, without its microseconds
   */
  public Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MILLIS);
  }

  /**
   * Runs a statement on the pool.
   *
   * @param statement the statement
   * @return the rows, or a future that fails if the statement does not build or the database refuses it
   */
  public Future<RowSet<Row>> run(Buildable statement) {
    return run(pool, statement);
  }

  /**
   * Runs a statement on a client, such as the connection of a transaction.
   *
   * @param client the client
   * @param statement the statement
   * @return the rows, or a future that fails if the statement does not build or the database refuses it
   */
  public Future<RowSet<Row>> run(SqlClient client, Buildable statement) {
    RenderedStatement rendered;
    try {
      rendered = statement.build();
    } catch (RuntimeException failed) {
      return Future.failedFuture(failed);
    }
    return client.preparedQuery(rendered.text()).execute(Tuple.from(rendered.params()));
  }

  /**
   * Runs a function in a transaction, which commits when its future succeeds and rolls back when it fails.
   *
   * @param function takes the connection of the transaction, to run each statement on
   * @param <T> the type of the result
   * @return the result of the function, after the commit
   */
  public <T> Future<T> transaction(Function<SqlClient, Future<T>> function) {
    return pool.withTransaction(function::apply);
  }
}
