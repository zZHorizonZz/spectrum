# OpenFGA compatibility

Spectrum serves `openfga.v1.OpenFGAService` from the protos of [OpenFGA's API](https://github.com/openfga/api) and
follows the behavior of the OpenFGA server. Its tests run the conformance cases of OpenFGA's repository, from
`assets/tests`, against each database. This page lists what is the same and what differs.

Spectrum is for tests and small applications, not a replacement for an OpenFGA server. To move to one, refer to
[Switch to an OpenFGA server](../how-to/switch-to-an-openfga-server.md).

## The same as OpenFGA

- **Calls:** every call of the service, including BatchCheck, Expand, ListUsers and StreamedListObjects.
- **Request validation:** the `validate.rules` of OpenFGA's protos, such as the ULID pattern of a store ID and the
  range of a page size. The rules are checked by validators that protoc-gen-validate generates.
- **Model validation:** schema versions 1.1 and 1.2, reserved names, rewrites, type restrictions, entrypoints, cycles
  and conditions, with OpenFGA's error messages.
- **Checks:** direct tuples, typed wildcards, usersets, computed relations, tuple to userset, unions,
  intersections, exclusions, cycles, contextual tuples and conditions.
- **Lists:** ListObjects and ListUsers give the users and objects that OpenFGA gives, including a typed wildcard for
  an exclusion from one.
- **Writes:** `on_duplicate` and `on_missing`, the refusal of a tuple that is in a request twice, and a conflict for
  a write of a stored tuple with another condition.
- **Changes:** every write and delete records its change in the same transaction. ReadChanges filters by type and
  start time.
- **Stores:** a deleted store keeps its rows, and gets and lists leave it out. A delete of a store that does not
  exist succeeds.
- **Limits:** the defaults of an OpenFGA server. For more information, refer to [Options](options.md).

## Different from OpenFGA

| Area                      | OpenFGA                                                      | Spectrum                                                       |
| ------------------------- | ------------------------------------------------------------ | -------------------------------------------------------------- |
| HTTP API                  | Serves the HTTP API that the OpenFGA SDKs call               | Serves gRPC only, over the event bus or the network            |
| Modeling language         | The CLI turns the modeling language into JSON                | Takes models as JSON or protobuf only                          |
| Error status codes        | Sends its error code as the gRPC status code, such as `2000` | Sends the standard gRPC status of the kind of the code         |
| UpdateStore               | Declares the call but does not serve it                      | Renames the store                                              |
| Consistency               | Can serve reads from a replica or a cache                    | Reads the database, so every preference reads the latest data  |
| Inline conditions         | Has `$expression` conditions behind the experimental flag    | Does not have them                                             |
| Condition evaluation cost | Limits the cost of each evaluation                           | Limits each comprehension to 100 iterations                    |
| Check trace               | Gives a resolution trace when `trace` is set                 | Gives no trace                                                 |
| Caches and optimizations  | Caches checks and iterators, and plans resolvers             | Caches type systems only                                       |
| Authentication            | Checks API keys or OIDC tokens                               | Leaves authentication to your server                           |
| Continuation tokens       | Encodes its own token format                                 | Encrypts AIP-158 page tokens: pass tokens back as you get them |
| AuthZEN                   | Has an experimental AuthZEN service                          | Does not have it                                               |

## Status codes

OpenFGA sends its error code as the gRPC status code, such as `2000` for `validation_error`. Vert.x gRPC sends only
the standard status codes, so Spectrum sends the standard status of the kind of the code, with OpenFGA's message:

| OpenFGA error code                                                | gRPC status          |
| ----------------------------------------------------------------- | -------------------- |
| A validation code, such as `validation_error` or `invalid_tuple`  | `INVALID_ARGUMENT`   |
| A not found code, such as `store_id_not_found`                    | `NOT_FOUND`          |
| A conflict of a write                                             | `ABORTED`            |
| An internal error                                                 | `INTERNAL`           |

In a BatchCheck response, each failed check has its OpenFGA error code in its `CheckError`.
