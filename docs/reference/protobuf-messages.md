# Protobuf messages

Spectrum defines its messages in the protobuf package `spectrum.v1` and the Java package `dev.horizon.spectrum.v1`.
The `spectrum` JAR contains the `.proto` files, so your own files can import them. For the ideas behind the model,
refer to [Models](../concepts/models.md).

| Message        | File                             | Purpose                                       |
| -------------- | -------------------------------- | --------------------------------------------- |
| `Model`        | `spectrum/v1/model.proto`        | The types and relations of a model            |
| `Type`         | `spectrum/v1/model.proto`        | A type of objects                             |
| `Relation`     | `spectrum/v1/model.proto`        | A relation of a type                          |
| `Rewrite`      | `spectrum/v1/model.proto`        | Which subjects a relation has                 |
| `Direct`       | `spectrum/v1/model.proto`        | The subjects that relationships name          |
| `Computed`     | `spectrum/v1/model.proto`        | The subjects of another relation              |
| `From`         | `spectrum/v1/model.proto`        | The subjects of a relation of related objects |
| `Rewrites`     | `spectrum/v1/model.proto`        | The rewrites of a union or an intersection    |
| `Difference`   | `spectrum/v1/model.proto`        | The subjects of one rewrite but not another   |
| `Relationship` | `spectrum/v1/relationship.proto` | A relationship and a row of the table         |
| `Store`        | `spectrum/v1/store.proto`        | A store and a row of the table                |
| `StoreModel`   | `spectrum/v1/store_model.proto`  | A model that a store keeps, as its row        |
| `SubjectSet`   | `spectrum/v1/subject_set.proto`  | The result of a list of subjects              |

## `spectrum.v1.Model`

| Field   | Type            | Required | Constraints                                    |
| ------- | --------------- | -------- | ---------------------------------------------- |
| `types` | `repeated Type` | No       | Each type name appears once                    |
| `id`    | `string`        | No       | The ULID that a store gave the model, or empty |

## `spectrum.v1.Type`

| Field       | Type                | Required | Constraints                                                           |
| ----------- | ------------------- | -------- | --------------------------------------------------------------------- |
| `name`      | `string`            | Yes      | At most 64 lower case letters, digits and `_`, starting with a letter |
| `relations` | `repeated Relation` | No       | Each relation name appears once in the type                           |

## `spectrum.v1.Relation`

| Field     | Type      | Required | Constraints                                                           |
| --------- | --------- | -------- | --------------------------------------------------------------------- |
| `name`    | `string`  | Yes      | At most 64 lower case letters, digits and `_`, starting with a letter |
| `rewrite` | `Rewrite` | Yes      | Must be set                                                           |

## `spectrum.v1.Rewrite`

A rewrite sets exactly one of these fields:

| Field          | Type         | Constraints                       |
| -------------- | ------------ | --------------------------------- |
| `direct`       | `Direct`     | Lists at least one subject        |
| `computed`     | `Computed`   | Names a relation of the same type |
| `from`         | `From`       | Names a tupleset of the same type |
| `union`        | `Rewrites`   | Combines at least one rewrite     |
| `intersection` | `Rewrites`   | Combines at least one rewrite     |
| `difference`   | `Difference` | Sets `base` and `subtract`        |

## `spectrum.v1.Direct`

| Field      | Type              | Required | Constraints                                                                                            |
| ---------- | ----------------- | -------- | ------------------------------------------------------------------------------------------------------ |
| `subjects` | `repeated string` | Yes      | Each is a type (`user`), every object of a type (`user:*`), or a userset (`group#member`) of the model |

## `spectrum.v1.Computed`

| Field      | Type     | Required | Constraints                 |
| ---------- | -------- | -------- | --------------------------- |
| `relation` | `string` | Yes      | A relation of the same type |

## `spectrum.v1.From`

| Field      | Type     | Required | Constraints                                                                           |
| ---------- | -------- | -------- | ------------------------------------------------------------------------------------- |
| `tupleset` | `string` | Yes      | A relation of the same type with a `direct` rewrite of types only, such as `"folder"` |
| `relation` | `string` | Yes      | A relation of at least one type that the tupleset lists                               |

## `spectrum.v1.Rewrites`

| Field      | Type               | Required | Constraints          |
| ---------- | ------------------ | -------- | -------------------- |
| `rewrites` | `repeated Rewrite` | Yes      | At least one rewrite |

## `spectrum.v1.Difference`

| Field      | Type      | Required | Constraints               |
| ---------- | --------- | -------- | ------------------------- |
| `base`     | `Rewrite` | Yes      | The subjects              |
| `subtract` | `Rewrite` | Yes      | The subjects to leave out |

