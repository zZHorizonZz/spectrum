CREATE TABLE "spectrum_store" ("id" TEXT NOT NULL, "name" TEXT NOT NULL, "created_at" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "updated_at" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "deleted_at" TEXT, CONSTRAINT "pk_spectrum_store" PRIMARY KEY ("id"));

CREATE TABLE "spectrum_authorization_model" ("store" TEXT NOT NULL, "authorization_model_id" TEXT NOT NULL, "schema_version" TEXT NOT NULL, "serialized_protobuf" BLOB NOT NULL, CONSTRAINT "pk_spectrum_authorization_model" PRIMARY KEY ("store", "authorization_model_id"));

CREATE TABLE "spectrum_tuple" ("id" INTEGER NOT NULL, "store" TEXT NOT NULL, "object_type" TEXT NOT NULL, "object_id" TEXT NOT NULL, "relation" TEXT NOT NULL, "_user" TEXT NOT NULL, "user_type" TEXT NOT NULL, "condition_name" TEXT, "condition_context" BLOB, "inserted_at" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), CONSTRAINT "pk_spectrum_tuple" PRIMARY KEY ("id"));

CREATE UNIQUE INDEX "ux_spectrum_tuple_key" ON "spectrum_tuple" ("store", "object_type", "object_id", "relation", "_user");

CREATE INDEX "ix_spectrum_tuple_user_lookup" ON "spectrum_tuple" ("store", "_user", "relation", "object_type", "object_id");

CREATE TABLE "spectrum_changelog" ("id" INTEGER NOT NULL, "store" TEXT NOT NULL, "object_type" TEXT NOT NULL, "object_id" TEXT NOT NULL, "relation" TEXT NOT NULL, "_user" TEXT NOT NULL, "condition_name" TEXT, "condition_context" BLOB, "operation" INTEGER NOT NULL, "inserted_at" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), CONSTRAINT "pk_spectrum_changelog" PRIMARY KEY ("id"));

CREATE INDEX "ix_spectrum_changelog_store" ON "spectrum_changelog" ("store", "id");

CREATE TABLE "spectrum_assertion" ("store" TEXT NOT NULL, "authorization_model_id" TEXT NOT NULL, "assertions" BLOB NOT NULL, CONSTRAINT "pk_spectrum_assertion" PRIMARY KEY ("store", "authorization_model_id"));

CREATE TRIGGER "tg_spectrum_store_update" AFTER UPDATE ON "spectrum_store" BEGIN
  UPDATE "spectrum_store" SET "updated_at" = (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z') WHERE "spectrum_store"."id" = NEW."id";
END;

CREATE TRIGGER "tg_spectrum_authorization_model_update" AFTER UPDATE ON "spectrum_authorization_model" BEGIN
  SELECT RAISE(ABORT, 'An authorization model never changes.') FROM "spectrum_authorization_model" WHERE "spectrum_authorization_model"."store" = NEW."store" AND "spectrum_authorization_model"."authorization_model_id" = NEW."authorization_model_id" AND (NOT 0);
END;

CREATE TRIGGER "tg_spectrum_tuple_insert" AFTER INSERT ON "spectrum_tuple" BEGIN
  INSERT INTO "spectrum_changelog" ("store", "object_type", "object_id", "relation", "_user", "condition_name", "condition_context", "operation") SELECT "spectrum_tuple"."store", "spectrum_tuple"."object_type", "spectrum_tuple"."object_id", "spectrum_tuple"."relation", "spectrum_tuple"."_user", "spectrum_tuple"."condition_name", "spectrum_tuple"."condition_context", 0 FROM "spectrum_tuple" WHERE "spectrum_tuple"."id" = NEW."id";
END;

CREATE TRIGGER "tg_spectrum_tuple_update" AFTER UPDATE ON "spectrum_tuple" BEGIN
  SELECT RAISE(ABORT, 'A tuple never changes: delete it and write it again.') FROM "spectrum_tuple" WHERE "spectrum_tuple"."id" = NEW."id" AND (NOT 0);
END;

CREATE TRIGGER "tg_spectrum_tuple_delete" AFTER DELETE ON "spectrum_tuple" BEGIN
  INSERT INTO "spectrum_changelog" ("store", "object_type", "object_id", "relation", "_user", "operation") SELECT OLD."store", OLD."object_type", OLD."object_id", OLD."relation", OLD."_user", 1;
END;
