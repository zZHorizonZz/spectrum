package dev.horizon.spectrum.database;

import dev.horizon.cinder.Driver;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import org.flywaydb.core.Flyway;

public interface TestDatabase {

  record Location(String url, String user, String password) {
  }

  String name();

  Location start();

  String migrations();

  Driver driver();

  Pool pool(Vertx vertx);

  default RunningDatabase open() {
    Location location = start();
    Flyway.configure().dataSource(location.url(), location.user(), location.password())
        .locations("classpath:spectrum/migration/**/" + migrations()).table("spectrum_schema_history")
        .baselineOnMigrate(true).load().migrate();
    Vertx vertx = Vertx.vertx();
    return new RunningDatabase(vertx, pool(vertx), driver());
  }
}
