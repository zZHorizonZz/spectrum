# Troubleshooting Spectrum

## ERROR: relation "spectrum_relationship" does not exist (42P01)

**Symptom:** The first call to the relationships fails on PostgreSQL:

```txt
io.vertx.pgclient.PgException: ERROR: relation "spectrum_relationship" does not exist (42P01)
```

**Cause:** The database has no tables of Spectrum. The migrations of Spectrum did not run on this database.

**Fix:** Run the migrations before you open the relationships:

```java
Flyway.configure()
    .dataSource("jdbc:postgresql://localhost:5432/app", "app", "secret")
    .locations("classpath:spectrum/migration/**/postgresql")
    .table("spectrum_schema_history")
    .baselineOnMigrate(true)
    .load()
    .migrate();
```

**Verify:** `SELECT count(*) FROM spectrum_relationship;` returns `0` instead of an error.

## Found non-empty schema(s) "public" but no schema history table

**Symptom:** Flyway fails when it runs the migrations of Spectrum with their own history table:

```txt
org.flywaydb.core.api.FlywayException: Found non-empty schema(s) "public" but no schema history table. Use baseline() or set baselineOnMigrate to true to initialize the schema history table.
```

**Cause:** The database already has tables, and the history table `spectrum_schema_history` does not exist yet.
Flyway does not create a history table in a schema with tables unless you allow it.

**Fix:** Add `.baselineOnMigrate(true)` to the Flyway configuration of Spectrum. The baseline version is `1`, so
Flyway still applies every migration of Spectrum.

**Verify:** Flyway applies the migrations, and `SELECT count(*) FROM spectrum_relationship;` returns `0`.

## Detected resolved migration not applied to database: 20261014172202

**Symptom:** Flyway fails after you add the migrations of Spectrum to the locations of your own migrations:

```txt
org.flywaydb.core.api.exception.FlywayValidateException: Validate failed: Migrations have failed validation
Detected resolved migration not applied to database: 20261014172202.
```

**Cause:** Your history table already has a newer version than the migration of Spectrum. Flyway refuses to apply
an older version out of order.

**Fix:** Remove `classpath:spectrum/migration/**/...` from your own Flyway configuration. Run the migrations of
Spectrum with a second Flyway configuration and their own history table, as in the fix for
[ERROR: relation "spectrum_relationship" does not exist (42P01)](#error-relation-spectrum_relationship-does-not-exist-42p01).

**Verify:** Both Flyway configurations migrate without errors.

## ERROR: syntax error at or near "," (42601)

**Symptom:** A write or a check fails on PostgreSQL with a syntax error:

```txt
io.vertx.pgclient.PgException: ERROR: syntax error at or near "," (42601)
```

**Cause:** The dialect writes `?` placeholders, but the Vert.x PostgreSQL client takes `$1`, `$2` and so on.

**Fix:** Open the relationships with the placeholder style of your client. For the PostgreSQL client, use
`ParameterStyle.DOLLAR`:

```java
Relations relations = Relations.open(pool, new PostgresDialect(ParameterStyle.DOLLAR), "default", model);
```

For every client, refer to [Databases](reference/databases.md).

**Verify:** `relations.check("user:ana", "viewer", "doc:9")` returns `true` or `false` instead of an error.

## A write of a relationship that exists returns true on MariaDB

**Symptom:** On MariaDB, `write` returns `true` for a relationship that the table already has. A write of a list
counts relationships that were already there.

**Cause:** Spectrum skips an existing relationship with `ON DUPLICATE KEY UPDATE`. By default, the Vert.x MySQL client
counts the rows that a statement found, not the rows that it changed. A skipped relationship counts as found.

**Fix:** Count changed rows in the connect options of the pool:

```java
MySQLConnectOptions options = new MySQLConnectOptions()
    .setHost("localhost")
    .setDatabase("app")
    .setUser("app")
    .setPassword("secret")
    .setUseAffectedRows(true);
```

**Verify:** A second write of the same relationship returns `false`.

## the model does not allow doc:1#viewer@group:eng

**Symptom:** A write fails at once with an `IllegalArgumentException`:

```txt
java.lang.IllegalArgumentException: the model does not allow doc:1#viewer@group:eng: no direct rewrite of doc#viewer allows group
```

**Cause:** The `direct` rewrite of the relation does not list the form of the subject. In this example,
`doc#viewer` allows `group#member`, but the subject is the group itself, `group:eng`.

**Fix:** Write the subject in a form that the rewrite lists, such as `group:eng#member`. If the model must allow the
new form, add it to the `subjects` of the `direct` rewrite:

```txt
relations { name: "viewer" rewrite { direct { subjects: ["user", "group#member"] } } }
```

**Verify:** The write returns `true`.

## the relations go more than 25 levels deep

**Symptom:** A check or a list of objects fails with an `IllegalStateException`:

```txt
java.lang.IllegalStateException: the relations go more than 25 levels deep; check the model and the tuples for a chain that long
```

**Cause:** The check followed more than 25 steps through `computed` rewrites, `from` rewrites or usersets. A chain
of about 25 nested folders causes this, and so does a chain of about 25 nested groups.

**Fix:** Find the long chain with `relations.read(object, relation)`, starting from the object of the check. Make
the chain shorter, for example by granting access on a higher folder or by flattening nested groups.

**Verify:** The check returns `true` or `false` instead of an exception.

## A request to the OpenFGA HTTP API returns 415

**Symptom:** An HTTP request to the OpenFGA API fails with an empty body:

```txt
HTTP/1.1 415 Unsupported Media Type
content-length: 0
```

**Cause:** Vert.x transcodes only requests with the content type of JSON. This holds for every method, also `GET`,
which has no body.

**Fix:** Send the header `Content-Type: application/json` with every request:

```sh
curl http://localhost:8080/stores -H 'Content-Type: application/json'
```

**Verify:** The response has the status `200` and a JSON body, such as `{"stores":[]}`.

## The OpenFGA service answers NOT_FOUND for a store that exists

**Symptom:** A check, a write or a list fails with `NOT_FOUND` for a store that `GetStore` finds.

**Cause:** The store has no model, and the request names none. Spectrum then looks for the latest model of the store,
and finds none.

**Fix:** Write a model to the store with `WriteAuthorizationModel` before you write tuples or check them.

**Verify:** `ReadAuthorizationModels` lists one model, and the check returns `allowed`.

## a concurrent write wrote 1 of the relationships first

**Symptom:** A write of relationships fails with an `IllegalStateException`:

```txt
java.lang.IllegalStateException: a concurrent write wrote 1 of the relationships first; write them again
```

**Cause:** Another transaction inserted one of the relationships after this write read which relationships the store
had, and before it inserted them. The write rolls back, so that the changes of the store hold exactly the
relationships that each write added.

**Fix:** Write the relationships again. The second write finds the relationship that the other transaction wrote, and
skips it.

**Verify:** The write returns the number of relationships that it added, and `relations.changes` lists each of them
once.
