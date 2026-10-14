# Get started

This guide adds Spectrum to a Java application that uses PostgreSQL. At the end, your application checks whether a
user can view a folder through a group.

## Prerequisites

- Java 25 or later.
- Maven.
- A PostgreSQL 13 or later database that your application can reach.
- A GitHub personal access token with the `read:packages` scope. GitHub Packages requires a token, also for public
  packages.

## 1. Add the dependencies

1. Add the GitHub Packages repositories and the dependencies to your `pom.xml`:

   ```xml
   <repositories>
     <repository>
       <id>github-spectrum</id>
       <url>https://maven.pkg.github.com/zZHorizonZz/spectrum</url>
     </repository>
     <repository>
       <id>github-basepoint</id>
       <url>https://maven.pkg.github.com/zZHorizonZz/basepoint</url>
     </repository>
     <repository>
       <id>github-leadseal</id>
       <url>https://maven.pkg.github.com/zZHorizonZz/leadseal</url>
     </repository>
     <repository>
       <id>github-aquery</id>
       <url>https://maven.pkg.github.com/zZHorizonZz/aquery</url>
     </repository>
   </repositories>

   <dependencies>
     <dependency>
       <groupId>dev.horizon</groupId>
       <artifactId>spectrum</artifactId>
       <version>0.1.0-SNAPSHOT</version> <!-- x-release-please-version -->
     </dependency>
     <dependency>
       <groupId>dev.horizon</groupId>
       <artifactId>basepoint-dialect-postgres</artifactId>
       <version>0.1.1-SNAPSHOT</version>
     </dependency>
     <dependency>
       <groupId>io.vertx</groupId>
       <artifactId>vertx-pg-client</artifactId>
       <version>5.2.0</version>
     </dependency>
     <dependency>
       <groupId>org.flywaydb</groupId>
       <artifactId>flyway-database-postgresql</artifactId>
       <version>13.10.0</version>
     </dependency>
     <dependency>
       <groupId>org.postgresql</groupId>
       <artifactId>postgresql</artifactId>
       <version>42.7.14</version>
     </dependency>
   </dependencies>
   ```

2. Add your token to `~/.m2/settings.xml`, once for each repository ID:

   ```xml
   <settings>
     <servers>
       <server>
         <id>github-spectrum</id>
         <username>YOUR_GITHUB_USERNAME</username>
         <password>YOUR_TOKEN</password>
       </server>
       <!-- Repeat for github-basepoint, github-leadseal and github-aquery. -->
     </servers>
   </settings>
   ```

## 2. Create the tables

Run the migrations that the `spectrum` JAR contains. Use a separate Flyway history table, so that the versions of
Spectrum do not mix with the versions of your own migrations:

```java
Flyway.configure()
    .dataSource("jdbc:postgresql://localhost:5432/app", "app", "secret")
    .locations("classpath:spectrum/migration/**/postgresql")
    .table("spectrum_schema_history")
    .baselineOnMigrate(true)
    .load()
    .migrate();
```

## 3. Write a model

The model states that members of a group can view a folder, and that a folder shares its viewers with its
subfolders:

```java
Model model = TextFormat.parse("""
    types { name: "user" }
    types {
      name: "group"
      relations { name: "member" rewrite { direct { subjects: ["user", "group#member"] } } }
    }
    types {
      name: "folder"
      relations { name: "parent" rewrite { direct { subjects: "folder" } } }
      relations { name: "viewer" rewrite { union {
        rewrites { direct { subjects: ["user", "group#member"] } }
        rewrites { from { tupleset: "parent" relation: "viewer" } }
      } } }
    }
    """, Model.class);
```

## 4. Open the relationships

Create a pool and open the relationships of a store with the PostgreSQL dialect. A store keeps its relationships apart
from those of every other store. This guide uses one store, `default`. The PostgreSQL client takes `$1` placeholders,
so pass `ParameterStyle.DOLLAR`:

```java
Pool pool = PgBuilder.pool()
    .connectingTo("postgresql://app:secret@localhost:5432/app")
    .using(vertx)
    .build();
Relations relations = Relations.open(pool, new PostgresDialect(ParameterStyle.DOLLAR), "default", model);
```

## 5. Write relationships and check a relation

Write three relationships, then check whether `user:ana` can view `folder:7`:

```java
relations.write(List.of(
        Relationship.newBuilder().setObject("folder:7").setRelation("parent").setSubject("folder:1").build(),
        Relationship.newBuilder().setObject("folder:1").setRelation("viewer").setSubject("group:eng#member").build(),
        Relationship.newBuilder().setObject("group:eng").setRelation("member").setSubject("user:ana").build()))
    .compose(written -> relations.check("user:ana", "viewer", "folder:7"))
    .onSuccess(System.out::println);
```

## Verify

The check prints `true`. `user:ana` is a member of `group:eng`, the members of `group:eng` can view `folder:1`, and
`folder:1` is the parent of `folder:7`:

```txt output
true
```

A check of a user that is not a member of `group:eng`, such as `user:bob`, prints `false`.

## Next steps

- Learn how relations combine in [Models](concepts/models.md).
- Write relationships with your data in [Write relationships in a transaction](how-to/write-relationships-in-a-transaction.md).
- Connect to MariaDB, SQL Server or SQLite with the settings in [Databases](reference/databases.md).
- Call Spectrum with OpenFGA's API in [Use the OpenFGA API](how-to/use-the-openfga-api.md).
