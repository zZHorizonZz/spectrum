# Spectrum

Spectrum is the OpenFGA service for Vert.x. It serves OpenFGA's gRPC API, `openfga.v1.OpenFGAService`, from the
tables of your own PostgreSQL, MariaDB, SQL Server or SQLite database. It follows the code and the behavior of
[OpenFGA](https://github.com/openfga/openfga): the same checks, lists, validation rules and limits.

```java
EventBusGrpcServer server = EventBusGrpcServer.server(vertx).await();
server.addService(OpenFgaService.create(vertx, pool, new PostgresDriver()));

OpenFGAServiceGrpcClient fga = OpenFGAServiceGrpcClient.create(EventBusGrpcClient.client(vertx).await());
fga.check(CheckRequest.newBuilder().setStoreId(store).setTupleKey(CheckRequestTupleKey.newBuilder()
    .setUser("user:anne").setRelation("viewer").setObject("doc:roadmap")).build())
    .onSuccess(response -> System.out.println(response.getAllowed()));
```

## When to use Spectrum

Spectrum is not a replacement for an OpenFGA server. Use it for:

- **Tests:** run OpenFGA's API in process, against the database of the test, with no server to start.
- **Small applications:** serve authorization from the database that the application already has, in the same
  Vert.x instance.

An OpenFGA server caches checks, plans its queries, scales on its own and serves the HTTP API of the OpenFGA SDKs.
Spectrum does none of that. Because both serve the same gRPC API, an application can move to a standalone OpenFGA
server when it needs one: point the client at the server, and move the data. For more information, refer to
[Switch to an OpenFGA server](docs/how-to/switch-to-an-openfga-server.md).

## Features

- **OpenFGA's API.** Every call of `openfga.v1.OpenFGAService`, with OpenFGA's request validation, model validation
  and limits. For more information, refer to [OpenFGA compatibility](docs/reference/openfga-compatibility.md).
- **A Vert.x gRPC service.** Add it to an `EventBusGrpcServer` for clients in the same Vert.x instance or cluster, or
  to a `GrpcServer` for gRPC clients over the network. For more information, refer to
  [Serve the service](docs/how-to/serve-the-service.md).
- **Conditions.** CEL conditions on tuples, with OpenFGA's parameter types, evaluated with
  [cel-java](https://github.com/google/cel-java). For more information, refer to
  [Conditions](docs/concepts/conditions.md).
- **Your database.** The tables follow OpenFGA's SQL schema. The JAR contains their migrations for each database.
  For more information, refer to [Create the tables](docs/how-to/create-the-tables.md).

## Get started

To serve the service and check your first relation, refer to [Get started](docs/get-started.md). Spectrum requires
Java 25 and Vert.x 5. It is the Java module `dev.horizon.spectrum`.

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>spectrum</artifactId>
  <version>0.2.2-SNAPSHOT</version>
</dependency>
```
<!-- x-release-please-end -->

## Documentation

- **Get started:** [Get started](docs/get-started.md)
- **Concepts:** [How Spectrum follows OpenFGA](docs/concepts/how-spectrum-follows-openfga.md),
  [Conditions](docs/concepts/conditions.md)
- **How-to:** [Create the tables](docs/how-to/create-the-tables.md),
  [Serve the service](docs/how-to/serve-the-service.md),
  [Switch to an OpenFGA server](docs/how-to/switch-to-an-openfga-server.md)
- **Reference:** [OpenFGA compatibility](docs/reference/openfga-compatibility.md),
  [Options](docs/reference/options.md), [Tables](docs/reference/tables.md), [Databases](docs/reference/databases.md)
- **Troubleshooting:** [Troubleshooting Spectrum](docs/troubleshooting.md)

## Run the tests

The main suite runs the conformance cases of OpenFGA, from `assets/tests` in the OpenFGA repository: Check,
ListObjects, StreamedListObjects and ListUsers. Like OpenFGA's own tests, it runs each case once with its tuples
written and once with them as contextual tuples. Smaller suites cover the other calls and the limits.

Each suite runs over the event bus against every database. Testcontainers starts PostgreSQL, MariaDB and SQL Server
in Docker, and SQLite runs in process. The cases of inline `$expression` conditions are skipped, because Spectrum
does not have them.

1. Start Docker.
2. Run the build:

   ```sh
   mvn verify
   ```

To test another server version, set its image with a system property:

| Property                        | Default                                      |
| ------------------------------- | -------------------------------------------- |
| `spectrum.test.postgres.image`  | `postgres:17-alpine`                         |
| `spectrum.test.mariadb.image`   | `mariadb:13.0`                               |
| `spectrum.test.sqlserver.image` | `mcr.microsoft.com/mssql/server:2022-latest` |

For example:

```sh
mvn verify -Dspectrum.test.mariadb.image=mariadb:11.4
```

## Resources

- [OpenFGA](https://github.com/openfga/openfga), whose code and behavior Spectrum follows
- [OpenFGA API](https://github.com/openfga/api), the protobuf definitions of the service
- [Vert.x gRPC](https://vertx.io/docs/vertx-grpc/java/), whose health service is the model of the service
- [cel-java](https://github.com/google/cel-java), which evaluates the conditions
- [Leadseal](https://github.com/zZHorizonZz/leadseal), which generates the migrations
- [Cinder](https://github.com/zZHorizonZz/cinder), which reads and writes the tables through a driver for each
  database
- [Basepoint](https://github.com/zZHorizonZz/basepoint), which writes the SQL of each database for Cinder

## License

Apache License 2.0. For more information, refer to [LICENSE](LICENSE).
