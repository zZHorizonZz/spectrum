# Tables

The tables follow the SQL schema of OpenFGA, with the prefix `spectrum_` on their names. The column names are those of
OpenFGA's PostgreSQL schema, and the lengths of text columns are those of its MySQL schema. The messages in
`spectrum/v1/schema.proto` define the tables, and [Leadseal](https://github.com/zZHorizonZz/leadseal) generates their
migrations.

## spectrum_store

| Column       | Type      | Description                                        |
| ------------ | --------- | -------------------------------------------------- |
| `id`         | Text, 26  | The store ID, a ULID. The primary key              |
| `name`       | Text, 64  | The name                                           |
| `created_at` | Timestamp | The time the store was created                     |
| `updated_at` | Timestamp | The time the store was last renamed                |
| `deleted_at` | Timestamp | The time the store was deleted, or `NULL`          |

The database writes the times: `created_at` and `updated_at` default to the time of the insert, and the rewrite of
`updated_at` sets it to the time of each update.

## spectrum_authorization_model

| Column                   | Type     | Description                                                     |
| ------------------------ | -------- | --------------------------------------------------------------- |
| `store`                  | Text, 26 | The store                                                       |
| `authorization_model_id` | Text, 26 | The model ID, a ULID. The latest model has the greatest ID      |
| `schema_version`         | Text, 5  | The schema version, such as `1.1`                               |
| `serialized_protobuf`    | Bytes    | The model, as a serialized `openfga.v1.AuthorizationModel`      |

The primary key is `store` and `authorization_model_id`. OpenFGA's table also has the columns `type` and
`type_definition`, for models of a row per type definition. OpenFGA writes each new model as one row with
`serialized_protobuf`, so Spectrum keeps only that. A model never changes, so the trigger `refuse_update` refuses an
update of a row.

## spectrum_tuple

| Column              | Type      | Description                                                            |
| ------------------- | --------- | ---------------------------------------------------------------------- |
| `id`                | Integer   | The tuple ID, which the database gives each tuple in order             |
| `store`             | Text, 26  | The store                                                              |
| `object_type`       | Text, 128 | The type of the object, such as `doc`                                  |
| `object_id`         | Text, 255 | The ID of the object, such as `roadmap`                                |
| `relation`          | Text, 50  | The relation, such as `viewer`                                         |
| `_user`             | Text, 256 | The user, such as `user:anne`, `user:*` or `group:eng#member`          |
| `user_type`         | Text, 7   | `user` for an object, or `userset` for a typed wildcard or a userset   |
| `condition_name`    | Text, 256 | The name of the condition of the tuple, or `NULL`                      |
| `condition_context` | Bytes     | The context of the condition, as a serialized `google.protobuf.Struct` |
| `inserted_at`       | Timestamp | The time of the write                                                  |

The primary key is `id`. The unique index `ux_spectrum_tuple_key` holds `store`, `object_type`, `object_id`, `relation`
and `_user`, which are OpenFGA's primary key, and `ix_spectrum_tuple_user_lookup` holds `store`, `_user`, `relation`,
`object_type` and `object_id` for lists from a user. OpenFGA's tuple table has a ULID of each tuple in place of `id`;
Read pages by `id`. OpenFGA also has partial indexes by `user_type`, which MariaDB cannot create, so Spectrum leaves them out.

The triggers of the table keep the changelog: `record_write` writes the change of each insert, and `record_delete` the
change of each delete, in the transaction of the write. `refuse_update` refuses an update, because a tuple changes by a
delete and a write, which the changelog records.

## spectrum_changelog

| Column              | Type      | Description                                                       |
| ------------------- | --------- | ----------------------------------------------------------------- |
| `id`                | Integer   | The change ID, which the database gives each change in order      |
| `store`             | Text, 26  | The store                                                         |
| `object_type`       | Text, 256 | The type of the object                                            |
| `object_id`         | Text, 256 | The ID of the object                                              |
| `relation`          | Text, 50  | The relation                                                      |
| `_user`             | Text, 512 | The user                                                          |
| `condition_name`    | Text, 256 | The name of the condition of a write, or `NULL`                   |
| `condition_context` | Bytes     | The context of the condition of a write, or `NULL`                |
| `operation`         | Integer   | `0` for a write, `1` for a delete, as `openfga.v1.TupleOperation` |
| `inserted_at`       | Timestamp | The time of the change                                            |

The primary key is `id`, and the index `ix_spectrum_changelog_store` holds `store` and `id`. OpenFGA's changelog has a
ULID of each change in place of `id`. The triggers of `spectrum_tuple` write the rows, and a database cannot make a
ULID, so it numbers the changes instead. A write runs one statement for each tuple, so the numbers follow the order of
the request on every database. ReadChanges pages by `id`, and reads the changes after a start time by `inserted_at`.

## spectrum_assertion

| Column                   | Type     | Description                                                |
| ------------------------ | -------- | ---------------------------------------------------------- |
| `store`                  | Text, 26 | The store                                                  |
| `authorization_model_id` | Text, 26 | The model                                                  |
| `assertions`             | Bytes    | The assertions, as a serialized `openfga.v1.Assertions`    |

The primary key is `store` and `authorization_model_id`.
