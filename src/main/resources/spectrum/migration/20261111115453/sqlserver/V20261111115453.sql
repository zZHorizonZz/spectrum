CREATE TABLE [spectrum_store] ([id] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [name] NVARCHAR(64) NOT NULL, [created_at] DATETIME2(6) NOT NULL CONSTRAINT [df_spectrum_store_created_at] DEFAULT SYSUTCDATETIME(), [updated_at] DATETIME2(6) NOT NULL CONSTRAINT [df_spectrum_store_updated_at] DEFAULT SYSUTCDATETIME(), [deleted_at] DATETIME2(6) NULL, CONSTRAINT [pk_spectrum_store] PRIMARY KEY ([id]));

DECLARE @spectrum_schema_1 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A store. Deleting a store sets `deleted_at` and keeps its rows.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_1, @level1type = N'TABLE', @level1name = N'spectrum_store';

DECLARE @spectrum_schema_2 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The store ID, a ULID.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_2, @level1type = N'TABLE', @level1name = N'spectrum_store', @level2type = N'COLUMN', @level2name = N'id';

DECLARE @spectrum_schema_3 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The time of the last change of the store, which the database writes on each update.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_3, @level1type = N'TABLE', @level1name = N'spectrum_store', @level2type = N'COLUMN', @level2name = N'updated_at';

CREATE TABLE [spectrum_authorization_model] ([store] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [authorization_model_id] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [schema_version] NVARCHAR(5) NOT NULL, [serialized_protobuf] VARBINARY(MAX) NOT NULL, CONSTRAINT [pk_spectrum_authorization_model] PRIMARY KEY ([store], [authorization_model_id]));

DECLARE @spectrum_schema_5 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'An authorization model of a store. Models never change after they are written, and the database refuses an update.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_5, @level1type = N'TABLE', @level1name = N'spectrum_authorization_model';

DECLARE @spectrum_schema_6 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The model ID, a ULID. The latest model of a store has the greatest ID.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_6, @level1type = N'TABLE', @level1name = N'spectrum_authorization_model', @level2type = N'COLUMN', @level2name = N'authorization_model_id';

DECLARE @spectrum_schema_7 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The model, as a serialized `openfga.v1.AuthorizationModel`.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_7, @level1type = N'TABLE', @level1name = N'spectrum_authorization_model', @level2type = N'COLUMN', @level2name = N'serialized_protobuf';

CREATE TABLE [spectrum_tuple] ([id] BIGINT IDENTITY(1, 1) NOT NULL, [store] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [object_type] NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL, [object_id] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [relation] NVARCHAR(50) COLLATE Latin1_General_100_BIN2 NOT NULL, [_user] NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL, [user_type] NVARCHAR(7) NOT NULL, [condition_name] NVARCHAR(256) NULL, [condition_context] VARBINARY(MAX) NULL, [inserted_at] DATETIME2(6) NOT NULL CONSTRAINT [df_spectrum_tuple_inserted_at] DEFAULT SYSUTCDATETIME(), CONSTRAINT [pk_spectrum_tuple] PRIMARY KEY ([id]));

CREATE UNIQUE INDEX [ux_spectrum_tuple_key] ON [spectrum_tuple] ([store], [object_type], [object_id], [relation], [_user]);

CREATE INDEX [ix_spectrum_tuple_user_lookup] ON [spectrum_tuple] ([store], [_user], [relation], [object_type], [object_id]);

DECLARE @spectrum_schema_11 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A relationship tuple of a store. The database records each write and delete of a tuple in the changelog, in the
transaction of the change, and refuses an update: a tuple changes by a delete and a write.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_11, @level1type = N'TABLE', @level1name = N'spectrum_tuple';

DECLARE @spectrum_schema_12 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The tuple ID, which the database gives each tuple in the order it writes them. Read pages by ID.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_12, @level1type = N'TABLE', @level1name = N'spectrum_tuple', @level2type = N'COLUMN', @level2name = N'id';

DECLARE @spectrum_schema_13 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The user: an object such as `user:anne`, a typed wildcard such as `user:*`, or a userset such as
`group:eng#member`.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_13, @level1type = N'TABLE', @level1name = N'spectrum_tuple', @level2type = N'COLUMN', @level2name = N'_user';

DECLARE @spectrum_schema_14 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'`user` for an object, or `userset` for a typed wildcard or a userset.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_14, @level1type = N'TABLE', @level1name = N'spectrum_tuple', @level2type = N'COLUMN', @level2name = N'user_type';

DECLARE @spectrum_schema_15 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The condition context, as a serialized `google.protobuf.Struct`.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_15, @level1type = N'TABLE', @level1name = N'spectrum_tuple', @level2type = N'COLUMN', @level2name = N'condition_context';

