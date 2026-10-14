package dev.horizon.spectrum.it;

import dev.horizon.basepoint.Dialect;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;

interface TestDatabase {

  record Location(String url, String user, String password) {
  }

  Location start();

  String migrations();

  Dialect dialect();

  Pool pool(Vertx vertx);
}
