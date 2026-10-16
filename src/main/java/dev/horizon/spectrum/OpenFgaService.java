package dev.horizon.spectrum;

import dev.horizon.basepoint.Dialect;
import io.vertx.core.Vertx;
import io.vertx.grpc.server.Service;
import io.vertx.sqlclient.Pool;

/**
 * The OpenFGA service, {@code openfga.v1.OpenFGAService}, on the tables of a SQL database.
 *
 * <p>
 * Add the service to a Vert.x gRPC server, as you add the Vert.x gRPC health service: an {@code EventBusGrpcServer}
 * serves it to clients in the same Vert.x instance or cluster, and a {@code GrpcServer} to gRPC clients over the
 * network. Call it with an {@code OpenFGAServiceGrpcClient} on the matching client.
 *
 * <p>
 * The service stores its data in the tables that the migrations in {@code spectrum/migration} create. Basepoint writes
 * the statements in the SQL of a {@link Dialect}, and a Vert.x {@link Pool} runs them. Each write commits in its own
 * transaction before the call ends, so the consistency preference of a request changes nothing.
 */
public interface OpenFgaService extends Service {

  /**
   * Creates the service with the default limits of {@link OpenFgaServiceOptions}.
   *
   * @param vertx the Vert.x instance, for the deadlines of ListObjects and ListUsers
   * @param pool the pool that runs the statements
   * @param dialect the SQL of the database, with the placeholders that the pool takes
   * @return the service, to add to a gRPC server
   */
  static OpenFgaService create(Vertx vertx, Pool pool, Dialect dialect) {
    return create(vertx, pool, dialect, new OpenFgaServiceOptions());
  }

  /**
   * Creates the service.
   *
   * @param vertx the Vert.x instance, for the deadlines of ListObjects and ListUsers
   * @param pool the pool that runs the statements
   * @param dialect the SQL of the database, with the placeholders that the pool takes
   * @param options the limits of the service, and the key of its continuation tokens
   * @return the service, to add to a gRPC server
   */
  static OpenFgaService create(Vertx vertx, Pool pool, Dialect dialect, OpenFgaServiceOptions options) {
    return new OpenFgaServiceImpl(vertx, pool, dialect, options);
  }
}
