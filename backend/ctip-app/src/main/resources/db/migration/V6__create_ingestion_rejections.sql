-- V6: ingestion_rejections(docs/spec/04-data-dictionary.md 表 7)
-- append-only,保留 30 天(REJECTION_RETENTION_DAYS)
CREATE TABLE ingestion_rejections (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    source_id      UUID          NOT NULL,
    source_sync_id UUID,
    raw_value      VARCHAR(4096) NOT NULL,
    declared_type  VARCHAR(16),
    reason         VARCHAR(64)   NOT NULL,
    detail         TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_ingestion_rejections PRIMARY KEY (id),
    CONSTRAINT fk_ir_source FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    CONSTRAINT fk_ir_sync   FOREIGN KEY (source_sync_id) REFERENCES source_sync(id) ON DELETE SET NULL,
    CONSTRAINT ck_ir_reason CHECK (reason IN ('MALFORMED_VALUE','PRIVATE_OR_RESERVED_IP',
                  'ALLOWLISTED_DOMAIN','LENGTH_EXCEEDED','HASH_LENGTH_MISMATCH','UNKNOWN_TYPE',
                  'DUPLICATE_IN_BATCH','QUOTA_EXCEEDED'))
);

CREATE INDEX ix_ir_source_created ON ingestion_rejections (source_id, created_at DESC);
CREATE INDEX ix_ir_gc             ON ingestion_rejections (created_at);
