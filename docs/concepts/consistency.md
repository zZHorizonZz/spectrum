# Consistency

Spectrum stores relationships in the database of your application. A relationship can therefore change in the same
transaction as the data that it protects. A check in that transaction sees the change at once. A rollback undoes the
relationship together with the data.

## Why it matters

Consider an application that creates a document and makes its author the owner. With a separate authorization
service, the application writes in two places. If the second write fails, the document has no owner, or the owner
has no document.

With Spectrum, both writes are in one transaction:

```java
pool.withTransaction(connection -> documents.insert(connection, document)
    .compose(inserted -> relations.on(connection).write(Relationship.newBuilder()
        .setObject("doc:" + document.getId())
        .setRelation("owner")
        .setSubject("user:" + author)
        .build())));
```

Either both rows exist or neither does.

## Compared to Zanzibar

Zanzibar is a separate service with its own storage. It answers a check from a snapshot, and a client passes a
consistency token, a zookie, to read a snapshot at least as new as its last write. Spectrum needs no token. The
isolation level of your database transaction decides what a check sees.

## Transactions in Spectrum

- A method that runs on a `Pool` takes a connection for each statement, outside any transaction of yours.
- `relations.on(connection)` runs every statement on that connection. If the connection is in a transaction, the
  statements are part of it.
- `write` and `delete` of a list run in one transaction. On a `Pool`, Spectrum starts the transaction. On a
  connection with no transaction, Spectrum begins one and commits it. On a connection in a transaction, Spectrum
  joins it.

## What consistency does not cover

- Spectrum does not make reads repeatable. A check reads several relations one after another. Under the
  `READ COMMITTED` isolation level, another transaction can commit between those reads.
- Spectrum does not lock relationships. Two transactions can write the same relationship. The unique index keeps
  one, and the write of the other counts as zero.

## Related resources

- [Changes](changes.md)
- [Write relationships in a transaction](../how-to/write-relationships-in-a-transaction.md)
- [Relationships](relationships.md)
