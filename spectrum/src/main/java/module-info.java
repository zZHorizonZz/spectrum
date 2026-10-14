module dev.horizon.spectrum {
  requires transitive dev.horizon.basepoint;
  requires transitive com.google.protobuf;
  requires transitive io.vertx.core;
  requires transitive io.vertx.sql.client;
  requires com.google.protobuf.util;
  requires dev.horizon.leadseal.protobuf;

  exports dev.horizon.spectrum;
  exports dev.horizon.spectrum.v1;
}
