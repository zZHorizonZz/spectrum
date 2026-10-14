# Tables

Spectrum keeps its data in five tables of your database. For how to create them, refer to
[Create the tables](../how-to/create-the-tables.md).

| Table                       | Message                       | One row per                         |
| --------------------------- | ----------------------------- | ----------------------------------- |
| `spectrum_store`            | `spectrum.v1.Store`           | Store                               |
| `spectrum_store_model`      | `spectrum.v1.StoreModel`      | Model that a store keeps            |
| `spectrum_relationship`     | `spectrum.v1.Relationship`    | Relationship of a store             |
| `spectrum_change`           | `spectrum.v1.Change`          | Change of a relationship of a store |
| `spectrum_model_assertions` | `spectrum.v1.ModelAssertions` | Model that has assertions           |

No table has a foreign key. A relationship can name a store that `spectrum_store` does not have, such as `default`.
`Stores.delete` deletes the rows of a store from all five tables.

## `spectrum_relationship`

| Column        | Set by   | PostgreSQL                 | MariaDB                            | SQL Server                                      | SQLite |
| ------------- | -------- | -------------------------- | ---------------------------------- | ----------------------------------------------- | ------ |
| `id`          | Database | `uuid`                     | `UUID`                             | `UNIQUEIDENTIFIER`                              | `TEXT` |
| `store_id`    | You      | `varchar(32) COLLATE "C"`  | `VARCHAR(32) COLLATE utf8mb4_bin`  | `NVARCHAR(32) COLLATE Latin1_General_100_BIN2`  | `TEXT` |
| `object`      | You      | `varchar(255) COLLATE "C"` | `VARCHAR(255) COLLATE utf8mb4_bin` | `NVARCHAR(255) COLLATE Latin1_General_100_BIN2` | `TEXT` |
| `relation`    | You      | `varchar(64) COLLATE "C"`  | `VARCHAR(64) COLLATE utf8mb4_bin`  | `NVARCHAR(64) COLLATE Latin1_General_100_BIN2`  | `TEXT` |
| `subject`     | You      | `varchar(255) COLLATE "C"` | `VARCHAR(255) COLLATE utf8mb4_bin` | `NVARCHAR(255) COLLATE Latin1_General_100_BIN2` | `TEXT` |
| `create_time` | Database | `timestamptz`              | `DATETIME(6)`                      | `DATETIME2(6)`                                  | `TEXT` |

Every column is `NOT NULL`.

| Index                                                       | Columns                                     | Unique | Serves                                 |
| ----------------------------------------------------------- | ------------------------------------------- | ------ | -------------------------------------- |
| `pk_spectrum_relationship`                                  | `id`                                        | Yes    | The primary key                        |
| `ux_spectrum_relationship_store_id_object_relation_subject` | `store_id`, `object`, `relation`, `subject` | Yes    | Checks and writes, down from an object |
| `ix_spectrum_relationship_store_id_subject_relation_object` | `store_id`, `subject`, `relation`, `object` | No     | Lists of objects, up from a subject    |

## `spectrum_store`

| Column        | Set by   | PostgreSQL                | MariaDB                           | SQL Server                                     | SQLite |
| ------------- | -------- | ------------------------- | --------------------------------- | ---------------------------------------------- | ------ |
| `id`          | Spectrum | `varchar(32) COLLATE "C"` | `VARCHAR(32) COLLATE utf8mb4_bin` | `NVARCHAR(32) COLLATE Latin1_General_100_BIN2` | `TEXT` |
| `name`        | You      | `varchar(255)`            | `VARCHAR(255)`                    | `NVARCHAR(255)`                                | `TEXT` |
| `create_time` | Database | `timestamptz`             | `DATETIME(6)`                     | `DATETIME2(6)`                                 | `TEXT` |

Every column is `NOT NULL`. The primary key is `id`, a ULID.

## `spectrum_store_model`

| Column        | Set by   | PostgreSQL                | MariaDB                           | SQL Server                                     | SQLite |
| ------------- | -------- | ------------------------- | --------------------------------- | ---------------------------------------------- | ------ |
| `id`          | Spectrum | `varchar(32) COLLATE "C"` | `VARCHAR(32) COLLATE utf8mb4_bin` | `NVARCHAR(32) COLLATE Latin1_General_100_BIN2` | `TEXT` |
| `store_id`    | Spectrum | `varchar(32) COLLATE "C"` | `VARCHAR(32) COLLATE utf8mb4_bin` | `NVARCHAR(32) COLLATE Latin1_General_100_BIN2` | `TEXT` |
| `definition`  | Spectrum | `text`                    | `LONGTEXT`                        | `NVARCHAR(MAX)`                                | `TEXT` |
| `create_time` | Database | `timestamptz`             | `DATETIME(6)`                     | `DATETIME2(6)`                                 | `TEXT` |

