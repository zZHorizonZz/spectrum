package dev.horizon.spectrum.database;

import dev.horizon.cinder.Driver;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

public record RunningDatabase(Vertx vertx, Pool pool, Driver driver) implements AutoCloseable {

  public static final Duration TIMEOUT = Duration.ofSeconds(10);

  private static final List<String> TABLES = List.of("spectrum_tuple", "spectrum_changelog",
      "spectrum_assertion", "spectrum_authorization_model", "spectrum_store");

  public void empty() throws TimeoutException {
    for (String table : TABLES) {
      pool.query("DELETE FROM " + table).execute().await(TIMEOUT);
    }
  }

  @Override
  public void close() throws TimeoutException {
    pool.close().await(TIMEOUT);
    vertx.close().await(TIMEOUT);
  }
}