## `spectrum.v1.Relationship`

| Field         | Type                        | Set by   | Constraints                                                       |
| ------------- | --------------------------- | -------- | ----------------------------------------------------------------- |
| `id`          | `string`                    | Database | A random UUID                                                     |
| `store_id`    | `string`                    | Spectrum | The store of the relationships that wrote it                      |
| `object`      | `string`                    | You      | `type:id`, at most 255 characters                                 |
| `relation`    | `string`                    | You      | A relation of the type of the object with a `direct` rewrite      |
| `subject`     | `string`                    | You      | `type:id`, `type:*` or `type:id#relation`, at most 255 characters |
| `create_time` | `google.protobuf.Timestamp` | Database | The time of the write, in UTC                                     |

An identifier has no spaces and no `#`. The relationship table keeps these fields as columns. For more information,
refer to [Tables](tables.md).

## `spectrum.v1.Store`

| Field         | Type                        | Set by   | Constraints                      |
| ------------- | --------------------------- | -------- | -------------------------------- |
| `id`          | `string`                    | Spectrum | A ULID                           |
| `name`        | `string`                    | You      | 1 to 255 characters              |
| `create_time` | `google.protobuf.Timestamp` | Database | The time of the creation, in UTC |

## `spectrum.v1.StoreModel`

The row of a kept model. The Stores API gives models as `Model`, with their `id`, and never as `StoreModel`.

| Field         | Type                        | Set by   | Constraints                     |
| ------------- | --------------------------- | -------- | ------------------------------- |
| `id`          | `string`                    | Spectrum | A ULID                          |
| `store_id`    | `string`                    | Spectrum | The store that keeps the model  |
| `definition`  | `string`                    | Spectrum | The model as protobuf JSON      |
| `create_time` | `google.protobuf.Timestamp` | Database | The time that the store kept it |

## `spectrum.v1.Change`

| Field         | Type                        | Set by   | Constraints                             |
| ------------- | --------------------------- | -------- | --------------------------------------- |
| `id`          | `string`                    | Spectrum | A ULID                                  |
| `store_id`    | `string`                    | Spectrum | The store of the relationship           |
| `object`      | `string`                    | Spectrum | The object of the relationship          |
| `relation`    | `string`                    | Spectrum | The relation of the relationship        |
| `subject`     | `string`                    | Spectrum | The subject of the relationship         |
| `operation`   | `Operation`                 | Spectrum | `OPERATION_WRITE` or `OPERATION_DELETE` |
| `create_time` | `google.protobuf.Timestamp` | Database | The time of the change, in UTC          |

## `spectrum.v1.Operation`

| Value                   | Number | Meaning                           |
| ----------------------- | ------ | --------------------------------- |
| `OPERATION_UNSPECIFIED` | 0      | Never recorded                    |
| `OPERATION_WRITE`       | 1      | A write added the relationship    |
| `OPERATION_DELETE`      | 2      | A delete removed the relationship |

## `spectrum.v1.Assertion`

| Field         | Type                    | Required | Constraints                                                      |
| ------------- | ----------------------- | -------- | ---------------------------------------------------------------- |
| `subject`     | `string`                | Yes      | A subject that the model has, such as `user:ana`                 |
| `relation`    | `string`                | Yes      | A relation of the type of the object                             |
| `object`      | `string`                | Yes      | An object, such as `doc:9`                                       |
| `expectation` | `bool`                  | No       | What a check of the subject, the relation and the object answers |
| `contextual`  | `repeated Relationship` | No       | Contextual relationships that the model allows                   |

## `spectrum.v1.ModelAssertions`

The row of the assertions of a model. The Stores API gives assertions as `Assertion`, and never as
`ModelAssertions`.

| Field        | Type                 | Set by   | Constraints                               |
| ------------ | -------------------- | -------- | ----------------------------------------- |
| `model_id`   | `string`             | Spectrum | The model                                 |
| `store_id`   | `string`             | Spectrum | The store of the model                    |
| `assertions` | `repeated Assertion` | Spectrum | Not a column; the content of `definition` |
| `definition` | `string`             | Spectrum | The assertions as protobuf JSON           |

## `spectrum.v1.SubjectSet`

| Field      | Type              | Constraints                                                                |
| ---------- | ----------------- | -------------------------------------------------------------------------- |
| `subjects` | `repeated string` | The subjects that have the relation, when `all` is `false`, in order       |
| `all`      | `bool`            | `true` if every subject of the type has the relation, except `except`      |
| `except`   | `repeated string` | The subjects that do not have the relation, when `all` is `true`, in order |

An empty `SubjectSet` means that no subject of the type has the relation.
