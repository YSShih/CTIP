-- V3: sources、source_sync(docs/spec/04-data-dictionary.md 表 2、3)
CREATE TABLE sources (
    id                           UUID          NOT NULL DEFAULT gen_random_uuid(),
    source_type                  VARCHAR(64)   NOT NULL,
    display_name                 VARCHAR(255)  NOT NULL,
    description                  TEXT,
    homepage_url                 VARCHAR(2048),
    default_tlp                  VARCHAR(16)   NOT NULL DEFAULT 'CLEAR',
    redistribution_policy        VARCHAR(32)   NOT NULL DEFAULT 'INTERNAL_ONLY',
    reputation                   SMALLINT      NOT NULL DEFAULT 50,
    enabled                      BOOLEAN       NOT NULL DEFAULT false,
    syncable                     BOOLEAN       NOT NULL DEFAULT true,
    recommended_interval_seconds INTEGER,
    requires_credentials         BOOLEAN       NOT NULL DEFAULT false,
    config                       JSONB         NOT NULL DEFAULT '{}',
    status                       VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    consecutive_failures         INTEGER       NOT NULL DEFAULT 0,
    last_sync_at                 TIMESTAMPTZ,
    last_success_at              TIMESTAMPTZ,
    last_failure_at              TIMESTAMPTZ,
    last_error_message           TEXT,
    avg_latency_ms               INTEGER,
    total_records_ingested       BIGINT        NOT NULL DEFAULT 0,
    next_cursor                  VARCHAR(1024),
    created_at                   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_sources             PRIMARY KEY (id),
    CONSTRAINT ux_sources_source_type UNIQUE (source_type),
    CONSTRAINT ck_sources_tlp         CHECK (default_tlp IN ('CLEAR','GREEN','AMBER','AMBER_STRICT','RED')),
    CONSTRAINT ck_sources_redist      CHECK (redistribution_policy IN
                  ('PUBLIC_REDISTRIBUTABLE','ATTRIBUTION_REQUIRED','DERIVED_ONLY','INTERNAL_ONLY')),
    CONSTRAINT ck_sources_status      CHECK (status IN ('ACTIVE','DEGRADED','FAILED','DISABLED')),
    CONSTRAINT ck_sources_reputation  CHECK (reputation BETWEEN 0 AND 100)
);

CREATE INDEX ix_sources_enabled_status ON sources (enabled, status) WHERE syncable = true;

-- source_sync:每次 ingestion 執行一列,append-only
CREATE TABLE source_sync (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    source_id        UUID        NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ,
    duration_ms      INTEGER,
    result           VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    records_fetched  INTEGER     NOT NULL DEFAULT 0,
    records_accepted INTEGER     NOT NULL DEFAULT 0,
    records_rejected INTEGER     NOT NULL DEFAULT 0,
    records_merged   INTEGER     NOT NULL DEFAULT 0,
    error_message    TEXT,
    trace_id         VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_source_sync         PRIMARY KEY (id),
    CONSTRAINT fk_source_sync_sources FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    CONSTRAINT ck_source_sync_result  CHECK (result IN ('RUNNING','SUCCESS','PARTIAL','FAILURE'))
);

CREATE INDEX ix_source_sync_source_started ON source_sync (source_id, started_at DESC);
