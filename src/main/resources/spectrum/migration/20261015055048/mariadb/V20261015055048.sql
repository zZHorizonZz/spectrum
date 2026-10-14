CREATE TABLE `store` (`id` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL COMMENT 'The store ID, a ULID.', `name` VARCHAR(64) NOT NULL, `created_at` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `updated_at` DATETIME(6) NULL, `deleted_at` DATETIME(6) NULL, CONSTRAINT `pk_store` PRIMARY KEY (`id`));

ALTER TABLE `store` COMMENT = 'A store. Deleting a store sets `deleted_at` and keeps its rows, as OpenFGA does.';

CREATE TABLE `authorization_model` (`store` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL, `authorization_model_id` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL COMMENT 'The model ID, a ULID. The latest model of a store has the greatest ID.', `schema_version` VARCHAR(5) NOT NULL, `serialized_protobuf` LONGBLOB NOT NULL COMMENT 'The model, as a serialized `openfga.v1.AuthorizationModel`.', CONSTRAINT `pk_authorization_model` PRIMARY KEY (`store`, `authorization_model_id`));

ALTER TABLE `authorization_model` COMMENT = 'An authorization model of a store. Models never change after they are written.';

CREATE TABLE `tuple` (`store` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL, `object_type` VARCHAR(128) COLLATE utf8mb4_bin NOT NULL, `object_id` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `relation` VARCHAR(50) COLLATE utf8mb4_bin NOT NULL, `_user` VARCHAR(256) COLLATE utf8mb4_bin NOT NULL COMMENT 'The user: an object such as `user:anne`, a typed wildcard such as `user:*`, or a userset such as
`group:eng#member`.', `user_type` VARCHAR(7) NOT NULL COMMENT '`user` for an object, or `userset` for a typed wildcard or a userset.', `condition_name` VARCHAR(256) NULL, `condition_context` LONGBLOB NULL COMMENT 'The condition context, as a serialized `google.protobuf.Struct`.', `ulid` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL COMMENT 'The tuple ID, a ULID of the time of the write.', `inserted_at` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), CONSTRAINT `pk_tuple` PRIMARY KEY (`store`, `object_type`, `object_id`, `relation`, `_user`));

CREATE UNIQUE INDEX `ux_tuple_ulid` ON `tuple` (`ulid`);

CREATE INDEX `ix_tuple_user_lookup` ON `tuple` (`store`, `_user`, `relation`, `object_type`, `object_id`);

ALTER TABLE `tuple` COMMENT = 'A relationship tuple of a store.';

CREATE TABLE `changelog` (`store` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL, `ulid` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL COMMENT 'The change ID, a ULID of the time of the change. Changes are in order of ID.', `object_type` VARCHAR(256) COLLATE utf8mb4_bin NOT NULL, `object_id` VARCHAR(256) NOT NULL, `relation` VARCHAR(50) NOT NULL, `_user` VARCHAR(512) NOT NULL, `condition_name` VARCHAR(256) NULL, `condition_context` LONGBLOB NULL COMMENT 'The condition context, as a serialized `google.protobuf.Struct`.', `operation` INT NOT NULL COMMENT 'The `openfga.v1.TupleOperation` number: 0 for a write, 1 for a delete.', `inserted_at` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), CONSTRAINT `pk_changelog` PRIMARY KEY (`store`, `ulid`, `object_type`));

ALTER TABLE `changelog` COMMENT = 'A write or a delete of a tuple of a store, in the transaction of the change.';

CREATE TABLE `assertion` (`store` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL, `authorization_model_id` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL, `assertions` LONGBLOB NOT NULL COMMENT 'The assertions, as a serialized `openfga.v1.Assertions` message.', CONSTRAINT `pk_assertion` PRIMARY KEY (`store`, `authorization_model_id`));

ALTER TABLE `assertion` COMMENT = 'The assertions of an authorization model.';
