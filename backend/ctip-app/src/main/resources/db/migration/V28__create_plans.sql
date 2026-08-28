-- V28: plans、subscriptions、import_jobs(docs/spec/04-data-dictionary.md 表 17、18、18b)
-- 版本號依實作順序遞增(ADR 0014);ingestion_rejections 一併加上 import_job_id。

-- 表 17 plans:§10.6 的配額表全部存於此,不得 hard-code。
-- 0 = 停用(不是無限制);NULL = 無限制(ADR 0019)。
CREATE TABLE plans (
    id                             UUID        NOT NULL DEFAULT gen_random_uuid(),
    code                           VARCHAR(32) NOT NULL,
    name                           VARCHAR(64) NOT NULL,
    tier                           SMALLINT    NOT NULL,
    requests_per_minute            INTEGER     NOT NULL,
    requests_per_day               INTEGER,
    max_page_size                  INTEGER     NOT NULL,
    max_batch_lookup               INTEGER     NOT NULL,
    min_sync_interval_seconds      INTEGER     NOT NULL,
    public_bloom_enabled           BOOLEAN     NOT NULL DEFAULT true,
    tenant_bloom_capacity          BIGINT,
    websocket_enabled              BOOLEAN     NOT NULL DEFAULT false,
    max_webhooks                   INTEGER     NOT NULL DEFAULT 0,
    max_api_keys                   INTEGER     NOT NULL DEFAULT 0,
    custom_feed_enabled            BOOLEAN     NOT NULL DEFAULT false,
    stix_export_max_objects        INTEGER,
    max_manual_submissions_per_day INTEGER     NOT NULL DEFAULT 0,
    max_import_rows_per_file       INTEGER     NOT NULL DEFAULT 0,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_plans      PRIMARY KEY (id),
    CONSTRAINT ux_plans_code UNIQUE (code),
    CONSTRAINT ux_plans_tier UNIQUE (tier),
    CONSTRAINT ck_plans_code CHECK (code IN ('ANONYMOUS','FREE','PREMIUM','ENTERPRISE')),
    CONSTRAINT ck_plans_tier CHECK (tier BETWEEN 0 AND 3),
    -- 配額值本身的合法域:負數在任何欄位都沒有語意(0 = 停用已由 DEFAULT 0 表達)
    CONSTRAINT ck_plans_quotas CHECK (
        requests_per_minute > 0
    AND (requests_per_day IS NULL OR requests_per_day > 0)
    AND max_page_size > 0
    AND max_batch_lookup >= 0
    AND min_sync_interval_seconds >= 0
    AND (tenant_bloom_capacity IS NULL OR tenant_bloom_capacity > 0)
    AND max_webhooks >= 0
    AND max_api_keys >= 0
    AND (stix_export_max_objects IS NULL OR stix_export_max_objects >= 0)
    AND max_manual_submissions_per_day >= 0
    AND max_import_rows_per_file >= 0)
);

-- 表 18 subscriptions:B1 由部分唯一索引強制(一個 tenant 同時只有一份 ACTIVE)
CREATE TABLE subscriptions (
    id                       UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                UUID        NOT NULL,
    plan_id                  UUID        NOT NULL,
    status                   VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    provider                 VARCHAR(32) NOT NULL DEFAULT 'NONE',
    external_subscription_id VARCHAR(255),
    current_period_start     TIMESTAMPTZ NOT NULL DEFAULT now(),
    current_period_end       TIMESTAMPTZ,
    cancelled_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_sub_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_plan   FOREIGN KEY (plan_id)   REFERENCES plans(id),
    CONSTRAINT ck_sub_status CHECK (status IN ('ACTIVE','PAST_DUE','CANCELLED','EXPIRED')),
    CONSTRAINT ck_sub_prov   CHECK (provider IN ('NONE','STRIPE','MANUAL')),
    -- B2:currentPeriodEnd 為 null 或晚於 start
    CONSTRAINT ck_sub_period CHECK (current_period_end IS NULL OR current_period_end > current_period_start),
    -- B5:provider = NONE 時不得有外部訂閱 id
    CONSTRAINT ck_sub_external CHECK (provider <> 'NONE' OR external_subscription_id IS NULL)
);

CREATE UNIQUE INDEX ux_subscriptions_active ON subscriptions (tenant_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_subscriptions_tenant ON subscriptions (tenant_id, created_at DESC);

-- 表 18b import_jobs:POST /iocs/import 的 202 + jobId 進度承載(ADR 0019)
CREATE TABLE import_jobs (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    submitted_by    UUID          NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    format          VARCHAR(16)   NOT NULL,
    total_rows      INTEGER,
    accepted_count  INTEGER       NOT NULL DEFAULT 0,
    merged_count    INTEGER       NOT NULL DEFAULT 0,
    rejected_count  INTEGER       NOT NULL DEFAULT 0,
    error_message   VARCHAR(1024),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_import_jobs PRIMARY KEY (id),
    CONSTRAINT fk_ij_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_ij_user   FOREIGN KEY (submitted_by) REFERENCES users(id),
    CONSTRAINT ck_ij_status CHECK (status IN ('PENDING','RUNNING','SUCCESS','PARTIAL','FAILURE')),
    CONSTRAINT ck_ij_format CHECK (format IN ('CSV','STIX_BUNDLE')),
    CONSTRAINT ck_ij_counts CHECK (accepted_count >= 0 AND merged_count >= 0 AND rejected_count >= 0)
);

CREATE INDEX ix_import_jobs_tenant ON import_jobs (tenant_id, created_at DESC);

-- 逐筆 rejection 沿用 ingestion_rejections(表 7);來源同步的 rejection 仍為 null
ALTER TABLE ingestion_rejections
    ADD COLUMN import_job_id UUID,
    ADD CONSTRAINT fk_ir_import_job FOREIGN KEY (import_job_id) REFERENCES import_jobs(id) ON DELETE CASCADE;

CREATE INDEX ix_ir_import_job ON ingestion_rejections (import_job_id) WHERE import_job_id IS NOT NULL;
