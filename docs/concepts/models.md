# Models

A model defines the types of objects and the relations of each type. For each relation, a rewrite states which
subjects have it. Zanzibar calls the model a namespace configuration, and OpenFGA calls it an authorization model.

Without a model, a relationship grants only what it names. With a model, one relationship can grant much more. For
example, the owner of a folder can view every document in it.

## Types and relations

A model is a `spectrum.v1.Model` protobuf message. This model has three types:

```txt
types { name: "user" }
types {
  name: "folder"
  relations { name: "owner" rewrite { direct { subjects: "user" } } }
  relations { name: "viewer" rewrite { union {
    rewrites { direct { subjects: "user" } }
    rewrites { computed { relation: "owner" } }
  } } }
}
types {
  name: "doc"
  relations { name: "parent" rewrite { direct { subjects: "folder" } } }
  relations { name: "viewer" rewrite { from { tupleset: "parent" relation: "viewer" } } }
}
```

A type without relations, such as `user`, can still be a subject. A relation exists only on the type that defines
it.

## Rewrites

A rewrite states which subjects a relation has. Spectrum has the six rewrites of Zanzibar:

| Rewrite        | The subjects of the relation are                                      | Example                                          |
| -------------- | --------------------------------------------------------------------- | ------------------------------------------------ |
| `direct`       | The subjects that relationships of the relation name                  | `direct { subjects: "user" }`                    |
| `computed`     | The subjects of another relation of the same object                   | `computed { relation: "owner" }`                 |
| `from`         | The subjects of a relation of the objects that another relation names | `from { tupleset: "parent" relation: "viewer" }` |
| `union`        | The subjects of any of the rewrites                                   | Viewers are users or owners                      |
| `intersection` | The subjects of all of the rewrites                                   | Publishers are editors and approvers             |
| `difference`   | The subjects of one rewrite that another rewrite does not have        | Readers are viewers but not blocked              |

Only a `direct` rewrite lets you write relationships of the relation. Its `subjects` list what a relationship can
name: a type (`user`), every object of a type (`user:*`), or the subjects of a relation (`group#member`). Spectrum
refuses a relationship whose subject the list does not allow.

A `from` rewrite follows a relation to other objects. Zanzibar calls it a tuple to userset. Its `tupleset` must be a
relation with a `direct` rewrite of types only, because Spectrum reads the objects that it names.

## Validation

Spectrum validates a model when you open the relationships with it. Each type and relation must have a valid name
and be defined once. Each rewrite must be set, and each name that it uses must exist in the model. An invalid model
fails at once with an `IllegalArgumentException` that names the relation and the problem:

```txt
the relation doc#viewer is computed from 'owner', which the type 'doc' does not have
```

A model is a protobuf message, so you can keep it as text in a file, as JSON, or build it in code.

## Recommendations

- Keep `direct` rewrites on the relations that people grant, such as `owner` and `editor`. Derive the other
  relations with `computed`, `from` and `union`.
- Grant access to groups through a userset, such as `group#member`, instead of to each user.
- Prefer `union` to `intersection` and `difference`. A list of objects can answer a model of unions alone from the
  relationships that it reads. An `intersection` or a `difference` makes it check each object that it finds.
- Keep chains of `from` short. A check follows the model at most 25 levels deep.

## What a model is not

- A model is not stored in the database. You pass it each time that you open the relationships.
- A model does not change existing relationships. A relationship that a new model no longer allows stays in the
  table until you delete it.
- A model has no conditions on attributes, such as time or IP address.

## Related resources

- [Write a model](../how-to/write-a-model.md)
- [Check evaluation](check-evaluation.md)
- [Protobuf messages](../reference/protobuf-messages.md)
