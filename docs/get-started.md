# Get started

In this guide, you serve the OpenFGA service on PostgreSQL over the Vert.x event bus. You then create a store, write
an authorization model and a tuple, and check a relation.

## Before you begin

- Java 25
- A PostgreSQL 13 or later database
- Flyway, to create the tables

## 1. Add the dependencies

Add Spectrum, the Cinder driver and Vert.x client of your database, and the Vert.x gRPC event bus:

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>spectrum</artifactId>
  <version>0.2.1-SNAPSHOT</version>
</dependency>
```
<!-- x-release-please-end -->

```xml
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>cinder-database-postgres</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>io.vertx</groupId>
  <artifactId>vertx-pg-client</artifactId>
  <version>5.2.0</version>
</dependency>
<dependency>
  <groupId>io.vertx</groupId>
  <artifactId>vertx-grpc-eventbus</artifactId>
  <version>5.2.0</version>
</dependency>
```

On the module path, Spectrum is the module `dev.horizon.spectrum`. It exports `dev.horizon.spectrum` and the
messages and client of `dev.horizon.spectrum.openfga.v1`:

```java
module com.example.app {
  requires dev.horizon.spectrum;
  requires dev.horizon.cinder.postgres;
  requires io.vertx.grpc.eventbus;
  requires io.vertx.sql.client.pg;
}
```

The JAR contains cel-java, protoc-gen-validate, RE2/J and the Google API annotations under
`dev.horizon.spectrum.shaded`, so they need no modules of their own.

## 2. Create the tables

Run the migrations of Spectrum with their own history table:

```java
Flyway.configure()
    .dataSource("jdbc:postgresql://localhost:5432/app", "app", "secret")
    .locations("classpath:spectrum/migration/**/postgresql")
    .table("spectrum_schema_history")
    .baselineOnMigrate(true)
    .load()
    .migrate();
```

For more information, refer to [Create the tables](how-to/create-the-tables.md).

## 3. Serve the service

Create the service on a pool, and add it to an event bus gRPC server:

```java
Vertx vertx = Vertx.vertx();
Pool pool = PgBuilder.pool().connectingTo("postgresql://app:secret@localhost:5432/app").using(vertx).build();

EventBusGrpcServer server = EventBusGrpcServer.server(vertx).await();
server.addService(OpenFgaService.create(vertx, pool, new PostgresDriver()));
```

## 4. Call the service

1. Create a client on the event bus:

   ```java
   OpenFGAServiceGrpcClient fga = OpenFGAServiceGrpcClient.create(EventBusGrpcClient.client(vertx).await());
   ```

2. Create a store:

   ```java
   String store = fga.createStore(CreateStoreRequest.newBuilder().setName("documents").build()).await().getId();
   ```

3. Write an authorization model. A `doc` has viewers, which are users:

   ```java
   WriteAuthorizationModelRequest.Builder model = WriteAuthorizationModelRequest.newBuilder().setStoreId(store);
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
   fga.writeAuthorizationModel(model.build()).await();
   ```

4. Write a tuple: `user:anne` is a viewer of `doc:roadmap`.

   ```java
   fga.write(WriteRequest.newBuilder().setStoreId(store).setWrites(WriteRequestWrites.newBuilder()
       .addTupleKeys(TupleKey.newBuilder().setUser("user:anne").setRelation("viewer").setObject("doc:roadmap")))
       .build()).await();
   ```

5. Check the relation:

   ```java
   boolean allowed = fga.check(CheckRequest.newBuilder().setStoreId(store).setTupleKey(CheckRequestTupleKey
       .newBuilder().setUser("user:anne").setRelation("viewer").setObject("doc:roadmap")).build()).await()
       .getAllowed();
   ```

   `allowed` is `true`. A check of `user:beth` is `false`.

## Next steps

- [Switch to an OpenFGA server](how-to/switch-to-an-openfga-server.md) when the application outgrows Spectrum.
- [Serve the service](how-to/serve-the-service.md) to gRPC clients over the network.
- [Conditions](concepts/conditions.md) add CEL expressions to tuples.
- [OpenFGA compatibility](reference/openfga-compatibility.md) lists what Spectrum does as OpenFGA does, and what it
  does not.
