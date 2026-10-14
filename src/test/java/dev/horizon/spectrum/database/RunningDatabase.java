package dev.horizon.spectrum.database;

import dev.horizon.basepoint.Dialect;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import java.util.List;

public record RunningDatabase(Vertx vertx, Pool pool, Dialect dialect) implements AutoCloseable {

  private static final List<String> TABLES = List.of("tuple", "changelog", "assertion", "authorization_model",
      "store");

  public void empty() {
    for (String table : TABLES) {
      pool.query("DELETE FROM " + table).execute().await();
    }
  }

  @Override
  public void close() {
    pool.close().await();
    vertx.close().await();
  }
}
