# How Spectrum follows OpenFGA

Spectrum is a port of the server of [OpenFGA](https://github.com/openfga/openfga) to Java and Vert.x. Its parts
follow the packages of OpenFGA, so a behavior of OpenFGA has one place in Spectrum.

Packages under `dev.horizon.spectrum` hold the parts. Only `dev.horizon.spectrum` and the generated
`dev.horizon.spectrum.openfga.v1` are exported from the module.

| OpenFGA                             | Spectrum                                                                                                              |
| ----------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `pkg/server`, `pkg/server/commands` | `OpenFgaServiceImpl`, and one handler per call in `handler`                                                           |
| `pkg/typesystem`                    | `TypeSystem`, `ModelValidator` and `RelationshipGraph` in `model`                                                     |
| `pkg/tuple`                         | `RelationshipTuple`, `TupleObject` and the `TupleUser` kinds in `tuple`                                               |
| `internal/validation`               | `KeyValidator`, `WriteValidator`, `StoredTupleValidator` and their rules in `tuple`                                   |
| `internal/graph`                    | `LocalChecker` in `check`                                                                                             |
| `pkg/server/commands/reverseexpand` | `ListObjectsQuery` and `ReverseExpandQuery` in `listobjects`                                                          |
| `pkg/server/commands/listusers`     | `ListUsersQuery` in `listusers`                                                                                       |
| `internal/condition`                | `EvaluableCondition`, `ParameterType` and `IpAddress` in `common`                                                     |
| `pkg/storage`                       | `TupleBackend`, `AuthorizationModelBackend`, `StoresBackend`, `AssertionsBackend` and `ChangelogBackend` in `storage` |
| `pkg/storage/sqlcommon`             | One SQL backend per table in `storage.sql`                                                                            |
| `pkg/storage/storagewrappers`       | `CombinedTupleReader` in `storage`                                                                                    |
| `pkg/encoder`                       | `PageQuery` in `storage`, over the AIP-158 page tokens of aquery                                                      |

## The service

`OpenFgaService` is a Vert.x gRPC service, written as the Vert.x gRPC health service is. `OpenFgaServiceImpl` gives
the name, the descriptor and the methods of `openfga.v1.OpenFGAService`, and a handler for each method, such as
`CheckHandler` or `WriteHandler`. A `UnaryHandler` ends its call with one response, and
`StreamedListObjectsHandler` streams its objects.

Each handler does what the matching method of OpenFGA's server and its command do:

1. It validates the request against the `validate.rules` of OpenFGA's protos.
2. It resolves the authorization model that the call needs: the model of the request, or the latest model of the
   store.
3. It runs the call.
4. It ends the response, or fails it with the gRPC status of the kind of OpenFGA's error code. For the statuses,
   refer to [OpenFGA compatibility](../reference/openfga-compatibility.md#status-codes).

## Checks

A check follows the rewrite of a relation:

- **Direct tuples:** a tuple of the user, a tuple of the typed wildcard of its type, or a tuple of a userset that
  the user is in.
- **Computed relations:** another relation of the same object.
- **Tuple to userset:** a relation of the objects that a tupleset relation names, such as the viewers of a parent.
- **Unions, intersections and exclusions:** their branches run at once, and a union or an intersection answers as
  soon as one branch decides it.

A check fails when it goes 25 levels deep through usersets and tuplesets. A path that comes back to a tuple it
checked before does not allow.

## Lists

ListObjects expands back from the user to the objects, along the edges of the relationship graph. An object that it
reaches through an intersection or an exclusion is a candidate, and a check confirms it. ListUsers expands the
rewrite of the relation down to the users. An exclusion from a typed wildcard gives the wildcard, and leaves out the
users that it excludes.

## Tuples

A `RelationshipTuple` holds an object, a relation, a user and an optional condition. Its user is one of three kinds:
an `ObjectUser` such as `user:anne`, a `WildcardUser` such as `user:*`, or a `UsersetUser` such as
`group:eng#member`. A validator checks a tuple against a list of rules, the rules that fit where its tuples come
from:

| Validator              | Checks                                            | Rules                                                                   |
| ---------------------- | ------------------------------------------------- | ----------------------------------------------------------------------- |
| `KeyValidator`         | The tuple of a Check or an assertion              | The user, object and relation are in the model                          |
| `StoredTupleValidator` | A stored tuple, which a newer model may not allow | The relation, the tupleset rule, the type restriction and the condition |
| `WriteValidator`       | A tuple to write, or a contextual tuple           | The rules of both                                                       |

## Storage

The tables follow OpenFGA's SQL schema: `store`, `authorization_model`, `tuple`, `changelog` and `assertion`. Each
table has a backend in `storage.sql`. A write reads the stored rows of its tuples, deletes and inserts in one
transaction, and records each change in the changelog. For the columns, refer to [Tables](../reference/tables.md).
