# Relations API

`dev.horizon.spectrum.Relations` reads and writes relationships and answers checks and lists. For how a check works,
refer to [Check evaluation](../concepts/check-evaluation.md).

| Method                                          | Returns                      | Summary                                                 |
| ----------------------------------------------- | ---------------------------- | ------------------------------------------------------- |
| `Relations.open(client, dialect, store, model)` | `Relations`                  | Opens the relationships of a store with a model         |
| `on(client)`                                    | `Relations`                  | Runs the statements through another client              |
| `with(contextual)`                              | `Relations`                  | Adds contextual relationships to checks and lists       |
| `store()`                                       | `String`                     | Gives the store                                         |
| `model()`                                       | `Model`                      | Gives the model                                         |
| `write(relationship)`                           | `Future<Boolean>`            | Writes one relationship                                 |
| `write(relationships)`                          | `Future<Long>`               | Writes a list of relationships in one transaction       |
| `delete(relationship)`                          | `Future<Boolean>`            | Deletes one relationship                                |
| `delete(relationships)`                         | `Future<Long>`               | Deletes a list of relationships in one transaction      |
| `read(object)`                                  | `Future<List<Relationship>>` | Reads the relationships of an object                    |
| `read(object, relation)`                        | `Future<List<Relationship>>` | Reads the relationships of a relation of an object      |
| `read(filter, size, token)`                     | `Future<Page<Relationship>>` | Reads a page of the relationships that a filter selects |
| `changes(type, since, size, token)`             | `Future<Page<Change>>`       | Reads a page of the changes of the store                |
| `check(subject, relation, object)`              | `Future<Boolean>`            | Checks if a subject has a relation to an object         |
| `listObjects(subject, relation, type)`          | `Future<List<String>>`       | Lists the objects that a subject has a relation to      |
| `listSubjects(object, relation, type)`          | `Future<SubjectSet>`         | Lists the subjects that have a relation to an object    |

A method that refuses a value throws an `IllegalArgumentException` at once, before it runs a statement. The
exception does not arrive through the returned future.

## `Relations.open(SqlClient client, Dialect dialect, String store, Model model)`

- **Parameters:** `client` is a `Pool` or a `SqlConnection`. `dialect` is the basepoint dialect of the database.
  `store` is the id of the store: one that `Stores.create` gave, or your own, such as `default`. `model` is the model
  of the relations.
- **Returns:** The relationships of the store.
- **Throws:** `IllegalArgumentException` if the store is not 1 to 32 letters, digits, `_` and `-`, or the model is not
  valid.
- **Constraints:** The dialect must write the placeholders that the client takes. A MariaDB client must set
  `setUseAffectedRows(true)`. The method does not check that the tables exist.

## `Relations.on(SqlClient client)`

- **Parameters:** `client` is a `Pool` or a `SqlConnection`, for example a connection in a transaction.
- **Returns:** The same store, model and contextual relationships, with every statement on `client`.

## `Relations.with(List<Relationship> contextual)`

- **Parameters:** `contextual` is the list of contextual relationships, such as those that one request holds.
- **Returns:** The same relationships, in which a check and a list count the contextual relationships as if the table
  had them. A read gives only the relationships of the table, and nothing writes the contextual relationships.
- **Throws:** `IllegalArgumentException` if the model does not allow one of them.
- **Constraints:** Replaces the contextual relationships of this one.

## `Relations.store()`

- **Returns:** The id of the store.

## `Relations.model()`

- **Returns:** The model that the relationships were opened with.

## `Relations.write(Relationship relationship)`

- **Parameters:** `relationship` is the relationship. Spectrum writes its `object`, `relation` and `subject`, and
  ignores its `id` and `create_time`.
- **Returns:** `true` if the relationship was written, `false` if the table already had it.
- **Throws:** `IllegalArgumentException` if the relationship is not valid, or if no `direct` rewrite of its relation
  allows its subject.

## `Relations.write(List<Relationship> relationships)`

