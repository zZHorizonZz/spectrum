# Create the tables

Spectrum keeps stores, models, assertions, relationships and their changes in five tables of your database:
`spectrum_store`, `spectrum_store_model`, `spectrum_model_assertions`, `spectrum_relationship` and `spectrum_change`. The `spectrum` JAR contains the migrations that create them, under
`spectrum/migration`, one folder per version. Leadseal generates these migrations from the protobuf messages of
Spectrum.

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

3. Run the migrations each time that your application starts, before you open the stores or the relationships.
   Flyway applies only the migrations that a new version of Spectrum adds.

## Create the tables with another tool

1. Extract the SQL files of your database from the `spectrum` JAR:

   ```sh
   unzip -j spectrum-0.1.0-SNAPSHOT.jar 'spectrum/migration/*/postgresql/*.sql' -d migrations
   ```

2. Add the files to your migration tool in the order of their names. Each name is a version, such as
   `V20261014172202.sql`.

> [!CAUTION]
> Do not edit the files. A later version of Spectrum adds migrations that expect the tables as these files create
> them.

## Verify

Query the relationship table. It exists and is empty:

```sql
SELECT count(*) FROM spectrum_relationship;
```

```txt output
 count
-------
     0
```

## Next steps

- [Write a model](write-a-model.md)
- [Tables](../reference/tables.md)
