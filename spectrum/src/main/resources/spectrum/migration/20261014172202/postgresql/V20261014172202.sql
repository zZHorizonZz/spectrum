CREATE TABLE "spectrum_store" ("id" varchar(32) COLLATE "C" NOT NULL, "name" varchar(255) NOT NULL, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT "pk_spectrum_store" PRIMARY KEY ("id"));

COMMENT ON TABLE "spectrum_store" IS 'A store: a set of relationships and the models that combine them, apart from those of every other store, as OpenFGA
says it. Spectrum gives a store its id when it creates it; the database sets `create_time`.';

COMMENT ON COLUMN "spectrum_store"."id" IS 'The id, a ULID, for example `01JAZ6RE1H6ZQ4WKQ2M0R0S8XQ`.';

COMMENT ON COLUMN "spectrum_store"."name" IS 'The name, for example `documents`.';

CREATE TABLE "spectrum_relationship" ("id" uuid NOT NULL DEFAULT gen_random_uuid(), "store_id" varchar(32) COLLATE "C" NOT NULL, "object" varchar(255) COLLATE "C" NOT NULL, "relation" varchar(64) COLLATE "C" NOT NULL, "subject" varchar(255) COLLATE "C" NOT NULL, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT "pk_spectrum_relationship" PRIMARY KEY ("id"));

CREATE UNIQUE INDEX "ux_spectrum_relationship_store_id_object_relation_subject" ON "spectrum_relationship" ("store_id", "object", "relation", "subject");

CREATE INDEX "ix_spectrum_relationship_store_id_subject_relation_object" ON "spectrum_relationship" ("store_id", "subject", "relation", "object");

COMMENT ON TABLE "spectrum_relationship" IS 'A relationship: the subject has the relation to the object in a store, a relation tuple of Zanzibar, as the
relationship table keeps it. The migrations of the table are in `spectrum/migration`. A write sets `object`,
`relation` and `subject` in the store of the relationships; the database sets `id` and `create_time`.';

COMMENT ON COLUMN "spectrum_relationship"."store_id" IS 'The store, for example `01JAZ6RE1H6ZQ4WKQ2M0R0S8XQ`.';

COMMENT ON COLUMN "spectrum_relationship"."object" IS 'The object, as `type:id`, for example `doc:9`.';

COMMENT ON COLUMN "spectrum_relationship"."relation" IS 'The relation, for example `viewer`.';

COMMENT ON COLUMN "spectrum_relationship"."subject" IS 'The subject: an object (`user:ana`), every object of a type (`user:*`), or the subjects of a relation of an object
(`group:eng#member`).';

CREATE TABLE "spectrum_model_assertions" ("model_id" varchar(32) COLLATE "C" NOT NULL, "store_id" varchar(32) COLLATE "C" NOT NULL, "definition" text NOT NULL, CONSTRAINT "pk_spectrum_model_assertions" PRIMARY KEY ("model_id"));

CREATE INDEX "ix_spectrum_model_assertions_store_id" ON "spectrum_model_assertions" ("store_id");

COMMENT ON TABLE "spectrum_model_assertions" IS 'The assertions of a model, as the assertion table keeps them: one row per model that has assertions.';

COMMENT ON COLUMN "spectrum_model_assertions"."model_id" IS 'The model.';

COMMENT ON COLUMN "spectrum_model_assertions"."store_id" IS 'The store of the model.';

COMMENT ON COLUMN "spectrum_model_assertions"."definition" IS 'The assertions, as the protobuf JSON of this message with only `assertions` set.';

CREATE TABLE "spectrum_store_model" ("id" varchar(32) COLLATE "C" NOT NULL, "store_id" varchar(32) COLLATE "C" NOT NULL, "definition" text NOT NULL, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT "pk_spectrum_store_model" PRIMARY KEY ("id"));

CREATE INDEX "ix_spectrum_store_model_store_id_id" ON "spectrum_store_model" ("store_id", "id");

COMMENT ON TABLE "spectrum_store_model" IS 'A model that a store keeps, as the model table keeps it. A store keeps each model it is given, and the model with the
greatest id is its latest. Spectrum gives a model its id when the store keeps it; the database sets `create_time`.';

COMMENT ON COLUMN "spectrum_store_model"."id" IS 'The id, a ULID, for example `01JAZ6S3N6T3Y5W1Z8PKV9B4CD`.';

COMMENT ON COLUMN "spectrum_store_model"."store_id" IS 'The store.';

COMMENT ON COLUMN "spectrum_store_model"."definition" IS 'The model, as protobuf JSON.';

CREATE TABLE "spectrum_change" ("id" varchar(32) COLLATE "C" NOT NULL, "store_id" varchar(32) COLLATE "C" NOT NULL, "object" varchar(255) COLLATE "C" NOT NULL, "relation" varchar(64) NOT NULL, "subject" varchar(255) NOT NULL, "operation" varchar(32) NOT NULL, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT "pk_spectrum_change" PRIMARY KEY ("id"));

CREATE INDEX "ix_spectrum_change_store_id_id" ON "spectrum_change" ("store_id", "id");

COMMENT ON TABLE "spectrum_change" IS 'A change of a relationship in a store, as the change table keeps it: a write of a relationship that the store did
not have, or a delete of one that it had. A change is written in the transaction of the write or the delete, so the
changes of a store are exactly the writes and deletes that committed. Spectrum gives a change a ULID as its id, so
that the changes of a store are in order of id; the database sets `create_time`.';

COMMENT ON COLUMN "spectrum_change"."id" IS 'The id, a ULID, for example `01M4XNYW5SK4DFCPRPE7SHW5H0`.';

COMMENT ON COLUMN "spectrum_change"."store_id" IS 'The store.';

COMMENT ON COLUMN "spectrum_change"."object" IS 'The object of the relationship, for example `doc:9`.';

COMMENT ON COLUMN "spectrum_change"."relation" IS 'The relation of the relationship, for example `viewer`.';

COMMENT ON COLUMN "spectrum_change"."subject" IS 'The subject of the relationship, for example `user:ana`.';

COMMENT ON COLUMN "spectrum_change"."operation" IS 'Whether the relationship was written or deleted.';
