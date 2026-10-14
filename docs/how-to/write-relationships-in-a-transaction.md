# Write relationships in a transaction

Write a relationship in the transaction that changes the data it guards. Both changes then commit or roll back
together. For the reasons, refer to [Consistency](../concepts/consistency.md).

## Before you begin

- Open the relationships with a `Pool`. For more information, refer to [Get started](../get-started.md).
- Run your own statements with the same `Pool`.

## Write in a transaction

1. Start a transaction with `Pool.withTransaction`. Vert.x commits it when the returned future succeeds, and rolls it
   back when the future fails:

   ```java
   pool.withTransaction(connection -> ...);
   ```

2. Run your statements on the connection. Then call `relations.on(connection)`, and write the relationships on the
   result:

   ```java
   Future<Boolean> created = pool.withTransaction(connection -> connection
       .preparedQuery("INSERT INTO doc (id, title) VALUES ($1, $2)")
       .execute(Tuple.of("9", "Plan"))
       .compose(inserted -> relations.on(connection).write(Relationship.newBuilder()
           .setObject("doc:9").setRelation("owner").setSubject("user:ana").build())));
   ```

   Every statement of `relations.on(connection)` runs on that connection, in your transaction.

3. To delete access with your data, call `delete` on the same result:

   ```java
   pool.withTransaction(connection -> connection
       .preparedQuery("DELETE FROM doc WHERE id = $1")
       .execute(Tuple.of("9"))
       .compose(deleted -> relations.on(connection).read("doc:9"))
       .compose(relationships -> relations.on(connection).delete(relationships)));
   ```

> [!NOTE]
> `write` and `delete` of a list already run in one transaction. Use `relations.on(connection)` when the
> relationships must change together with your own rows.

## Verify

Check the relation inside the transaction, then fail the transaction on purpose:

```java
pool.withTransaction(connection -> relations.on(connection)
        .write(Relationship.newBuilder().setObject("doc:9").setRelation("owner").setSubject("user:ana").build())
        .compose(written -> relations.on(connection).check("user:ana", "owner", "doc:9"))
        .compose(seen -> Future.<Boolean>failedFuture("seen " + seen)))
    .recover(failed -> {
      System.out.println(failed.getMessage());
      return relations.check("user:ana", "owner", "doc:9");
    })
    .onSuccess(System.out::println);
```

The check inside the transaction sees the relationship. After the rollback, it is gone:

```txt output
seen true
false
```

## Next steps

- [Consistency](../concepts/consistency.md)
- [Relations API](../reference/relations-api.md)
