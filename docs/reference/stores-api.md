# Stores API

`dev.horizon.spectrum.Stores` creates, lists and deletes stores, and keeps the models of each. For the ideas behind
stores, refer to [Stores](../concepts/stores.md).

| Method                                      | Returns                   | Summary                                       |
| ------------------------------------------- | ------------------------- | --------------------------------------------- |
| `Stores.open(client, dialect)`              | `Stores`                  | Opens the stores of a database                |
| `on(client)`                                | `Stores`                  | Runs the statements through another client    |
| `create(name)`                              | `Future<Store>`           | Creates a store                               |
| `get(id)`                                   | `Future<Store>`           | Gets a store                                  |
| `list(name, size, token)`                   | `Future<Page<Store>>`     | Lists stores, in order of id                  |
| `delete(id)`                                | `Future<Boolean>`         | Deletes a store and everything in it          |
| `writeModel(store, model)`                  | `Future<Model>`           | Keeps a model in a store                      |
| `readModel(store, id)`                      | `Future<Model>`           | Reads a model of a store                      |
| `latestModel(store)`                        | `Future<Model>`           | Reads the latest model of a store             |
| `listModels(store, size, token)`            | `Future<Page<Model>>`     | Lists the models of a store, the latest first |
| `writeAssertions(store, model, assertions)` | `Future<Void>`            | Keeps the assertions of a model               |
| `readAssertions(store, model)`              | `Future<List<Assertion>>` | Reads the assertions of a model               |

A method fails with a `NoSuchElementException`, through its future, when the store or the model does not exist. A
method that refuses a value throws an `IllegalArgumentException` at once.

## `Stores.open(SqlClient client, Dialect dialect)`

- **Parameters:** `client` is a `Pool` or a `SqlConnection`. `dialect` is the basepoint dialect of the database.
- **Returns:** The stores.

## `Stores.on(SqlClient client)`

- **Parameters:** `client` is a `Pool` or a `SqlConnection`, for example a connection in a transaction.
- **Returns:** The same stores, with every statement on `client`.

## `Stores.create(String name)`

- **Parameters:** `name` is the name of the store.
- **Returns:** The store, with a ULID as its id and the time that the database set.
- **Throws:** `IllegalArgumentException` if the name is empty or has more than 255 characters.

## `Stores.get(String id)`

- **Parameters:** `id` is the id of the store.
- **Returns:** The store.
- **Fails:** `NoSuchElementException` if the store does not exist.

## `Stores.list(String name, int size, String token)`

- **Parameters:** `name` is the name of the stores to list, or `""` for every store. `size` is the most stores that
  the page has. `token` is the `next` token of the page before, or `""` for the first page.
- **Returns:** A page of stores, in order of id.
- **Throws:** `IllegalArgumentException` if `size` is less than 1, or the token is not valid.

## `Stores.delete(String id)`

- **Parameters:** `id` is the id of the store.
- **Returns:** `true` if the store was deleted, `false` if it did not exist.
- **Constraints:** Deletes the relationships, the changes, the assertions, the models and the store in one
  transaction.

## `Stores.writeModel(String store, Model model)`

- **Parameters:** `store` is the id of the store. `model` is the model. Spectrum ignores its `id`.
- **Returns:** The model, with the ULID that the store gave it as its `id`.
- **Throws:** `IllegalArgumentException` if the model is not valid.
- **Fails:** `NoSuchElementException` if the store does not exist.

## `Stores.readModel(String store, String id)`

- **Parameters:** `store` is the id of the store. `id` is the id of the model.
- **Returns:** The model, with its `id`.
- **Fails:** `NoSuchElementException` if the store or the model does not exist.

## `Stores.latestModel(String store)`

- **Parameters:** `store` is the id of the store.
- **Returns:** The model with the greatest id, with its `id`.
- **Fails:** `NoSuchElementException` if the store does not exist or has no model.

## `Stores.listModels(String store, int size, String token)`

- **Parameters:** `store` is the id of the store. `size` is the most models that the page has. `token` is the `next`
  token of the page before, or `""` for the first page.
- **Returns:** A page of models, the latest first.
- **Throws:** `IllegalArgumentException` if `size` is less than 1, or the token is not valid.
- **Fails:** `NoSuchElementException` if the store does not exist.

## `Stores.writeAssertions(String store, String model, List<Assertion> assertions)`

- **Parameters:** `store` is the id of the store. `model` is the id of the model. `assertions` is the list of
  assertions, which replaces the assertions that the model had. An empty list removes them.
- **Returns:** A future that completes when the store keeps the assertions.
- **Fails:** `IllegalArgumentException` if the model cannot check an assertion, or does not allow one of its contextual
  relationships. `NoSuchElementException` if the store or the model does not exist.

## `Stores.readAssertions(String store, String model)`

- **Parameters:** `store` is the id of the store. `model` is the id of the model.
- **Returns:** The assertions of the model, in the order that they were written, or an empty list.
- **Fails:** `NoSuchElementException` if the store or the model does not exist.

## `dev.horizon.spectrum.Page<T>`

| Component | Type      | Description                                                  |
| --------- | --------- | ------------------------------------------------------------ |
| `items`   | `List<T>` | The items of the page, in order                              |
| `next`    | `String`  | The token that reads the next page, or `""` on the last page |

`hasNext()` returns `true` when `next` is not empty.
