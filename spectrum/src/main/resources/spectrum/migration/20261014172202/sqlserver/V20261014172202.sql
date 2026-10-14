CREATE TABLE [spectrum_store] ([id] NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL, [name] NVARCHAR(255) NOT NULL, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_spectrum_store_create_time] DEFAULT SYSUTCDATETIME(), CONSTRAINT [pk_spectrum_store] PRIMARY KEY ([id]));

DECLARE @leadseal_schema_1 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A store: a set of relationships and the models that combine them, apart from those of every other store, as OpenFGA
says it. Spectrum gives a store its id when it creates it; the database sets `create_time`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_1, @level1type = N'TABLE', @level1name = N'spectrum_store';

DECLARE @leadseal_schema_2 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The id, a ULID, for example `01JAZ6RE1H6ZQ4WKQ2M0R0S8XQ`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_2, @level1type = N'TABLE', @level1name = N'spectrum_store', @level2type = N'COLUMN', @level2name = N'id';

DECLARE @leadseal_schema_3 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The name, for example `documents`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_3, @level1type = N'TABLE', @level1name = N'spectrum_store', @level2type = N'COLUMN', @level2name = N'name';

CREATE TABLE [spectrum_relationship] ([id] UNIQUEIDENTIFIER NOT NULL CONSTRAINT [df_spectrum_relationship_id] DEFAULT NEWID(), [store_id] NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL, [object] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [relation] NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL, [subject] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_spectrum_relationship_create_time] DEFAULT SYSUTCDATETIME(), CONSTRAINT [pk_spectrum_relationship] PRIMARY KEY ([id]));

CREATE UNIQUE INDEX [ux_spectrum_relationship_store_id_object_relation_subject] ON [spectrum_relationship] ([store_id], [object], [relation], [subject]);

CREATE INDEX [ix_spectrum_relationship_store_id_subject_relation_object] ON [spectrum_relationship] ([store_id], [subject], [relation], [object]);

DECLARE @leadseal_schema_7 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A relationship: the subject has the relation to the object in a store, a relation tuple of Zanzibar, as the
relationship table keeps it. The migrations of the table are in `spectrum/migration`. A write sets `object`,
`relation` and `subject` in the store of the relationships; the database sets `id` and `create_time`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_7, @level1type = N'TABLE', @level1name = N'spectrum_relationship';

DECLARE @leadseal_schema_8 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The store, for example `01JAZ6RE1H6ZQ4WKQ2M0R0S8XQ`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_8, @level1type = N'TABLE', @level1name = N'spectrum_relationship', @level2type = N'COLUMN', @level2name = N'store_id';

DECLARE @leadseal_schema_9 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The object, as `type:id`, for example `doc:9`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_9, @level1type = N'TABLE', @level1name = N'spectrum_relationship', @level2type = N'COLUMN', @level2name = N'object';

DECLARE @leadseal_schema_10 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The relation, for example `viewer`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_10, @level1type = N'TABLE', @level1name = N'spectrum_relationship', @level2type = N'COLUMN', @level2name = N'relation';

DECLARE @leadseal_schema_11 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The subject: an object (`user:ana`), every object of a type (`user:*`), or the subjects of a relation of an object
(`group:eng#member`).', @level0type = N'SCHEMA', @level0name = @leadseal_schema_11, @level1type = N'TABLE', @level1name = N'spectrum_relationship', @level2type = N'COLUMN', @level2name = N'subject';

CREATE TABLE [spectrum_model_assertions] ([model_id] NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL, [store_id] NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL, [definition] NVARCHAR(MAX) NOT NULL, CONSTRAINT [pk_spectrum_model_assertions] PRIMARY KEY ([model_id]));

CREATE INDEX [ix_spectrum_model_assertions_store_id] ON [spectrum_model_assertions] ([store_id]);

DECLARE @leadseal_schema_14 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The assertions of a model, as the assertion table keeps them: one row per model that has assertions.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_14, @level1type = N'TABLE', @level1name = N'spectrum_model_assertions';

DECLARE @leadseal_schema_15 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The model.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_15, @level1type = N'TABLE', @level1name = N'spectrum_model_assertions', @level2type = N'COLUMN', @level2name = N'model_id';

