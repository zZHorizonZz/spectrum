package dev.horizon.spectrum.it;

import dev.horizon.aquery.ParameterStyle;
import dev.horizon.basepoint.Dialect;
import dev.horizon.basepoint.sqlite.SqliteDialect;
import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class SqliteTestDatabase implements TestDatabase {

  private final String url;

  SqliteTestDatabase() {
    try {
      Path file = Files.createTempFile("spectrum", ".db");
      file.toFile().deleteOnExit();
      this.url = "jdbc:sqlite:" + file;
    } catch (IOException failed) {
      throw new UncheckedIOException(failed);
    }
  }

  @Override
  public Location start() {
    return new Location(url, null, null);
  }

  @Override
  public String migrations() {
    return "sqlite";
  }

  @Override
  public Dialect dialect() {
    return new SqliteDialect(ParameterStyle.QUESTION_MARK);
  }

  @Override
  public Pool pool(Vertx vertx) {
    return JDBCPool.pool(vertx, new JDBCConnectOptions().setJdbcUrl(url), new PoolOptions().setMaxSize(1));
  }
}
