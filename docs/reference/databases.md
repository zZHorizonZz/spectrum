# Databases

Spectrum runs on four databases. Each needs a Vert.x 5 SQL client and the basepoint dialect of the database. For a
complete setup on PostgreSQL, refer to [Get started](../get-started.md).

| Database   | Minimum version | Vert.x client        | Basepoint module              | Placeholder style              |
| ---------- | --------------- | -------------------- | ----------------------------- | ------------------------------ |
| PostgreSQL | 13              | `vertx-pg-client`    | `basepoint-dialect-postgres`  | `ParameterStyle.DOLLAR`        |
| MariaDB    | 11.4            | `vertx-mysql-client` | `basepoint-dialect-mariadb`   | `ParameterStyle.QUESTION_MARK` |
| SQL Server | 2017            | `vertx-mssql-client` | `basepoint-dialect-sqlserver` | `ParameterStyle.AT_P`          |
| SQLite     | 3.44            | `vertx-jdbc-client`  | `basepoint-dialect-sqlite`    | `ParameterStyle.QUESTION_MARK` |

## PostgreSQL

- **Dialect:** `new PostgresDialect(ParameterStyle.DOLLAR)`
- **Pool:** `PgBuilder.pool()`
- **Migrations:** `classpath:spectrum/migration/**/postgresql`
- **Required settings:** None.

## MariaDB

- **Dialect:** `new MariaDbDialect(ParameterStyle.QUESTION_MARK)`
- **Pool:** `MySQLBuilder.pool()`
- **Migrations:** `classpath:spectrum/migration/**/mariadb`
- **Required settings:** `MySQLConnectOptions.setUseAffectedRows(true)`. Without it, a write counts relationships
  that the table already has.

## SQL Server

- **Dialect:** `new SqlServerDialect(ParameterStyle.AT_P)`
- **Pool:** `MSSQLBuilder.pool()`
- **Migrations:** `classpath:spectrum/migration/**/sqlserver`
- **Required settings:** None.

## SQLite

- **Dialect:** `new SqliteDialect(ParameterStyle.QUESTION_MARK)`
- **Pool:** `JDBCPool.pool(vertx, new JDBCConnectOptions().setJdbcUrl("jdbc:sqlite:app.db"), poolOptions)`
- **Migrations:** `classpath:spectrum/migration/**/sqlite`
- **Required settings:** The `org.xerial:sqlite-jdbc` driver. Use a file database, not `:memory:`, because each
  connection of an in-memory database sees its own empty database.
