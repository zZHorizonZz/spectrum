# Read the changes of a store

Read the changes of a store to keep another system in step with its relationships. For how the log works, refer to
[Changes](../concepts/changes.md).

## Before you begin

- Open the relationships of the store. For more information, refer to [Get started](../get-started.md).

## Read the changes

1. Read the first page of changes. Pass an empty type for every type, and `Instant.EPOCH` to start at the first
   change:

   ```java
   Page<Change> page = relations.changes("", Instant.EPOCH, 100, "").await();
   page.items().forEach(change -> System.out.println(change.getOperation() + " " + change.getObject() + "#"
       + change.getRelation() + "@" + change.getSubject()));
   ```

2. Keep the token of the page, for example in your own table, and read the next page with it:

   ```java
   Page<Change> next = relations.changes("", Instant.EPOCH, 100, page.next()).await();
   ```

3. When a page is empty, wait and read again with the same token. The token of an empty page is the token that you
   passed:

   ```java
   vertx.setPeriodic(5000, timer -> relations.changes("", Instant.EPOCH, 100, token)
       .onSuccess(read -> {
         read.items().forEach(this::apply);
         token = read.next();
       }));
   ```

To read the changes of one type only, pass the type, such as `doc`. To start at a time instead of the first change,
pass that time instead of `Instant.EPOCH` and an empty token.

## Read the changes with the OpenFGA API

Call `ReadChanges` with the token of the response before:

```java
ReadChangesResponse page = fga.readChanges(ReadChangesRequest.newBuilder()
    .setStoreId(store)
    .setContinuationToken(token)
    .build()).await();
```

## Verify

Write a relationship, then read from the token that you kept. The page holds one change:

```java
relations.write(Relationship.newBuilder()
    .setObject("doc:9").setRelation("viewer").setSubject("user:ana").build()).await();
relations.changes("", Instant.EPOCH, 100, token).await().items()
    .forEach(change -> System.out.println(change.getOperation() + " " + change.getObject()));
```

```txt output
OPERATION_WRITE doc:9
```

## Next steps

- [Changes](../concepts/changes.md)
- [OpenFGA compatibility](../reference/openfga-compatibility.md)
