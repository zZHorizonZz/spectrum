CREATE TABLE `spectrum_store` (`id` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'The id, a ULID, for example `01JAZ6RE1H6ZQ4WKQ2M0R0S8XQ`.', `name` VARCHAR(255) NOT NULL COMMENT 'The name, for example `documents`.', `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), CONSTRAINT `pk_spectrum_store` PRIMARY KEY (`id`));

ALTER TABLE `spectrum_store` COMMENT = 'A store: a set of relationships and the models that combine them, apart from those of every other store, as OpenFGA
says it. Spectrum gives a store its id when it creates it; the database sets `create_time`.';

CREATE TABLE `spectrum_relationship` (`id` UUID NOT NULL DEFAULT (UUID()), `store_id` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'The store, for example `01JAZ6RE1H6ZQ4WKQ2M0R0S8XQ`.', `object` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT 'The object, as `type:id`, for example `doc:9`.', `relation` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT 'The relation, for example `viewer`.', `subject` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT 'The subject: an object (`user:ana`), every object of a type (`user:*`), or the subjects of a relation of an object
(`group:eng#member`).', `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), CONSTRAINT `pk_spectrum_relationship` PRIMARY KEY (`id`));

CREATE UNIQUE INDEX `ux_spectrum_relationship_store_id_object_relation_subject` ON `spectrum_relationship` (`store_id`, `object`, `relation`, `subject`);

CREATE INDEX `ix_spectrum_relationship_store_id_subject_relation_object` ON `spectrum_relationship` (`store_id`, `subject`, `relation`, `object`);

ALTER TABLE `spectrum_relationship` COMMENT = 'A relationship: the subject has the relation to the object in a store, a relation tuple of Zanzibar, as the
relationship table keeps it. The migrations of the table are in `spectrum/migration`. A write sets `object`,
`relation` and `subject` in the store of the relationships; the database sets `id` and `create_time`.';

CREATE TABLE `spectrum_model_assertions` (`model_id` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'The model.', `store_id` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'The store of the model.', `definition` LONGTEXT NOT NULL COMMENT 'The assertions, as the protobuf JSON of this message with only `assertions` set.', CONSTRAINT `pk_spectrum_model_assertions` PRIMARY KEY (`model_id`));

CREATE INDEX `ix_spectrum_model_assertions_store_id` ON `spectrum_model_assertions` (`store_id`);

ALTER TABLE `spectrum_model_assertions` COMMENT = 'The assertions of a model, as the assertion table keeps them: one row per model that has assertions.';

CREATE TABLE `spectrum_store_model` (`id` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'The id, a ULID, for example `01JAZ6S3N6T3Y5W1Z8PKV9B4CD`.', `store_id` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'The store.', `definition` LONGTEXT NOT NULL COMMENT 'The model, as protobuf JSON.', `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), CONSTRAINT `pk_spectrum_store_model` PRIMARY KEY (`id`));

CREATE INDEX `ix_spectrum_store_model_store_id_id` ON `spectrum_store_model` (`store_id`, `id`);

ALTER TABLE `spectrum_store_model` COMMENT = 'A model that a store keeps, as the model table keeps it. A store keeps each model it is given, and the model with the
greatest id is its latest. Spectrum gives a model its id when the store keeps it; the database sets `create_time`.';

CREATE TABLE `spectrum_change` (`id` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'The id, a ULID, for example `01M4XNYW5SK4DFCPRPE7SHW5H0`.', `store_id` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'The store.', `object` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT 'The object of the relationship, for example `doc:9`.', `relation` VARCHAR(64) NOT NULL COMMENT 'The relation of the relationship, for example `viewer`.', `subject` VARCHAR(255) NOT NULL COMMENT 'The subject of the relationship, for example `user:ana`.', `operation` VARCHAR(32) NOT NULL COMMENT 'Whether the relationship was written or deleted.', `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), CONSTRAINT `pk_spectrum_change` PRIMARY KEY (`id`));

CREATE INDEX `ix_spectrum_change_store_id_id` ON `spectrum_change` (`store_id`, `id`);

ALTER TABLE `spectrum_change` COMMENT = 'A change of a relationship in a store, as the change table keeps it: a write of a relationship that the store did
not have, or a delete of one that it had. A change is written in the transaction of the write or the delete, so the
changes of a store are exactly the writes and deletes that committed. Spectrum gives a change a ULID as its id, so
that the changes of a store are in order of id; the database sets `create_time`.';
