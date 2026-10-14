# Stores

A store holds a set of relationships and the models that combine them, apart from every other store. OpenFGA has the
same concept. Use one store per application, per environment, or per tenant whose access rules must never meet.

## Why stores exist

Two tenants can use the same object names, such as `doc:1`. In separate stores, a relationship of one tenant never
grants access in the store of the other. A check, a list and a read see only the relationships of their own store.

## Store ids

Every relationship row carries the id of its store, and every read and write of Spectrum filters by it. The unique
index of the relationship table starts with the store id, so each store has its own set of relationships.

A store id has 1 to 32 letters, digits, `_` and `-`. It comes from one of two places:

- **`Stores.create`** gives a new store a ULID, such as `01M4XNYW5SK4DFCPRPE7SHW5H0`, and keeps its name in the store
  table. The OpenFGA service creates every store this way.
- **Your own id**, such as `default`, works without a row in the store table. Pass it to `Relations.open`. This suits
  an application with one set of relationships.

## Models of a store

A store keeps every model that it is given, with a ULID as its id. ULIDs grow with time, so the model with the
greatest id is the latest. The OpenFGA service uses the latest model when a request names no model.

A kept model never changes. To change the rules, keep a new model. Relationships stay in the store, and a check with
the new model reads them.

## What a store is not

- A store is not a database schema. All stores share the three tables of Spectrum.
- A store does not check its relationships against its models. Each write is checked against the model that you
  open the relationships with.
- A store has no quotas or permissions of its own.

## Related resources

- [Manage stores and models](../how-to/manage-stores-and-models.md)
- [Stores API](../reference/stores-api.md)
- [Relationships](relationships.md)