- **Parameters:** `relationships` is the list. A relationship that appears more than once is written once.
- **Returns:** The number of relationships that were written.
- **Throws:** `IllegalArgumentException` if one relationship is not valid or not allowed. Spectrum writes none of
  them in that case.
- **Constraints:** Spectrum reads which relationships the store has, and inserts the others, 400 per statement, in one
  transaction. It records a change for each relationship that it adds.
- **Fails:** `IllegalStateException` if a concurrent write added one of the relationships between the read and the
  insert. The transaction rolls back; write the relationships again.

## `Relations.delete(Relationship relationship)`

- **Parameters:** `relationship` is the relationship, found by its `object`, `relation` and `subject`.
- **Returns:** `true` if the relationship was deleted, `false` if the table did not have it.
- **Throws:** `IllegalArgumentException` if the relationship is not valid.

## `Relations.delete(List<Relationship> relationships)`

- **Parameters:** `relationships` is the list.
- **Returns:** The number of relationships that were deleted.
- **Throws:** `IllegalArgumentException` if one relationship is not valid.
- **Constraints:** Spectrum deletes the relationships one statement at a time, in one transaction. It records a change
  for each relationship that it removes.

## `Relations.read(String object)`

- **Parameters:** `object` is the object, such as `folder:7`.
- **Returns:** The relationships of the object, in order of relation, then subject.
- **Throws:** `IllegalArgumentException` if the object is not valid.

## `Relations.read(String object, String relation)`

- **Parameters:** `object` is the object. `relation` is the relation, such as `parent`.
- **Returns:** The relationships of the relation of the object, in order of subject.
- **Throws:** `IllegalArgumentException` if the object is not valid.

## `Relations.read(Relationship filter, int size, String token)`

- **Parameters:** `filter` selects the relationships: an empty field selects every value, and an object of a type
  alone, such as `doc:`, selects every object of the type. `size` is the most relationships that the page has.
  `token` is the `next` token of the page before, or `""` for the first page.
- **Returns:** A page of relationships, in order of object, relation and subject.
- **Throws:** `IllegalArgumentException` if a field of the filter, the size or the token is not valid.

## `Relations.changes(String type, Instant since, int size, String token)`

- **Parameters:** `type` is the type of the objects whose changes to read, such as `doc`, or `""` for every type.
  `since` is the earliest time of a change to read when the token is empty, or `Instant.EPOCH` for all. `size` is the
  most changes that the page has. `token` is the `next` token of the page before, or `""` to start at `since`.
- **Returns:** A page of changes, in order of id. Its `next` token reads the changes after it. When no change follows,
  the page is empty and its token is the token that you passed.
- **Throws:** `IllegalArgumentException` if the type, the size or the token is not valid, or the token was read for
  another type.

## `Relations.check(String subject, String relation, String object)`

- **Parameters:** `subject` is the subject, such as `user:ana`, `group:eng#member` or `user:*`. `relation` is the
  relation. `object` is the object.
- **Returns:** `true` if the subject has the relation to the object. For `user:*`, `true` if every user has it.
- **Throws:** `IllegalArgumentException` if a value is not valid or the model does not have it.
  `IllegalStateException`, through the future, if the check goes more than 25 levels deep.

## `Relations.listObjects(String subject, String relation, String type)`

- **Parameters:** `subject` is the subject. `relation` is the relation. `type` is the type of the objects, such as
  `doc`.
- **Returns:** The objects of the type that the subject has the relation to, in order.
- **Throws:** `IllegalArgumentException` if a value is not valid or the model does not have it.
  `IllegalStateException`, through the future, if the list goes more than 25 levels deep.

## `Relations.listSubjects(String object, String relation, String type)`

- **Parameters:** `object` is the object. `relation` is the relation. `type` is the type of the subjects, such as
  `user`.
- **Returns:** A `SubjectSet`: the subjects of the type that have the relation, or every subject of the type except
  some.
- **Throws:** `IllegalArgumentException` if a value is not valid or the model does not have it.
