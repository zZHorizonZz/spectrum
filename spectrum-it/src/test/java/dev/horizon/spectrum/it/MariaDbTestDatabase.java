package dev.horizon.spectrum.it;

import dev.horizon.aquery.ParameterStyle;
import dev.horizon.basepoint.Dialect;
import dev.horizon.basepoint.mariadb.MariaDbDialect;
import io.vertx.core.Vertx;
import io.vertx.mysqlclient.MySQLBuilder;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.Pool;
import org.testcontainers.mariadb.MariaDBContainer;

final class MariaDbTestDatabase implements TestDatabase {

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
  public Dialect dialect() {
    return new MariaDbDialect(ParameterStyle.QUESTION_MARK);
  }

  @Override
  public Pool pool(Vertx vertx) {
    return MySQLBuilder.pool().connectingTo(new MySQLConnectOptions().setHost(CONTAINER.getHost())
        .setPort(CONTAINER.getMappedPort(3306)).setDatabase(CONTAINER.getDatabaseName())
        .setUser(CONTAINER.getUsername()).setPassword(CONTAINER.getPassword()).setUseAffectedRows(true))
        .using(vertx).build();
  }
}
