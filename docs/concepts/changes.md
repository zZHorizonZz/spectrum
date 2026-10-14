# Changes

The changes of a store are a log of each relationship that a write added and each relationship that a delete removed,
in order. OpenFGA serves the same log with `ReadChanges`. Use it to keep a cache, a search index or another system in
step with the relationships, without reading every relationship again.

## What a change records

A change names the store, the object, the relation and the subject of one relationship, and whether a write added it
or a delete removed it. Spectrum records a change only when the store changes:

- A write of a relationship that the store already has records nothing.
- A delete of a relationship that the store does not have records nothing.
- Contextual relationships record nothing, because nothing writes them.

A write and a delete record their changes in their own transaction. A rollback removes the changes together with
the relationships, so the log holds exactly the writes and deletes that committed.

## Order and ids

Spectrum gives each change a ULID as its id. A ULID starts with the time in milliseconds, so ids grow in the order
that the changes are made, and a page of changes is in order of id. A time to read from, such as OpenFGA's
`start_time`, becomes the least ULID of that time.

The id comes from the clock of the application that writes, not from the database. Two transactions that write at
the same time can commit in the other order than their ids. A reader that reads right after the first commit can
miss the change of the second one. To read every change, read again from a token that is a few seconds older than
the newest change, or read when no write is running.

## Tokens

A page of changes ends with a token. Pass the token to read the changes after the page. When no change follows, the
page is empty, and its token is the token that you passed. Keep that token, and read again later to get the next
changes. A store that never changed gives an empty token.

A token remembers the type that it was read for. A token of the type `doc` cannot read the changes of every type.

## Writes that race

To record exactly the relationships that it added, a write reads which relationships the store has before it inserts
the others. If another transaction inserts one of them in between, the insert adds fewer rows than the write expects.
The write then fails with an `IllegalStateException`, and its transaction rolls back. Write the relationships again.

## What the log is not

- The log is not a backup. Deleting a store deletes its changes.
- The log does not shrink. Delete old rows of `spectrum_change` yourself if you do not need them.
- The log does not record models or assertions.

## Related resources

- [Read the changes of a store](../how-to/read-the-changes-of-a-store.md)
- [Consistency](consistency.md)
- [Relations API](../reference/relations-api.md)
