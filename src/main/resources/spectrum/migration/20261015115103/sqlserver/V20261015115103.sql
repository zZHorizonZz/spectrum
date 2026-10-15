CREATE TABLE [spectrum_store] ([id] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [name] NVARCHAR(64) NOT NULL, [created_at] DATETIME2(6) NOT NULL CONSTRAINT [df_spectrum_store_created_at] DEFAULT SYSUTCDATETIME(), [updated_at] DATETIME2(6) NULL, [deleted_at] DATETIME2(6) NULL, CONSTRAINT [pk_spectrum_store] PRIMARY KEY ([id]));

DECLARE @spectrum_schema_1 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A store. Deleting a store sets `deleted_at` and keeps its rows.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_1, @level1type = N'TABLE', @level1name = N'spectrum_store';

DECLARE @spectrum_schema_2 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The store ID, a ULID.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_2, @level1type = N'TABLE', @level1name = N'spectrum_store', @level2type = N'COLUMN', @level2name = N'id';

CREATE TABLE [spectrum_authorization_model] ([store] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [authorization_model_id] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [schema_version] NVARCHAR(5) NOT NULL, [serialized_protobuf] VARBINARY(MAX) NOT NULL, CONSTRAINT [pk_spectrum_authorization_model] PRIMARY KEY ([store], [authorization_model_id]));

DECLARE @spectrum_schema_4 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'An authorization model of a store. Models never change after they are written.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_4, @level1type = N'TABLE', @level1name = N'spectrum_authorization_model';

DECLARE @spectrum_schema_5 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The model ID, a ULID. The latest model of a store has the greatest ID.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_5, @level1type = N'TABLE', @level1name = N'spectrum_authorization_model', @level2type = N'COLUMN', @level2name = N'authorization_model_id';

DECLARE @spectrum_schema_6 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The model, as a serialized `openfga.v1.AuthorizationModel`.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_6, @level1type = N'TABLE', @level1name = N'spectrum_authorization_model', @level2type = N'COLUMN', @level2name = N'serialized_protobuf';

CREATE TABLE [spectrum_tuple] ([store] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [object_type] NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL, [object_id] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [relation] NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NOT NULL, [_user] NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL, [user_type] NVARCHAR(7) NOT NULL, [condition_name] NVARCHAR(256) NULL, [condition_context] VARBINARY(MAX) NULL, [ulid] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [inserted_at] DATETIME2(6) NOT NULL CONSTRAINT [df_spectrum_tuple_inserted_at] DEFAULT SYSUTCDATETIME(), CONSTRAINT [pk_spectrum_tuple] PRIMARY KEY ([store], [object_type], [object_id], [relation], [_user]));

CREATE UNIQUE INDEX [ux_spectrum_tuple_ulid] ON [spectrum_tuple] ([ulid]);

CREATE INDEX [ix_spectrum_tuple_user_lookup] ON [spectrum_tuple] ([store], [_user], [relation], [object_type], [object_id]);

DECLARE @spectrum_schema_10 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A relationship tuple of a store.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_10, @level1type = N'TABLE', @level1name = N'spectrum_tuple';

DECLARE @spectrum_schema_11 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The user: an object such as `user:anne`, a typed wildcard such as `user:*`, or a userset such as
`group:eng#member`.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_11, @level1type = N'TABLE', @level1name = N'spectrum_tuple', @level2type = N'COLUMN', @level2name = N'_user';

DECLARE @spectrum_schema_12 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'`user` for an object, or `userset` for a typed wildcard or a userset.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_12, @level1type = N'TABLE', @level1name = N'spectrum_tuple', @level2type = N'COLUMN', @level2name = N'user_type';

DECLARE @spectrum_schema_13 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The condition context, as a serialized `google.protobuf.Struct`.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_13, @level1type = N'TABLE', @level1name = N'spectrum_tuple', @level2type = N'COLUMN', @level2name = N'condition_context';

DECLARE @spectrum_schema_14 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The tuple ID, a ULID of the time of the write.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_14, @level1type = N'TABLE', @level1name = N'spectrum_tuple', @level2type = N'COLUMN', @level2name = N'ulid';

CREATE TABLE [spectrum_changelog] ([store] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [ulid] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [object_type] NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL, [object_id] NVARCHAR(256) NOT NULL, [relation] NVARCHAR(50) NOT NULL, [_user] NVARCHAR(512) NOT NULL, [condition_name] NVARCHAR(256) NULL, [condition_context] VARBINARY(MAX) NULL, [operation] INT NOT NULL, [inserted_at] DATETIME2(6) NOT NULL CONSTRAINT [df_spectrum_changelog_inserted_at] DEFAULT SYSUTCDATETIME(), CONSTRAINT [pk_spectrum_changelog] PRIMARY KEY ([store], [ulid], [object_type]));

DECLARE @spectrum_schema_16 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A write or a delete of a tuple of a store, in the transaction of the change.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_16, @level1type = N'TABLE', @level1name = N'spectrum_changelog';

DECLARE @spectrum_schema_17 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The change ID, a ULID of the time of the change. Changes are in order of ID.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_17, @level1type = N'TABLE', @level1name = N'spectrum_changelog', @level2type = N'COLUMN', @level2name = N'ulid';

DECLARE @spectrum_schema_18 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The condition context, as a serialized `google.protobuf.Struct`.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_18, @level1type = N'TABLE', @level1name = N'spectrum_changelog', @level2type = N'COLUMN', @level2name = N'condition_context';

DECLARE @spectrum_schema_19 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The `openfga.v1.TupleOperation` number: 0 for a write, 1 for a delete.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_19, @level1type = N'TABLE', @level1name = N'spectrum_changelog', @level2type = N'COLUMN', @level2name = N'operation';

CREATE TABLE [spectrum_assertion] ([store] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [authorization_model_id] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [assertions] VARBINARY(MAX) NOT NULL, CONSTRAINT [pk_spectrum_assertion] PRIMARY KEY ([store], [authorization_model_id]));

DECLARE @spectrum_schema_21 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The assertions of an authorization model.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_21, @level1type = N'TABLE', @level1name = N'spectrum_assertion';

DECLARE @spectrum_schema_22 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The assertions, as a serialized `openfga.v1.Assertions` message.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_22, @level1type = N'TABLE', @level1name = N'spectrum_assertion', @level2type = N'COLUMN', @level2name = N'assertions';
