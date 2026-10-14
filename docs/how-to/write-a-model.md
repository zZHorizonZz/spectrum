# Write a model

A model defines the types of your application and how their relations combine. For the ideas behind a model, refer
to [Models](../concepts/models.md).

## Before you begin

- List the types of objects that your application protects, such as `doc` and `folder`, and the types that get
  access, such as `user` and `group`.
- List the relations that people grant, such as `owner` and `editor`.

## Write the model

1. Create the file `src/main/resources/model.txtpb`, and add a type for each kind of subject:

   ```txt
   types { name: "user" }
   types {
     name: "group"
     relations { name: "member" rewrite { direct { subjects: ["user", "group#member"] } } }
   }
   ```

   The subject `group#member` lets a group contain the members of another group.

2. Add the protected types. Give each relation that people grant a `direct` rewrite:

   ```txt
   types {
     name: "doc"
     relations { name: "owner" rewrite { direct { subjects: "user" } } }
     relations { name: "editor" rewrite { direct { subjects: ["user", "group#member"] } } }
   }
   ```

3. Derive the other relations from the ones that people grant. In this example, editors and owners can also view
   the document:

   ```txt
   relations { name: "viewer" rewrite { union {
     rewrites { direct { subjects: ["user", "user:*"] } }
     rewrites { computed { relation: "editor" } }
     rewrites { computed { relation: "owner" } }
   } } }
   ```

   Add the relation inside the `doc` type. The subject `user:*` lets one relationship share the document with
   every user.

4. Load the model and open the relationships with it:

   ```java
   Model model = TextFormat.parse(
       Files.readString(Path.of("src/main/resources/model.txtpb")), Model.class);
   Relations relations = Relations.open(pool, dialect, "default", model);
   ```

   `Relations.open` validates the model. An invalid model throws an `IllegalArgumentException` at once.

## Fix validation errors

Each error names the relation and the problem. These are the most common errors:

| Error                                                                                  | Fix                               |
| -------------------------------------------------------------------------------------- | --------------------------------- |
| `the relation doc#viewer allows 'user', whose type 'user' the model does not have`     | Add `types { name: "user" }`      |
| `the relation doc#viewer is computed from 'owner', which the type 'doc' does not have` | Add the relation `owner` to `doc` |
| `the relation doc#viewer has a rewrite that is not set`                                | Add a `rewrite` to the relation   |
| `the type 'user' is defined two times`                                                 | Merge the two types               |

For every rule, refer to [Protobuf messages](../reference/protobuf-messages.md).

## Verify

Check a relation that the model derives:

```java
relations.write(Relationship.newBuilder()
        .setObject("doc:9").setRelation("owner").setSubject("user:ana").build())
    .compose(written -> relations.check("user:ana", "viewer", "doc:9"))
    .onSuccess(System.out::println);
```

```txt output
true
```

## Next steps

- [Write relationships in a transaction](write-relationships-in-a-transaction.md)
- [Check evaluation](../concepts/check-evaluation.md)
