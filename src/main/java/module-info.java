/**
 * The OpenFGA service on Vert.x and a SQL database. The JAR contains cel-java, protoc-gen-validate, RE2/J and the
 * Google API annotations under {@code dev.horizon.spectrum.shaded}, so that they need no modules of their own.
 */
module dev.horizon.spectrum {
  requires transitive com.google.protobuf;
  requires transitive dev.horizon.basepoint;
  requires transitive io.vertx.core;
  requires transitive io.vertx.grpc.client;
  requires transitive io.vertx.grpc.common;
  requires transitive io.vertx.grpc.server;
  requires transitive io.vertx.sql.client;
  requires com.google.common;
  requires dev.horizon.aquery.api;
  requires com.google.protobuf.util;

  exports dev.horizon.spectrum;
  exports dev.horizon.spectrum.openfga.v1;
}
