# Spectrum

Spectrum is a Java library for relationship-based access control in the style of Zanzibar. It stores relationships
in your own PostgreSQL, MariaDB, SQL Server or SQLite database. It answers whether a subject has a relation to an
object, and lists the objects of a subject and the subjects of an object.

A relationship states that a subject has a relation to an object, such as `user:ana` is a `viewer` of `doc:9`. A model
states how relations combine. For example, the viewers of a folder can view each document in it.

```java
Relations relations = Relations.open(pool, new PostgresDialect(ParameterStyle.DOLLAR), "default", model);
relations.check("user:ana", "viewer", "doc:9").onSuccess(System.out::println);
```

## Features

- **Zanzibar rewrites.** Combine relations with `direct`, `computed`, `from`, `union`, `intersection` and
  `difference`. For more information, refer to [Models](docs/concepts/models.md).
- **Exact lists.** `listObjects` and `listSubjects` give the same answers as `check`, including every subject except
  some. For more information, refer to [Check evaluation](docs/concepts/check-evaluation.md).
- **Your database, your transactions.** A relationship changes in the same transaction as the rows that it guards.
  For more information, refer to [Consistency](docs/concepts/consistency.md).
- **Stores.** Keep the relationships and models of each tenant apart, as OpenFGA stores do. For more information,
  refer to [Stores](docs/concepts/stores.md).
- **A change log.** Every write and delete that changes a store records the change in the same transaction, for
  `ReadChanges` and your own readers. For more information, refer to [Changes](docs/concepts/changes.md).
- **The OpenFGA API.** `spectrum-openfga` implements OpenFGA's service. Call it locally over the Vert.x event bus,
  or add it to your own Vert.x gRPC server for gRPC and HTTP clients. For more information, refer to
  [Use the OpenFGA API](docs/how-to/use-the-openfga-api.md).
- **Generated migrations.** The JAR contains the migrations of the tables for each database.
  [Leadseal](https://github.com/zZHorizonZz/leadseal) generates them, and
  [Basepoint](https://github.com/zZHorizonZz/basepoint) writes the SQL of each statement.

## Get started

To add Spectrum to an application and check your first relation, refer to [Get started](docs/get-started.md).
Spectrum requires Java 25 and a Vert.x 5 SQL client.

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>spectrum</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```
<!-- x-release-please-end -->

## Documentation

- **Get started:** [Get started](docs/get-started.md)
- **Concepts:** [Relationships](docs/concepts/relationships.md), [Models](docs/concepts/models.md),
  [Stores](docs/concepts/stores.md), [Changes](docs/concepts/changes.md),
  [Check evaluation](docs/concepts/check-evaluation.md),
  [Consistency](docs/concepts/consistency.md)
- **How-to:** [Create the tables](docs/how-to/create-the-tables.md), [Write a model](docs/how-to/write-a-model.md),
  [Manage stores and models](docs/how-to/manage-stores-and-models.md),
  [Write relationships in a transaction](docs/how-to/write-relationships-in-a-transaction.md),
  [List objects and subjects](docs/how-to/list-objects-and-subjects.md),
  [Read the changes of a store](docs/how-to/read-the-changes-of-a-store.md),
  [Use the OpenFGA API](docs/how-to/use-the-openfga-api.md),
  [Serve the OpenFGA API over gRPC and HTTP](docs/how-to/serve-the-openfga-api-over-grpc-and-http.md)
- **Reference:** [Relations API](docs/reference/relations-api.md), [Stores API](docs/reference/stores-api.md),
  [OpenFGA compatibility](docs/reference/openfga-compatibility.md),
  [Protobuf messages](docs/reference/protobuf-messages.md), [Tables](docs/reference/tables.md),
  [Databases](docs/reference/databases.md), [Limits](docs/reference/limits.md)
- **Troubleshooting:** [Troubleshooting Spectrum](docs/troubleshooting.md)

## Modules

| Module             | Package                        | Contents                                                                    |
| ------------------ | ------------------------------ | --------------------------------------------------------------------------- |
| `spectrum`         | `dev.horizon.spectrum`         | Relations, stores, the protobuf messages and the migrations                 |
| `spectrum-openfga` | `dev.horizon.spectrum.openfga` | The OpenFGA service, and the generated OpenFGA messages, service and client |
| `spectrum-it`      |                                | The contract tests against each database                                    |

## Run the tests

`spectrum` and `spectrum-openfga` have unit tests. `spectrum-it` runs the contract tests of the relations, the stores
and the OpenFGA service over the event bus against each database.
Testcontainers starts PostgreSQL, MariaDB and SQL Server in Docker, and SQLite runs in process.

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

## License

Apache License 2.0. For more information, refer to [LICENSE](LICENSE).
