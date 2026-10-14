CREATE TABLE "spectrum_store" ("id" TEXT NOT NULL, "name" TEXT NOT NULL, "create_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')), CONSTRAINT "pk_spectrum_store" PRIMARY KEY ("id"));

CREATE TABLE "spectrum_relationship" ("id" TEXT NOT NULL DEFAULT (lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))), "store_id" TEXT NOT NULL, "object" TEXT NOT NULL, "relation" TEXT NOT NULL, "subject" TEXT NOT NULL, "create_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')), CONSTRAINT "pk_spectrum_relationship" PRIMARY KEY ("id"));

CREATE UNIQUE INDEX "ux_spectrum_relationship_store_id_object_relation_subject" ON "spectrum_relationship" ("store_id", "object", "relation", "subject");

CREATE INDEX "ix_spectrum_relationship_store_id_subject_relation_object" ON "spectrum_relationship" ("store_id", "subject", "relation", "object");

CREATE TABLE "spectrum_model_assertions" ("model_id" TEXT NOT NULL, "store_id" TEXT NOT NULL, "definition" TEXT NOT NULL, CONSTRAINT "pk_spectrum_model_assertions" PRIMARY KEY ("model_id"));

CREATE INDEX "ix_spectrum_model_assertions_store_id" ON "spectrum_model_assertions" ("store_id");

CREATE TABLE "spectrum_store_model" ("id" TEXT NOT NULL, "store_id" TEXT NOT NULL, "definition" TEXT NOT NULL, "create_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')), CONSTRAINT "pk_spectrum_store_model" PRIMARY KEY ("id"));

CREATE INDEX "ix_spectrum_store_model_store_id_id" ON "spectrum_store_model" ("store_id", "id");

CREATE TABLE "spectrum_change" ("id" TEXT NOT NULL, "store_id" TEXT NOT NULL, "object" TEXT NOT NULL, "relation" TEXT NOT NULL, "subject" TEXT NOT NULL, "operation" TEXT NOT NULL, "create_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')), CONSTRAINT "pk_spectrum_change" PRIMARY KEY ("id"));

CREATE INDEX "ix_spectrum_change_store_id_id" ON "spectrum_change" ("store_id", "id");