CREATE TABLE [spectrum_changelog] ([id] BIGINT IDENTITY(1, 1) NOT NULL, [store] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [object_type] NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL, [object_id] NVARCHAR(256) NOT NULL, [relation] NVARCHAR(50) NOT NULL, [_user] NVARCHAR(512) NOT NULL, [condition_name] NVARCHAR(256) NULL, [condition_context] VARBINARY(MAX) NULL, [operation] INT NOT NULL, [inserted_at] DATETIME2(6) NOT NULL CONSTRAINT [df_spectrum_changelog_inserted_at] DEFAULT SYSUTCDATETIME(), CONSTRAINT [pk_spectrum_changelog] PRIMARY KEY ([id]));

CREATE INDEX [ix_spectrum_changelog_store] ON [spectrum_changelog] ([store], [id]);

DECLARE @spectrum_schema_18 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A write or a delete of a tuple of a store, which the triggers of the tuple table record in the transaction of the
change.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_18, @level1type = N'TABLE', @level1name = N'spectrum_changelog';

DECLARE @spectrum_schema_19 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The change ID, which the database gives each change in the order it records them. Changes are in order of ID.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_19, @level1type = N'TABLE', @level1name = N'spectrum_changelog', @level2type = N'COLUMN', @level2name = N'id';

DECLARE @spectrum_schema_20 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The condition context, as a serialized `google.protobuf.Struct`.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_20, @level1type = N'TABLE', @level1name = N'spectrum_changelog', @level2type = N'COLUMN', @level2name = N'condition_context';

DECLARE @spectrum_schema_21 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The `openfga.v1.TupleOperation` number: 0 for a write, 1 for a delete.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_21, @level1type = N'TABLE', @level1name = N'spectrum_changelog', @level2type = N'COLUMN', @level2name = N'operation';

CREATE TABLE [spectrum_assertion] ([store] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [authorization_model_id] NVARCHAR(26) COLLATE Latin1_General_100_BIN2 NOT NULL, [assertions] VARBINARY(MAX) NOT NULL, CONSTRAINT [pk_spectrum_assertion] PRIMARY KEY ([store], [authorization_model_id]));

DECLARE @spectrum_schema_23 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The assertions of an authorization model.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_23, @level1type = N'TABLE', @level1name = N'spectrum_assertion';

DECLARE @spectrum_schema_24 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The assertions, as a serialized `openfga.v1.Assertions` message.', @level0type = N'SCHEMA', @level0name = @spectrum_schema_24, @level1type = N'TABLE', @level1name = N'spectrum_assertion', @level2type = N'COLUMN', @level2name = N'assertions';
GO

CREATE TRIGGER [tg_spectrum_store_update] ON [spectrum_store] AFTER UPDATE AS
BEGIN
  SET NOCOUNT ON;
  IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;
  UPDATE [t] SET [updated_at] = SYSUTCDATETIME() FROM [spectrum_store] AS [t] JOIN inserted AS [i] ON [t].[id] = [i].[id] LEFT JOIN deleted AS [d] ON [t].[id] = [d].[id];
END;
GO

CREATE TRIGGER [tg_spectrum_authorization_model_update] ON [spectrum_authorization_model] AFTER UPDATE AS
BEGIN
  SET NOCOUNT ON;
  IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;
  IF EXISTS (SELECT 1 FROM [spectrum_authorization_model] AS [t] JOIN inserted AS [i] ON [t].[store] = [i].[store] AND [t].[authorization_model_id] = [i].[authorization_model_id] LEFT JOIN deleted AS [d] ON [t].[store] = [d].[store] AND [t].[authorization_model_id] = [d].[authorization_model_id] WHERE (NOT (0 = 1))) THROW 50000, N'An authorization model never changes.', 1;
END;
GO

CREATE TRIGGER [tg_spectrum_tuple_insert] ON [spectrum_tuple] AFTER INSERT AS
BEGIN
  SET NOCOUNT ON;
  IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;
  INSERT INTO [spectrum_changelog] ([store], [object_type], [object_id], [relation], [_user], [condition_name], [condition_context], [operation]) SELECT [t].[store], [t].[object_type], [t].[object_id], [t].[relation], [t].[_user], [t].[condition_name], [t].[condition_context], 0 FROM [spectrum_tuple] AS [t] JOIN inserted AS [i] ON [t].[id] = [i].[id];
END;
GO

CREATE TRIGGER [tg_spectrum_tuple_update] ON [spectrum_tuple] AFTER UPDATE AS
BEGIN
  SET NOCOUNT ON;
  IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;
  IF EXISTS (SELECT 1 FROM [spectrum_tuple] AS [t] JOIN inserted AS [i] ON [t].[id] = [i].[id] LEFT JOIN deleted AS [d] ON [t].[id] = [d].[id] WHERE (NOT (0 = 1))) THROW 50000, N'A tuple never changes: delete it and write it again.', 1;
END;
GO

CREATE TRIGGER [tg_spectrum_tuple_delete] ON [spectrum_tuple] AFTER DELETE AS
BEGIN
  SET NOCOUNT ON;
  IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;
  INSERT INTO [spectrum_changelog] ([store], [object_type], [object_id], [relation], [_user], [operation]) SELECT [d].[store], [d].[object_type], [d].[object_id], [d].[relation], [d].[_user], 1 FROM deleted AS [d];
END;
