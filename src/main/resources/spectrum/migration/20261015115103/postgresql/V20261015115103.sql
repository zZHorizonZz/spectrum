CREATE TABLE "spectrum_store" ("id" varchar(26) COLLATE "C" NOT NULL, "name" varchar(64) NOT NULL, "created_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "updated_at" timestamptz, "deleted_at" timestamptz, CONSTRAINT "pk_spectrum_store" PRIMARY KEY ("id"));

COMMENT ON TABLE "spectrum_store" IS 'A store. Deleting a store sets `deleted_at` and keeps its rows.';

COMMENT ON COLUMN "spectrum_store"."id" IS 'The store ID, a ULID.';

CREATE TABLE "spectrum_authorization_model" ("store" varchar(26) COLLATE "C" NOT NULL, "authorization_model_id" varchar(26) COLLATE "C" NOT NULL, "schema_version" varchar(5) NOT NULL, "serialized_protobuf" bytea NOT NULL, CONSTRAINT "pk_spectrum_authorization_model" PRIMARY KEY ("store", "authorization_model_id"));

COMMENT ON TABLE "spectrum_authorization_model" IS 'An authorization model of a store. Models never change after they are written.';

COMMENT ON COLUMN "spectrum_authorization_model"."authorization_model_id" IS 'The model ID, a ULID. The latest model of a store has the greatest ID.';

COMMENT ON COLUMN "spectrum_authorization_model"."serialized_protobuf" IS 'The model, as a serialized `openfga.v1.AuthorizationModel`.';

CREATE TABLE "spectrum_tuple" ("store" varchar(26) COLLATE "C" NOT NULL, "object_type" varchar(128) COLLATE "C" NOT NULL, "object_id" varchar(255) COLLATE "C" NOT NULL, "relation" varchar(50) COLLATE "C" NOT NULL, "_user" varchar(256) COLLATE "C" NOT NULL, "user_type" varchar(7) NOT NULL, "condition_name" varchar(256), "condition_context" bytea, "ulid" varchar(26) COLLATE "C" NOT NULL, "inserted_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT "pk_spectrum_tuple" PRIMARY KEY ("store", "object_type", "object_id", "relation", "_user"));

CREATE UNIQUE INDEX "ux_spectrum_tuple_ulid" ON "spectrum_tuple" ("ulid");

CREATE INDEX "ix_spectrum_tuple_user_lookup" ON "spectrum_tuple" ("store", "_user", "relation", "object_type", "object_id");

COMMENT ON TABLE "spectrum_tuple" IS 'A relationship tuple of a store.';

COMMENT ON COLUMN "spectrum_tuple"."_user" IS 'The user: an object such as `user:anne`, a typed wildcard such as `user:*`, or a userset such as
`group:eng#member`.';

COMMENT ON COLUMN "spectrum_tuple"."user_type" IS '`user` for an object, or `userset` for a typed wildcard or a userset.';

COMMENT ON COLUMN "spectrum_tuple"."condition_context" IS 'The condition context, as a serialized `google.protobuf.Struct`.';

COMMENT ON COLUMN "spectrum_tuple"."ulid" IS 'The tuple ID, a ULID of the time of the write.';

CREATE TABLE "spectrum_changelog" ("store" varchar(26) COLLATE "C" NOT NULL, "ulid" varchar(26) COLLATE "C" NOT NULL, "object_type" varchar(256) COLLATE "C" NOT NULL, "object_id" varchar(256) NOT NULL, "relation" varchar(50) NOT NULL, "_user" varchar(512) NOT NULL, "condition_name" varchar(256), "condition_context" bytea, "operation" integer NOT NULL, "inserted_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT "pk_spectrum_changelog" PRIMARY KEY ("store", "ulid", "object_type"));

COMMENT ON TABLE "spectrum_changelog" IS 'A write or a delete of a tuple of a store, in the transaction of the change.';

COMMENT ON COLUMN "spectrum_changelog"."ulid" IS 'The change ID, a ULID of the time of the change. Changes are in order of ID.';

COMMENT ON COLUMN "spectrum_changelog"."condition_context" IS 'The condition context, as a serialized `google.protobuf.Struct`.';

COMMENT ON COLUMN "spectrum_changelog"."operation" IS 'The `openfga.v1.TupleOperation` number: 0 for a write, 1 for a delete.';

CREATE TABLE "spectrum_assertion" ("store" varchar(26) COLLATE "C" NOT NULL, "authorization_model_id" varchar(26) COLLATE "C" NOT NULL, "assertions" bytea NOT NULL, CONSTRAINT "pk_spectrum_assertion" PRIMARY KEY ("store", "authorization_model_id"));

COMMENT ON TABLE "spectrum_assertion" IS 'The assertions of an authorization model.';

COMMENT ON COLUMN "spectrum_assertion"."assertions" IS 'The assertions, as a serialized `openfga.v1.Assertions` message.';
