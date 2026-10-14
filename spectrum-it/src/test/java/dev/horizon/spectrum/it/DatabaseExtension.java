package dev.horizon.spectrum.it;

import dev.horizon.basepoint.Dialect;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

final class DatabaseExtension implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {

  private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(
      DatabaseExtension.class);

  private static final List<String> TABLES = List.of("spectrum_relationship", "spectrum_change",
      "spectrum_model_assertions", "spectrum_store_model", "spectrum_store");

  private final TestDatabase database;

  DatabaseExtension(TestDatabase database) {
    this.database = database;
  }

  private record Open(Vertx vertx, Pool pool) implements AutoCloseable {

    @Override
    public void close() {
      pool.close().await();
      vertx.close().await();
    }
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    ExtensionContext.Store store = context.getStore(NAMESPACE);
    if (store.get(Open.class) != null) {
      return;
    }
    TestDatabase.Location location = database.start();
    Flyway.configure().dataSource(location.url(), location.user(), location.password())
        .locations("classpath:spectrum/migration/**/" + database.migrations())
        .table("spectrum_schema_history").baselineOnMigrate(true).load().migrate();
    Vertx vertx = Vertx.vertx();
    store.put(Open.class, new Open(vertx, database.pool(vertx)));
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    Pool pool = open(context).pool();
    for (String table : TABLES) {
      pool.query("DELETE FROM " + table).execute().await();
    }
  }

  @Override
  public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
    Class<?> type = parameter.getParameter().getType();
    return type == Pool.class || type == Dialect.class || type == Vertx.class;
  }

  @Override
  public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
    Class<?> type = parameter.getParameter().getType();
    return type == Pool.class ? open(context).pool() : type == Vertx.class ? open(context).vertx() : database.dialect();
  }

  private static Open open(ExtensionContext context) {
    return context.getStore(NAMESPACE).get(Open.class, Open.class);
  }
}
