# Relationships

A relationship states that a subject has a relation to an object. For example, `user:ana` is a `viewer` of `doc:9`.
Zanzibar calls this a relation tuple. Spectrum answers every question from the relationships that you write and the
[model](models.md) that combines them.

A relationship has three parts:

| Part     | Format                                    | Example            |
| -------- | ----------------------------------------- | ------------------ |
| Object   | `type:id`                                 | `doc:9`            |
| Relation | A name                                    | `viewer`           |
| Subject  | `type:id`, `type:*` or `type:id#relation` | `group:eng#member` |

Spectrum writes a relationship as `object#relation@subject`, for example `doc:9#viewer@user:ana`.

## Subjects

A subject takes one of three forms:

- **An object**, such as `user:ana`. The relationship gives the relation to that object only.
- **Every object of a type**, such as `user:*`. The relationship gives the relation to all users, including users
  that no relationship names.
- **The subjects of a relation of an object**, such as `group:eng#member`. The relationship gives the relation to
  every subject that is a `member` of `group:eng`. Zanzibar calls this a userset.

Use a userset to grant access to a group instead of to each member. When a member joins or leaves the group, you
write or delete one relationship. The access of that member follows without more writes.

## Names and identifiers

A type and a relation are names. A name has lower case letters, digits and `_`, and starts with a letter. An
identifier has no spaces and no `#`. A type or a relation has at most 64 characters, and an object or a subject at
most 255.

Spectrum compares objects and subjects by their exact characters. `doc:A` and `doc:a` are two objects. The
relationship table uses a binary collation on each database, so this holds on PostgreSQL, MariaDB, SQL Server and
SQLite.

## The relationship table

Spectrum stores each relationship as one row of the `spectrum_relationship` table, in a store. The database sets the
`id` and `create_time` of each row. A unique index on `(store_id, object, relation, subject)` keeps each relationship
once per store and serves the reads down from an object. A second index on `(store_id, subject, relation, object)`
serves the reads up from a subject. For more information about stores, refer to [Stores](stores.md).

The table lives in your own database. A relationship can therefore change in the same transaction as the rows that
it guards. For more information, refer to [Consistency](consistency.md).

## What a relationship is not

- A relationship is not a permission check. A check combines relationships through the model.
- A relationship does not name users only. The types come from your model, and Spectrum treats each type the same.
- A relationship does not carry conditions or expiry times. Delete a relationship to take access away.
- A relationship that the model does not allow is never stored. Spectrum refuses it before it reaches the database.

## Related resources

- [Models](models.md)
- [Stores](stores.md)
- [Tables](../reference/tables.md)
- [Protobuf messages](../reference/protobuf-messages.md)