Every column is `NOT NULL`. The primary key is `id`, a ULID. The index `ix_spectrum_store_model_store_id_id` on
`store_id` and `id` finds the latest model of a store. `definition` holds the model as protobuf JSON.

## `spectrum_change`

| Column        | Set by   | PostgreSQL                 | MariaDB                            | SQL Server                                      | SQLite |
| ------------- | -------- | -------------------------- | ---------------------------------- | ----------------------------------------------- | ------ |
| `id`          | Spectrum | `varchar(32) COLLATE "C"`  | `VARCHAR(32) COLLATE utf8mb4_bin`  | `NVARCHAR(32) COLLATE Latin1_General_100_BIN2`  | `TEXT` |
| `store_id`    | Spectrum | `varchar(32) COLLATE "C"`  | `VARCHAR(32) COLLATE utf8mb4_bin`  | `NVARCHAR(32) COLLATE Latin1_General_100_BIN2`  | `TEXT` |
| `object`      | Spectrum | `varchar(255) COLLATE "C"` | `VARCHAR(255) COLLATE utf8mb4_bin` | `NVARCHAR(255) COLLATE Latin1_General_100_BIN2` | `TEXT` |
| `relation`    | Spectrum | `varchar(64)`              | `VARCHAR(64)`                      | `NVARCHAR(64)`                                  | `TEXT` |
| `subject`     | Spectrum | `varchar(255)`             | `VARCHAR(255)`                     | `NVARCHAR(255)`                                 | `TEXT` |
| `operation`   | Spectrum | `varchar(32)`              | `VARCHAR(32)`                      | `NVARCHAR(32)`                                  | `TEXT` |
| `create_time` | Database | `timestamptz`              | `DATETIME(6)`                      | `DATETIME2(6)`                                  | `TEXT` |

Every column is `NOT NULL`. The primary key is `id`, a ULID. The index `ix_spectrum_change_store_id_id` on `store_id`
and `id` reads the changes of a store in order. `operation` is `OPERATION_WRITE` or `OPERATION_DELETE`.

## `spectrum_model_assertions`

| Column       | Set by   | PostgreSQL                | MariaDB                           | SQL Server                                     | SQLite |
| ------------ | -------- | ------------------------- | --------------------------------- | ---------------------------------------------- | ------ |
| `model_id`   | Spectrum | `varchar(32) COLLATE "C"` | `VARCHAR(32) COLLATE utf8mb4_bin` | `NVARCHAR(32) COLLATE Latin1_General_100_BIN2` | `TEXT` |
| `store_id`   | Spectrum | `varchar(32) COLLATE "C"` | `VARCHAR(32) COLLATE utf8mb4_bin` | `NVARCHAR(32) COLLATE Latin1_General_100_BIN2` | `TEXT` |
| `definition` | Spectrum | `text`                    | `LONGTEXT`                        | `NVARCHAR(MAX)`                                | `TEXT` |

Every column is `NOT NULL`. The primary key is `model_id`. The index `ix_spectrum_model_assertions_store_id` finds the
rows of a store. `definition` holds the assertions as protobuf JSON.

## Collations and defaults

The keyed text columns compare by binary collation, so `doc:A` and `doc:a` are two objects. SQLite compares text by
its bytes by default.

| Column                       | PostgreSQL          | MariaDB            | SQL Server         | SQLite                                  |
| ---------------------------- | ------------------- | ------------------ | ------------------ | --------------------------------------- |
| `id` of a relationship       | `gen_random_uuid()` | `UUID()`           | `NEWID()`          | A random version 4 UUID                 |
| `create_time` of every table | `CURRENT_TIMESTAMP` | `UTC_TIMESTAMP(6)` | `SYSUTCDATETIME()` | `strftime('%Y-%m-%dT%H:%M:%fZ', 'now')` |

MariaDB, SQL Server and SQLite store `create_time` in UTC without a time zone.

## Migrations

The `spectrum` JAR contains one folder per migration under `spectrum/migration`. A folder is named by its version
alone, and holds one SQL file per database:

```txt
spectrum/migration/
  20261014172202/
    migration.json
    mariadb/V20261014172202.sql
    postgresql/V20261014172202.sql
    sqlite/V20261014172202.sql
    sqlserver/V20261014172202.sql
```

Leadseal generates the files from the protobuf messages of Spectrum when Spectrum builds.
