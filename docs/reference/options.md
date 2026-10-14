# Options

`OpenFgaServiceOptions` holds the limits of the service and the key of its continuation tokens.
`new OpenFgaServiceOptions()` has the defaults of an OpenFGA server. Each `with` method gives a copy with another
limit.

| Option                             | Method                                 | Default          | Sets                                                     |
| ---------------------------------- | -------------------------------------- | ---------------- | -------------------------------------------------------- |
| `maxTuplesPerWrite`                | `withMaxTuplesPerWrite`                | 100              | The tuples that one Write writes and deletes             |
| `maxTypesPerAuthorizationModel`    | `withMaxTypesPerAuthorizationModel`    | 100              | The type definitions of an authorization model           |
| `maxAuthorizationModelSizeInBytes` | `withMaxAuthorizationModelSizeInBytes` | 262144           | The serialized size of an authorization model            |
| `maxConditionContextSizeInBytes`   | `withMaxConditionContextSizeInBytes`   | 32768            | The serialized size of the condition context of a tuple  |
| `resolveNodeLimit`                 | `withResolveNodeLimit`                 | 25               | The depth of a Check, ListObjects or ListUsers           |
| `maxChecksPerBatchCheck`           | `withMaxChecksPerBatchCheck`           | 50               | The checks of one BatchCheck                             |
| `listObjectsMaxResults`            | `withListObjects`                      | 1000             | The objects that ListObjects gives                       |
| `listObjectsDeadline`              | `withListObjects`                      | 3 seconds        | The time that ListObjects and StreamedListObjects search |
| `listUsersMaxResults`              | `withListUsers`                        | 1000             | The users that ListUsers gives                           |
| `listUsersDeadline`                | `withListUsers`                        | 3 seconds        | The time that ListUsers searches                         |
| `pageTokenKey`                     | `withPageTokenKey`                     | A random AES key | The key that encrypts continuation tokens                |

At the deadline, ListObjects and ListUsers give what they found so far, as OpenFGA does.

Continuation tokens are [AIP-158](https://google.aip.dev/158) page tokens, encrypted with AES-GCM. A client can't
read or change them, and a token works only for the list that gave it. Each `new OpenFgaServiceOptions()` makes a new
random key, so give every service that serves the same clients the same key:

```java
SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(System.getenv("SPECTRUM_PAGE_TOKEN_KEY")), "AES");
OpenFgaServiceOptions options = new OpenFgaServiceOptions().withPageTokenKey(key);
```

When the key changes, the tokens of the old key stop working, and clients start their lists again.
