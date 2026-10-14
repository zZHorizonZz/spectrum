# Use the OpenFGA API

The `spectrum-openfga` module implements the OpenFGA service on Spectrum. Use it to call Spectrum with OpenFGA's
requests and responses, in the same process or over the network. This page serves the API locally, over the Vert.x
event bus. No port opens, and calls in a Vert.x cluster reach the service on any node.

## Before you begin

- Create the tables. For more information, refer to [Create the tables](create-the-tables.md).
- Add `spectrum-openfga` and the event bus transport of Vert.x gRPC to your dependencies:

  ```xml
  <dependency>
    <groupId>dev.horizon</groupId>
    <artifactId>spectrum-openfga</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  <dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-grpc-eventbus</artifactId>
    <version>5.2.0</version>
  </dependency>
  ```

> [!NOTE]
> The event bus transport is a tech preview in Vert.x 5.2.

## Serve the API on the event bus

1. Create the service with your pool and dialect, and add it to an event bus server:

   ```java
   EventBusGrpcServer server = EventBusGrpcServer.server(vertx).await();
   server.addService(OpenFGAServiceGrpcService.of(new OpenFgaService(pool, dialect)));
   ```

   The server receives calls on the address `openfga.v1.OpenFGAService`.

2. Create a client on the event bus, and the OpenFGA client on top of it:

   ```java
   EventBusGrpcClient client = EventBusGrpcClient.client(vertx).await();
   OpenFGAServiceGrpcClient fga = OpenFGAServiceGrpcClient.create(client);
   ```

3. Create a store and write a model. The model uses OpenFGA's JSON format:

   ```java
   String store = fga.createStore(CreateStoreRequest.newBuilder().setName("documents").build())
       .await().getId();

   WriteAuthorizationModelRequest.Builder model = WriteAuthorizationModelRequest.newBuilder();
   JsonFormat.parser().merge("""
       {
         "schema_version": "1.1",
         "type_definitions": [
           { "type": "user" },
           {
             "type": "doc",
             "relations": { "viewer": { "this": {} } },
             "metadata": { "relations": { "viewer": { "directly_related_user_types": [{ "type": "user" }] } } }
           }
         ]
       }
       """, model);
   fga.writeAuthorizationModel(model.setStoreId(store).build()).await();
   ```

4. Write a tuple:

   ```java
   fga.write(WriteRequest.newBuilder()
       .setStoreId(store)
       .setWrites(WriteRequestWrites.newBuilder().addTupleKeys(TupleKey.newBuilder()
           .setUser("user:ana").setRelation("viewer").setObject("doc:9")))
       .build()).await();
   ```

## Verify

Check the tuple that you wrote:

```java
boolean allowed = fga.check(CheckRequest.newBuilder()
    .setStoreId(store)
    .setTupleKey(CheckRequestTupleKey.newBuilder()
        .setUser("user:ana").setRelation("viewer").setObject("doc:9"))
    .build()).await().getAllowed();
System.out.println(allowed);
```

```txt output
true
```

A failed call fails the future with an `InvalidStatusException`. Its `actualStatus()` gives the gRPC status, such as
`NOT_FOUND` for a store that does not exist.

## Next steps

- [Serve the OpenFGA API over gRPC and HTTP](serve-the-openfga-api-over-grpc-and-http.md)
- [OpenFGA compatibility](../reference/openfga-compatibility.md)
