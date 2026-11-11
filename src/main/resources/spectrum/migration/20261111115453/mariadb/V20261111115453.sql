CREATE TABLE `spectrum_store` (`id` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL COMMENT 'The store ID, a ULID.', `name` VARCHAR(64) NOT NULL, `created_at` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `updated_at` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'The time of the last change of the store, which the database writes on each update.', `deleted_at` DATETIME(6) NULL, CONSTRAINT `pk_spectrum_store` PRIMARY KEY (`id`));

ALTER TABLE `spectrum_store` COMMENT = 'A store. Deleting a store sets `deleted_at` and keeps its rows.';

CREATE TABLE `spectrum_authorization_model` (`store` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL, `authorization_model_id` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL COMMENT 'The model ID, a ULID. The latest model of a store has the greatest ID.', `schema_version` VARCHAR(5) NOT NULL, `serialized_protobuf` LONGBLOB NOT NULL COMMENT 'The model, as a serialized `openfga.v1.AuthorizationModel`.', CONSTRAINT `pk_spectrum_authorization_model` PRIMARY KEY (`store`, `authorization_model_id`));

ALTER TABLE `spectrum_authorization_model` COMMENT = 'An authorization model of a store. Models never change after they are written, and the database refuses an update.';

CREATE TABLE `spectrum_tuple` (`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'The tuple ID, which the database gives each tuple in the order it writes them. Read pages by ID.', `store` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL, `object_type` VARCHAR(128) COLLATE utf8mb4_bin NOT NULL, `object_id` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `relation` VARCHAR(50) COLLATE utf8mb4_bin NOT NULL, `_user` VARCHAR(256) COLLATE utf8mb4_bin NOT NULL COMMENT 'The user: an object such as `user:anne`, a typed wildcard such as `user:*`, or a userset such as
`group:eng#member`.', `user_type` VARCHAR(7) NOT NULL COMMENT '`user` for an object, or `userset` for a typed wildcard or a userset.', `condition_name` VARCHAR(256) NULL, `condition_context` LONGBLOB NULL COMMENT 'The condition context, as a serialized `google.protobuf.Struct`.', `inserted_at` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), CONSTRAINT `pk_spectrum_tuple` PRIMARY KEY (`id`));

CREATE UNIQUE INDEX `ux_spectrum_tuple_key` ON `spectrum_tuple` (`store`, `object_type`, `object_id`, `relation`, `_user`);

CREATE INDEX `ix_spectrum_tuple_user_lookup` ON `spectrum_tuple` (`store`, `_user`, `relation`, `object_type`, `object_id`);

ALTER TABLE `spectrum_tuple` COMMENT = 'A relationship tuple of a store. The database records each write and delete of a tuple in the changelog, in the
transaction of the change, and refuses an update: a tuple changes by a delete and a write.';

CREATE TABLE `spectrum_changelog` (`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'The change ID, which the database gives each change in the order it records them. Changes are in order of ID.', `store` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL, `object_type` VARCHAR(256) COLLATE utf8mb4_bin NOT NULL, `object_id` VARCHAR(256) NOT NULL, `relation` VARCHAR(50) NOT NULL, `_user` VARCHAR(512) NOT NULL, `condition_name` VARCHAR(256) NULL, `condition_context` LONGBLOB NULL COMMENT 'The condition context, as a serialized `google.protobuf.Struct`.', `operation` INT NOT NULL COMMENT 'The `openfga.v1.TupleOperation` number: 0 for a write, 1 for a delete.', `inserted_at` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), CONSTRAINT `pk_spectrum_changelog` PRIMARY KEY (`id`));

CREATE INDEX `ix_spectrum_changelog_store` ON `spectrum_changelog` (`store`, `id`);

ALTER TABLE `spectrum_changelog` COMMENT = 'A write or a delete of a tuple of a store, which the triggers of the tuple table record in the transaction of the
change.';

CREATE TABLE `spectrum_assertion` (`store` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL, `authorization_model_id` VARCHAR(26) COLLATE utf8mb4_bin NOT NULL, `assertions` LONGBLOB NOT NULL COMMENT 'The assertions, as a serialized `openfga.v1.Assertions` message.', CONSTRAINT `pk_spectrum_assertion` PRIMARY KEY (`store`, `authorization_model_id`));

ALTER TABLE `spectrum_assertion` COMMENT = 'The assertions of an authorization model.';

CREATE TRIGGER `rw_spectrum_store` BEFORE UPDATE ON `spectrum_store` FOR EACH ROW BEGIN
  SET NEW.`updated_at` = (UTC_TIMESTAMP(6));
END;

CREATE TRIGGER `tg_spectrum_authorization_model_update` AFTER UPDATE ON `spectrum_authorization_model` FOR EACH ROW BEGIN
  IF (NOT FALSE) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'An authorization model never changes.';
  END IF;
END;

CREATE TRIGGER `tg_spectrum_tuple_insert` AFTER INSERT ON `spectrum_tuple` FOR EACH ROW BEGIN
  INSERT INTO `spectrum_changelog` (`store`, `object_type`, `object_id`, `relation`, `_user`, `condition_name`, `condition_context`, `operation`) VALUES (NEW.`store`, NEW.`object_type`, NEW.`object_id`, NEW.`relation`, NEW.`_user`, NEW.`condition_name`, NEW.`condition_context`, 0);
END;

CREATE TRIGGER `tg_spectrum_tuple_update` AFTER UPDATE ON `spectrum_tuple` FOR EACH ROW BEGIN
  IF (NOT FALSE) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A tuple never changes: delete it and write it again.';
  END IF;
END;

CREATE TRIGGER `tg_spectrum_tuple_delete` AFTER DELETE ON `spectrum_tuple` FOR EACH ROW BEGIN
  INSERT INTO `spectrum_changelog` (`store`, `object_type`, `object_id`, `relation`, `_user`, `operation`) VALUES (OLD.`store`, OLD.`object_type`, OLD.`object_id`, OLD.`relation`, OLD.`_user`, 1);
END;
