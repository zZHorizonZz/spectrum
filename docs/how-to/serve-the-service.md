# Serve the service

`OpenFgaService` is a Vert.x gRPC service. Add it to the server that your clients reach: an event bus server for
clients in the same Vert.x instance or cluster, or a gRPC server for clients over the network. Both serve the same
service, and the generated `OpenFGAServiceGrpcClient` calls either.

## Before you begin

- Create the tables. For more information, refer to [Create the tables](create-the-tables.md).
- Create a Vert.x pool of your database, and the Cinder driver that matches it. For more information, refer to
  [Databases](../reference/databases.md).

## Serve the service over the event bus

1. Add `io.vertx:vertx-grpc-eventbus` to your dependencies.

2. Add the service to an event bus server:

   ```java
   EventBusGrpcServer server = EventBusGrpcServer.server(vertx).await();
   server.addService(OpenFgaService.create(vertx, pool, driver));
   ```

3. Call the service from any verticle of the Vert.x instance, or of the cluster:

   ```java
   OpenFGAServiceGrpcClient fga = OpenFGAServiceGrpcClient.create(EventBusGrpcClient.client(vertx).await());
   ```

## Serve the service over gRPC

1. Add the service to a gRPC server, and listen on a port:

   ```java
   GrpcServer grpc = GrpcServer.server(vertx);
   grpc.addService(OpenFgaService.create(vertx, pool, driver));
   vertx.createHttpServer().requestHandler(grpc).listen(8081).await();
   ```

2. Call the service from a gRPC client:

   ```java
   OpenFGAServiceGrpcClient fga = OpenFGAServiceGrpcClient.create(GrpcClient.client(vertx),
       SocketAddress.inetSocketAddress(8081, "localhost"));
   ```

   A client in another language uses the protos of [OpenFGA's API](https://github.com/openfga/api), as for an OpenFGA
   server.

## Change the limits

Pass options with other limits to the service:

```java
OpenFgaService service = OpenFgaService.create(vertx, pool, driver, new OpenFgaServiceOptions()
    .withMaxTuplesPerWrite(500)
    .withListObjects(5000, Duration.ofSeconds(10)));
```

For every limit and its default, refer to [Options](../reference/options.md).

## Related resources

- [OpenFGA compatibility](../reference/openfga-compatibility.md)
- [Switch to an OpenFGA server](switch-to-an-openfga-server.md)
- [Vert.x gRPC](https://vertx.io/docs/vertx-grpc/java/)
