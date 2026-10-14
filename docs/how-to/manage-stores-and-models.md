# Manage stores and models

Create a store for each set of relationships that must stay apart, and keep each version of your model in it. For the
ideas behind stores, refer to [Stores](../concepts/stores.md).

## Before you begin

- Create the tables. For more information, refer to [Create the tables](create-the-tables.md).
- Write a model. For more information, refer to [Write a model](write-a-model.md).

## Create a store and keep a model

1. Open the stores with your pool and dialect:

   ```java
   Stores stores = Stores.open(pool, new PostgresDialect(ParameterStyle.DOLLAR));
   ```

2. Create a store, then keep a model in it:

   ```java
   Future<Relations> relations = stores.create("documents")
       .compose(store -> stores.writeModel(store.getId(), model)
           .map(kept -> Relations.open(pool, dialect, store.getId(), kept)));
   ```

   `writeModel` validates the model, and returns it with the id that the store gave it.

3. Later, open the relationships of the store with its latest model:

   ```java
   stores.latestModel(storeId)
       .map(latest -> Relations.open(pool, dialect, storeId, latest));
   ```

## List and delete stores

1. List the stores one page at a time. Pass the token of a page to read the next page:

   ```java
   stores.list("", 50, "")
       .onSuccess(page -> page.items().forEach(store -> System.out.println(store.getName())));
   ```

   Pass a name instead of `""` to list only the stores with that name.

2. Delete a store. Spectrum deletes its models and relationships in the same transaction:

   ```java
   stores.delete(storeId).onSuccess(deleted -> System.out.println(deleted));
   ```

> [!CAUTION]
> Deleting a store deletes every relationship in it. The rows cannot be restored.

## Verify

Get the store after you delete it. The future fails with a `NoSuchElementException`:

```java
stores.get(storeId).onFailure(failed -> System.out.println(failed.getMessage()));
```

```txt output
the store '01M4XNYW5SK4DFCPRPE7SHW5H0' does not exist
```

## Next steps

- [Use the OpenFGA API](use-the-openfga-api.md)
- [Stores API](../reference/stores-api.md)
