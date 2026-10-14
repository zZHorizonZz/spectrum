CREATE TABLE "store" ("id" TEXT NOT NULL, "name" TEXT NOT NULL, "created_at" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')), "updated_at" TEXT, "deleted_at" TEXT, CONSTRAINT "pk_store" PRIMARY KEY ("id"));

CREATE TABLE "authorization_model" ("store" TEXT NOT NULL, "authorization_model_id" TEXT NOT NULL, "schema_version" TEXT NOT NULL, "serialized_protobuf" BLOB NOT NULL, CONSTRAINT "pk_authorization_model" PRIMARY KEY ("store", "authorization_model_id"));

CREATE TABLE "tuple" ("store" TEXT NOT NULL, "object_type" TEXT NOT NULL, "object_id" TEXT NOT NULL, "relation" TEXT NOT NULL, "_user" TEXT NOT NULL, "user_type" TEXT NOT NULL, "condition_name" TEXT, "condition_context" BLOB, "ulid" TEXT NOT NULL, "inserted_at" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')), CONSTRAINT "pk_tuple" PRIMARY KEY ("store", "object_type", "object_id", "relation", "_user"));

CREATE UNIQUE INDEX "ux_tuple_ulid" ON "tuple" ("ulid");

CREATE INDEX "ix_tuple_user_lookup" ON "tuple" ("store", "_user", "relation", "object_type", "object_id");

CREATE TABLE "changelog" ("store" TEXT NOT NULL, "ulid" TEXT NOT NULL, "object_type" TEXT NOT NULL, "object_id" TEXT NOT NULL, "relation" TEXT NOT NULL, "_user" TEXT NOT NULL, "condition_name" TEXT, "condition_context" BLOB, "operation" INTEGER NOT NULL, "inserted_at" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')), CONSTRAINT "pk_changelog" PRIMARY KEY ("store", "ulid", "object_type"));

CREATE TABLE "assertion" ("store" TEXT NOT NULL, "authorization_model_id" TEXT NOT NULL, "assertions" BLOB NOT NULL, CONSTRAINT "pk_assertion" PRIMARY KEY ("store", "authorization_model_id"));
