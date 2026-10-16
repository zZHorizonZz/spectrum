package dev.horizon.spectrum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import com.google.protobuf.TextFormat;
import com.google.protobuf.util.JsonFormat;
import dev.horizon.spectrum.database.RunningDatabase;
import dev.horizon.spectrum.openfga.v1.CreateStoreRequest;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import dev.horizon.spectrum.openfga.v1.RelationshipCondition;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import dev.openfga.language.DslToJsonTransformer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

final class Spectrum implements AutoCloseable {

  private static final Duration DEADLINE = Duration.ofSeconds(30);

  private final RunningDatabase database;
  private EventBusGrpcServer server;
  private EventBusGrpcClient client;
  private OpenFGAServiceGrpcClient fga;

  Spectrum(RunningDatabase database) {
    this.database = database;
    database.empty();
    serve(new OpenFgaServiceOptions());
  }

  OpenFGAServiceGrpcClient serve(OpenFgaServiceOptions options) {
    close();
    server = EventBusGrpcServer.server(database.vertx()).await();
    server.addService(OpenFgaService.create(database.vertx(), database.pool(), database.driver(), options));
    client = EventBusGrpcClient.client(database.vertx()).await();
    fga = deadlined(OpenFGAServiceGrpcClient.create(client));
    return fga;
  }

  OpenFGAServiceGrpcClient client() {
    return fga;
  }

  Vertx vertx() {
    return database.vertx();
  }

  TestStore store() {
    return new TestStore(this, fga.createStore(CreateStoreRequest.newBuilder().setName("spectrum").build()).await()
        .getId());
  }

  TestStore store(String dsl) {
    TestStore store = store();
    store.writeModel(dsl);
    return store;
  }

  TestStore documents() {
    TestStore store = store(Documents.MODEL);
    store.write(Documents.TUPLES);
    return store;
  }

  WriteAuthorizationModelRequest model(String store, String dsl) {
    WriteAuthorizationModelRequest.Builder model = WriteAuthorizationModelRequest.newBuilder();
    try {
      JsonFormat.parser().ignoringUnknownFields().merge(new DslToJsonTransformer().transform(dsl), model);
    } catch (Exception invalid) {
      throw new IllegalArgumentException(dsl, invalid);
    }
    return model.setStoreId(store).build();
  }

  TupleKey tuple(String text) {
    int hash = text.indexOf('#');
    int at = text.indexOf('@');
    return TupleKey.newBuilder().setObject(text.substring(0, hash)).setRelation(text.substring(hash + 1, at))
        .setUser(text.substring(at + 1)).build();
  }

  TupleKey tuple(String text, String condition, String context) {
    return tuple(text).toBuilder().setCondition(RelationshipCondition.newBuilder().setName(condition)
        .setContext(struct(context))).build();
  }

  String text(TupleKey tuple) {
    return tuple.getObject() + "#" + tuple.getRelation() + "@" + tuple.getUser();
  }

  Struct struct(String json) {
    Struct.Builder struct = Struct.newBuilder();
    try {
      JsonFormat.parser().merge(json == null || json.isBlank() ? "{}" : json, struct);
    } catch (InvalidProtocolBufferException invalid) {
      throw new IllegalArgumentException(json, invalid);
    }
    return struct.build();
  }

  void assertStatus(GrpcStatus status, Supplier<Future<?>> call) {
    assertThatExceptionOfType(InvalidStatusException.class).isThrownBy(() -> call.get().await())
        .satisfies(failed -> assertThat(failed.actualStatus()).isEqualTo(status));
  }

  /**
   * Fails a future that does not complete before the deadline, so that a call that never answers fails its test and
   * names the call, in place of waiting forever.
   */
  static <T> Future<T> deadline(Future<T> future, String call) {
    return future.timeout(DEADLINE).recover(failed -> Future.failedFuture(failed instanceof TimeoutException
        ? new AssertionError(call + " gave no answer in " + DEADLINE.toSeconds() + " s", failed)
        : failed));
  }

  /** Gives a client whose every call has the deadline. */
  private static OpenFGAServiceGrpcClient deadlined(OpenFGAServiceGrpcClient client) {
    return (OpenFGAServiceGrpcClient) Proxy.newProxyInstance(OpenFGAServiceGrpcClient.class.getClassLoader(),
        new Class<?>[] { OpenFGAServiceGrpcClient.class }, (proxy, method, arguments) -> {
          Object result;
          try {
            result = method.invoke(client, arguments);
          } catch (InvocationTargetException failed) {
            throw failed.getCause();
          }
          return result instanceof Future<?> future ? deadline(future, method.getName() + " " + (arguments != null
              && arguments.length == 1 && arguments[0] instanceof Message message ? TextFormat.shortDebugString(message)
                  : Arrays.toString(arguments)))
              : result;
        });
  }

  @Override
  public void close() {
    if (server != null) {
      client.close().await();
      server.close().await();
    }
  }
}
