# Create the tables

Spectrum keeps its data in five tables of your database: `store`, `authorization_model`, `tuple`, `changelog` and
`assertion`. The `spectrum` JAR contains the migrations that create them, under `spectrum/migration`, one folder per
version. [Leadseal](https://github.com/zZHorizonZz/leadseal) generates these migrations from the protobuf messages
in `spectrum/v1/schema.proto`.

## Before you begin

- Add `spectrum` to your dependencies. For more information, refer to [Get started](../get-started.md).
- Add Flyway and the Flyway module of your database, such as `flyway-database-postgresql`.

## Create the tables with Flyway

1. Find the folder name of your database:

   | Database   | Folder       |
   | ---------- | ------------ |
   | PostgreSQL | `postgresql` |
   | MariaDB    | `mariadb`    |
   | SQL Server | `sqlserver`  |
   | SQLite     | `sqlite`     |

2. Run the migrations of that folder with their own history table:

   ```java
   Flyway.configure()
       .dataSource("jdbc:postgresql://localhost:5432/app", "app", "secret")
       .locations("classpath:spectrum/migration/**/postgresql")
       .table("spectrum_schema_history")
       .baselineOnMigrate(true)
       .load()
       .migrate();
   ```

   The separate history table keeps the versions of Spectrum apart from the versions of your migrations. Both use
   timestamps, and Flyway refuses a version that is older than the last one it applied.

   `baselineOnMigrate(true)` lets Flyway create that history table in a database that already has tables. The
   baseline version is `1`, which is older than every migration of Spectrum, so Flyway still applies them all.

3. Run the migrations each time that your application starts, before you serve the service. Flyway applies only the
   migrations that a new version of Spectrum adds.

## Create the tables with another tool

1. Extract the SQL files of your database from the `spectrum` JAR:

   ```sh
   unzip -j spectrum-0.1.0-SNAPSHOT.jar 'spectrum/migration/*/postgresql/*.sql' -d migrations
   ```

2. Add the files to your migration tool in the order of their names. Each name is a version, such as
   `V20261015055048.sql`.

> [!CAUTION]
> Do not edit the files. A later version of Spectrum adds migrations that expect the tables as these files create
> them.

> [!WARNING]
> The table names are those of OpenFGA. If your database already has a table named `store`, `tuple`, `changelog`,
> `assertion` or `authorization_model`, give Spectrum its own schema or database.

## Verify

Query the tuple table. It exists and is empty:

```sql
SELECT count(*) FROM tuple;
```

```txt output
 count
-------
     0
```

## Next steps

- [Serve the service](serve-the-service.md)
- [Tables](../reference/tables.md)
