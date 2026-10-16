package dev.horizon.spectrum.database;

import dev.horizon.cinder.Driver;
import dev.horizon.cinder.mariadb.MariaDbDriver;
import io.vertx.core.Vertx;
import io.vertx.mysqlclient.MySQLBuilder;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.Pool;
import org.testcontainers.mariadb.MariaDBContainer;

public final class MariaDbTestDatabase implements TestDatabase {

  @Override
  public String name() {
    return "MariaDB";
  }

  private static final MariaDBContainer CONTAINER = new MariaDBContainer(
      System.getProperty("spectrum.test.mariadb.image", "mariadb:13.0"));

  @Override
  public Location start() {
    CONTAINER.start();
    return new Location(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
  }

  @Override
  public String migrations() {
    return "mariadb";
  }

  @Override
  public Driver driver() {
    return new MariaDbDriver();
  }

  @Override
  public Pool pool(Vertx vertx) {
    return MySQLBuilder.pool().connectingTo(new MySQLConnectOptions().setHost(CONTAINER.getHost())
        .setPort(CONTAINER.getMappedPort(3306)).setDatabase(CONTAINER.getDatabaseName())
        .setUser(CONTAINER.getUsername()).setPassword(CONTAINER.getPassword()).setUseAffectedRows(true)
        .setCachePreparedStatements(true))
        .using(vertx).build();
  }
}
