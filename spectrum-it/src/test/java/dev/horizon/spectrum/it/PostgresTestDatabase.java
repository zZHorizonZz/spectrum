package dev.horizon.spectrum.it;

import dev.horizon.aquery.ParameterStyle;
import dev.horizon.basepoint.Dialect;
import dev.horizon.basepoint.postgres.PostgresDialect;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import org.testcontainers.postgresql.PostgreSQLContainer;

final class PostgresTestDatabase implements TestDatabase {

  private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer(
      System.getProperty("spectrum.test.postgres.image", "postgres:17-alpine"));

  @Override
  public Location start() {
    CONTAINER.start();
    return new Location(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
  }

  @Override
  public String migrations() {
    return "postgresql";
  }

  @Override
  public Dialect dialect() {
    return new PostgresDialect(ParameterStyle.DOLLAR);
  }

  @Override
  public Pool pool(Vertx vertx) {
    return PgBuilder.pool().connectingTo(new PgConnectOptions().setHost(CONTAINER.getHost())
        .setPort(CONTAINER.getMappedPort(5432)).setDatabase(CONTAINER.getDatabaseName())
        .setUser(CONTAINER.getUsername()).setPassword(CONTAINER.getPassword())).using(vertx).build();
  }
}
