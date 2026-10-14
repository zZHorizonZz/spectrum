# OpenFGA compatibility

`dev.horizon.spectrum.openfga.OpenFgaService` implements the `openfga.v1.OpenFGAService` of OpenFGA's API protos, at
commit `c0650ce` of `github.com/openfga/api`. For how to serve it, refer to
[Use the OpenFGA API](../how-to/use-the-openfga-api.md).

## Methods

| Method                    | Status          | Notes                                                                            |
| ------------------------- | --------------- | -------------------------------------------------------------------------------- |
| `CreateStore`             | Supported       | The id is a ULID                                                                 |
| `GetStore`                | Supported       | `updated_at` equals `created_at`                                                 |
| `ListStores`              | Supported       | Filters by `name`                                                                |
| `DeleteStore`             | Supported       | Deletes the models and tuples of the store                                       |
| `UpdateStore`             | `UNIMPLEMENTED` | OpenFGA does not implement it either                                             |
| `WriteAuthorizationModel` | Supported       | Schema 1.1 and 1.2, without conditions                                           |
| `ReadAuthorizationModel`  | Supported       | Gives schema 1.1                                                                 |
| `ReadAuthorizationModels` | Supported       | The latest model first                                                           |
| `Write`                   | Supported       | At most 100 tuples, in one transaction, with `on_duplicate` and `on_missing`     |
| `Read`                    | Supported       | In order of object, relation and user                                            |
| `Check`                   | Supported       | With contextual tuples                                                           |
| `BatchCheck`              | Supported       | With contextual tuples per check                                                 |
| `ListObjects`             | Supported       | With contextual tuples                                                           |
| `ListUsers`               | Supported       | One user filter of a type, with contextual tuples                                |
| `Expand`                  | `UNIMPLEMENTED` |                                                                                  |
| `ReadChanges`             | Supported       | In order of id, by type and `start_time`; the token stays when no change follows |
| `WriteAssertions`         | Supported       | At most 100 assertions; replaces those of the model                              |
| `ReadAssertions`          | Supported       |                                                                                  |
| `StreamedListObjects`     | Supported       | Streams the objects of a list of objects once the list is complete               |

## Models

- A model must have the schema version `1.1` or `1.2`. Other versions fail with `INVALID_ARGUMENT`.
- A model with conditions, or a directly related user type with a condition, fails with `UNIMPLEMENTED`.
- A type or a relation name has at most 64 lower case letters, digits and `_`, and starts with a letter. OpenFGA takes
  more names, such as `Doc` or `doc-v2`. Spectrum refuses them with `INVALID_ARGUMENT`.
- `ReadAuthorizationModel` gives metadata with the directly related user types of every relation. It drops the
  `module` and `source_info` of the metadata.

## Requests

- The consistency preference changes nothing. Spectrum reads the database, which holds every committed write.
- `context` changes nothing, because Spectrum has no conditions.
- A tuple with a condition fails with `UNIMPLEMENTED`.
- A request without `authorization_model_id` uses the latest model of the store.
- `ListUsers` with a user filter that has a relation, such as `group#member`, fails with `UNIMPLEMENTED`.
- When every user of the type has the relation, `ListUsers` gives the typed wildcard, such as `user:*`, as OpenFGA
  does. It does not name the users that a difference leaves out.
- A page size is 1 to 100, and 50 by default.

## Changes and assertions

- A change records a write that added a relationship or a delete that removed one, in the transaction of the write.
  For more information, refer to [Changes](../concepts/changes.md).
- The id of a change is a ULID from the clock of the application. Two writes that commit at the same time can commit
  in the other order than their ids, so a reader that reads right after a commit can miss a change.
- An assertion keeps its tuple key, its expectation and its contextual tuples. It drops its `context`, because
  Spectrum has no conditions. A contextual tuple with a condition fails with `UNIMPLEMENTED`.

## Errors

| Cause                                                      | gRPC status        |
| ---------------------------------------------------------- | ------------------ |
| An invalid value, or a tuple that the model does not allow | `INVALID_ARGUMENT` |
| A check that goes more than 25 levels deep                 | `INVALID_ARGUMENT` |
| A store or a model that does not exist                     | `NOT_FOUND`        |
| A store with no model, and no model in the request         | `NOT_FOUND`        |
| A feature that Spectrum does not have                      | `UNIMPLEMENTED`    |

Over HTTP, Vert.x gives the HTTP status of the gRPC status, such as `400` for `INVALID_ARGUMENT`, with an empty body.
OpenFGA sends a JSON body with an error code and a message.

A `BatchCheck` that refuses one check gives that check an `error` with the code `validation_error` and a message, and
answers the other checks.

## Transports

| Transport        | How to serve                                     | Client                                                                           |
| ---------------- | ------------------------------------------------ | -------------------------------------------------------------------------------- |
| Vert.x event bus | `EventBusGrpcServer.addService`                  | `OpenFGAServiceGrpcClient.create(EventBusGrpcClient)`                            |
| gRPC over HTTP/2 | `GrpcServer.addService`, on a Vert.x HTTP server | `OpenFGAServiceGrpcClient.create(GrpcClient, SocketAddress)`, or any gRPC client |
| HTTP with JSON   | The same `GrpcServer`, by transcoding            | Any HTTP client, with `Content-Type: application/json`                           |