DECLARE @leadseal_schema_16 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The store of the model.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_16, @level1type = N'TABLE', @level1name = N'spectrum_model_assertions', @level2type = N'COLUMN', @level2name = N'store_id';

DECLARE @leadseal_schema_17 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The assertions, as the protobuf JSON of this message with only `assertions` set.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_17, @level1type = N'TABLE', @level1name = N'spectrum_model_assertions', @level2type = N'COLUMN', @level2name = N'definition';

CREATE TABLE [spectrum_store_model] ([id] NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL, [store_id] NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL, [definition] NVARCHAR(MAX) NOT NULL, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_spectrum_store_model_create_time] DEFAULT SYSUTCDATETIME(), CONSTRAINT [pk_spectrum_store_model] PRIMARY KEY ([id]));

CREATE INDEX [ix_spectrum_store_model_store_id_id] ON [spectrum_store_model] ([store_id], [id]);

DECLARE @leadseal_schema_20 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A model that a store keeps, as the model table keeps it. A store keeps each model it is given, and the model with the
greatest id is its latest. Spectrum gives a model its id when the store keeps it; the database sets `create_time`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_20, @level1type = N'TABLE', @level1name = N'spectrum_store_model';

DECLARE @leadseal_schema_21 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The id, a ULID, for example `01JAZ6S3N6T3Y5W1Z8PKV9B4CD`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_21, @level1type = N'TABLE', @level1name = N'spectrum_store_model', @level2type = N'COLUMN', @level2name = N'id';

DECLARE @leadseal_schema_22 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The store.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_22, @level1type = N'TABLE', @level1name = N'spectrum_store_model', @level2type = N'COLUMN', @level2name = N'store_id';

DECLARE @leadseal_schema_23 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The model, as protobuf JSON.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_23, @level1type = N'TABLE', @level1name = N'spectrum_store_model', @level2type = N'COLUMN', @level2name = N'definition';

CREATE TABLE [spectrum_change] ([id] NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL, [store_id] NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL, [object] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [relation] NVARCHAR(64) NOT NULL, [subject] NVARCHAR(255) NOT NULL, [operation] NVARCHAR(32) NOT NULL, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_spectrum_change_create_time] DEFAULT SYSUTCDATETIME(), CONSTRAINT [pk_spectrum_change] PRIMARY KEY ([id]));

CREATE INDEX [ix_spectrum_change_store_id_id] ON [spectrum_change] ([store_id], [id]);

DECLARE @leadseal_schema_26 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A change of a relationship in a store, as the change table keeps it: a write of a relationship that the store did
not have, or a delete of one that it had. A change is written in the transaction of the write or the delete, so the
changes of a store are exactly the writes and deletes that committed. Spectrum gives a change a ULID as its id, so
that the changes of a store are in order of id; the database sets `create_time`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_26, @level1type = N'TABLE', @level1name = N'spectrum_change';

DECLARE @leadseal_schema_27 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The id, a ULID, for example `01M4XNYW5SK4DFCPRPE7SHW5H0`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_27, @level1type = N'TABLE', @level1name = N'spectrum_change', @level2type = N'COLUMN', @level2name = N'id';

DECLARE @leadseal_schema_28 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The store.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_28, @level1type = N'TABLE', @level1name = N'spectrum_change', @level2type = N'COLUMN', @level2name = N'store_id';

DECLARE @leadseal_schema_29 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The object of the relationship, for example `doc:9`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_29, @level1type = N'TABLE', @level1name = N'spectrum_change', @level2type = N'COLUMN', @level2name = N'object';

DECLARE @leadseal_schema_30 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The relation of the relationship, for example `viewer`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_30, @level1type = N'TABLE', @level1name = N'spectrum_change', @level2type = N'COLUMN', @level2name = N'relation';

DECLARE @leadseal_schema_31 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The subject of the relationship, for example `user:ana`.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_31, @level1type = N'TABLE', @level1name = N'spectrum_change', @level2type = N'COLUMN', @level2name = N'subject';

DECLARE @leadseal_schema_32 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Whether the relationship was written or deleted.', @level0type = N'SCHEMA', @level0name = @leadseal_schema_32, @level1type = N'TABLE', @level1name = N'spectrum_change', @level2type = N'COLUMN', @level2name = N'operation';
