ALTER TABLE t_knowledge_base
    ADD COLUMN IF NOT EXISTS kb_type VARCHAR(32) NOT NULL DEFAULT 'GUIDE',
    ADD COLUMN IF NOT EXISTS description VARCHAR(512),
    ADD COLUMN IF NOT EXISTS routing_keywords_json JSONB,
    ADD COLUMN IF NOT EXISTS metadata_schema_json JSONB,
    ADD COLUMN IF NOT EXISTS default_pipeline_profile VARCHAR(64);

ALTER TABLE t_knowledge_document
    ADD COLUMN IF NOT EXISTS routed_kb_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS routing_confidence NUMERIC(5,4),
    ADD COLUMN IF NOT EXISTS routing_reason VARCHAR(512),
    ADD COLUMN IF NOT EXISTS extracted_metadata_json JSONB,
    ADD COLUMN IF NOT EXISTS needs_review SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE t_intent_node
    ADD COLUMN IF NOT EXISTS metadata_filter_template TEXT,
    ADD COLUMN IF NOT EXISTS slot_schema_json TEXT,
    ADD COLUMN IF NOT EXISTS routing_hint VARCHAR(512),
    ADD COLUMN IF NOT EXISTS preferred_source VARCHAR(16) NOT NULL DEFAULT 'KB';

ALTER TABLE t_ingestion_task
    ADD COLUMN IF NOT EXISTS routed_kb_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS routing_confidence NUMERIC(5,4),
    ADD COLUMN IF NOT EXISTS routing_reason VARCHAR(512),
    ADD COLUMN IF NOT EXISTS extracted_metadata_json JSONB,
    ADD COLUMN IF NOT EXISTS needs_review SMALLINT NOT NULL DEFAULT 0;
