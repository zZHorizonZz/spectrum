# Databases

Spectrum runs on four databases. Each needs a Vert.x 5 SQL client and the Cinder driver of the database. For a
complete setup on PostgreSQL, refer to [Get started](../get-started.md).

| Database   | Minimum version | Vert.x client        | Cinder module               | Driver                  |
| ---------- | --------------- | -------------------- | --------------------------- | ----------------------- |
| PostgreSQL | 13              | `vertx-pg-client`    | `cinder-database-postgres`  | `new PostgresDriver()`  |
| MariaDB    | 11.4            | `vertx-mysql-client` | `cinder-database-mariadb`   | `new MariaDbDriver()`   |
| SQL Server | 2017            | `vertx-mssql-client` | `cinder-database-sqlserver` | `new SqlServerDriver()` |
| SQLite     | 3.44            | `vertx-jdbc-client`  | `cinder-database-sqlite`    | `new SqliteDriver()`    |

## PostgreSQL

- **Driver:** `new PostgresDriver()`
- **Pool:** `PgBuilder.pool()`
- **Migrations:** `classpath:spectrum/migration/**/postgresql`

## MariaDB

- **Driver:** `new MariaDbDriver()`
- **Pool:** `MySQLBuilder.pool()`
- **Migrations:** `classpath:spectrum/migration/**/mariadb`
- **Settings:** Set `setUseAffectedRows(true)` and `setCachePreparedStatements(true)` on `MySQLConnectOptions`, as the
  tests of Spectrum do. Without the statement cache, the client prepares and closes each statement, so a query takes
  three round trips. ListObjects and ListUsers run many queries, and on a slow link they reach their deadline with
  part of the answer.

## SQL Server

- **Driver:** `new SqlServerDriver()`
- **Pool:** `MSSQLBuilder.pool()`
- **Migrations:** `classpath:spectrum/migration/**/sqlserver`
- **Key size:** SQL Server limits a clustered primary key to 900 bytes. The key of a tuple, its store, object, relation
  and user, must fit in 450 characters.

## SQLite

- **Driver:** `new SqliteDriver()`
- **Pool:** `JDBCPool.pool(vertx, new JDBCConnectOptions().setJdbcUrl("jdbc:sqlite:app.db"), poolOptions)`
- **Migrations:** `classpath:spectrum/migration/**/sqlite`
- **Required settings:** The `org.xerial:sqlite-jdbc` driver. Use a file database, not `:memory:`, because each
  connection of an in-memory database sees its own empty database.
