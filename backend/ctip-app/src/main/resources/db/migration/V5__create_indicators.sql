-- V5: indicators、indicator_sources、hash_records(docs/spec/04-data-dictionary.md 表 4、5、6)
CREATE TABLE indicators (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    owner_tenant_id  UUID          NOT NULL,
    type             VARCHAR(16)   NOT NULL,
    hash_type        VARCHAR(16),
    value            VARCHAR(2048) NOT NULL,
    normalized_value VARCHAR(2048) NOT NULL,
    fingerprint      CHAR(64)      NOT NULL,
    first_seen       TIMESTAMPTZ   NOT NULL,
    last_seen        TIMESTAMPTZ   NOT NULL,
    valid_from       TIMESTAMPTZ   NOT NULL,
    valid_until      TIMESTAMPTZ,
    confidence       SMALLINT      NOT NULL DEFAULT 0,
    severity         VARCHAR(16)   NOT NULL DEFAULT 'INFO',
    score            SMALLINT      NOT NULL DEFAULT 0,
    tlp              VARCHAR(16)   NOT NULL DEFAULT 'CLEAR',
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    tags             TEXT[]        NOT NULL DEFAULT '{}',
    source_count     SMALLINT      NOT NULL DEFAULT 0,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_indicators          PRIMARY KEY (id),
    CONSTRAINT fk_indicators_tenant   FOREIGN KEY (owner_tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_indicators_type     CHECK (type IN ('IPV4','IPV6','DOMAIN','URL','FILE_HASH','EMAIL')),
    CONSTRAINT ck_indicators_hashtype CHECK (
        (type = 'FILE_HASH' AND hash_type IN ('MD5','SHA1','SHA256','SHA512'))
     OR (type <> 'FILE_HASH' AND hash_type IS NULL)),
    CONSTRAINT ck_indicators_tlp      CHECK (tlp IN ('CLEAR','GREEN','AMBER','AMBER_STRICT','RED')),
    CONSTRAINT ck_indicators_severity CHECK (severity IN ('INFO','LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_indicators_status   CHECK (status IN ('ACTIVE','EXPIRED','REVOKED','FALSE_POSITIVE')),
    CONSTRAINT ck_indicators_conf     CHECK (confidence BETWEEN 0 AND 100),
    CONSTRAINT ck_indicators_score    CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_indicators_seen     CHECK (last_seen >= first_seen),
    CONSTRAINT ck_indicators_fp       CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);

-- 索引(強制;ix_indicators_last_seen 為 cursor 分頁所必需,不可移除)
CREATE UNIQUE INDEX ux_indicators_identity
    ON indicators (type, normalized_value, owner_tenant_id);
CREATE INDEX ix_indicators_fingerprint   ON indicators (fingerprint);
CREATE INDEX ix_indicators_tenant_status ON indicators (owner_tenant_id, status, tlp);
CREATE INDEX ix_indicators_last_seen     ON indicators (last_seen DESC, id DESC);
CREATE INDEX ix_indicators_valid_until   ON indicators (valid_until) WHERE status = 'ACTIVE';
CREATE INDEX ix_indicators_tags          ON indicators USING GIN (tags);
CREATE INDEX ix_indicators_value_trgm    ON indicators USING GIN (normalized_value gin_trgm_ops);

-- indicator_sources:每個 (indicator, source) 一列;同來源 UPSERT,跨來源永不互相覆寫
CREATE TABLE indicator_sources (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    indicator_id          UUID          NOT NULL,
    source_id             UUID          NOT NULL,
    source_value          VARCHAR(2048) NOT NULL,
    source_confidence     SMALLINT,
    source_severity       VARCHAR(16),
    source_tlp            VARCHAR(16)   NOT NULL,
    source_first_seen     TIMESTAMPTZ   NOT NULL,
    source_last_seen      TIMESTAMPTZ   NOT NULL,
    source_valid_until    TIMESTAMPTZ,
    redistribution_policy VARCHAR(32)   NOT NULL,
    report_count          INTEGER       NOT NULL DEFAULT 1,
    raw_payload           JSONB,
    status                VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_indicator_sources PRIMARY KEY (id),
    CONSTRAINT ux_indicator_sources UNIQUE (indicator_id, source_id),
    CONSTRAINT fk_is_indicator FOREIGN KEY (indicator_id) REFERENCES indicators(id) ON DELETE CASCADE,
    CONSTRAINT fk_is_source    FOREIGN KEY (source_id)    REFERENCES sources(id),
    CONSTRAINT ck_is_tlp       CHECK (source_tlp IN ('CLEAR','GREEN','AMBER','AMBER_STRICT','RED')),
    CONSTRAINT ck_is_status    CHECK (status IN ('ACTIVE','EXPIRED','RETRACTED','FALSE_POSITIVE')),
    CONSTRAINT ck_is_redist    CHECK (redistribution_policy IN
                  ('PUBLIC_REDISTRIBUTABLE','ATTRIBUTION_REQUIRED','DERIVED_ONLY','INTERNAL_ONLY')),
    CONSTRAINT ck_is_conf      CHECK (source_confidence IS NULL OR source_confidence BETWEEN 0 AND 100),
    CONSTRAINT ck_is_seen      CHECK (source_last_seen >= source_first_seen),
    CONSTRAINT ck_is_count     CHECK (report_count >= 1)
);

CREATE INDEX ix_is_source_status ON indicator_sources (source_id, status);
CREATE INDEX ix_is_payload_gc    ON indicator_sources (updated_at) WHERE raw_payload IS NOT NULL;

-- hash_records:去重指紋(FingerprintAlgorithm),與 indicators.hash_type(IocHashType)是兩件不同的事
CREATE TABLE hash_records (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    indicator_id UUID         NOT NULL,
    source_id    UUID,
    algorithm    VARCHAR(16)  NOT NULL,
    digest       VARCHAR(128) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_hash_records  PRIMARY KEY (id),
    CONSTRAINT ux_hash_records  UNIQUE (algorithm, digest, indicator_id),
    CONSTRAINT fk_hr_indicator  FOREIGN KEY (indicator_id) REFERENCES indicators(id) ON DELETE CASCADE,
    CONSTRAINT fk_hr_source     FOREIGN KEY (source_id)    REFERENCES sources(id),
    CONSTRAINT ck_hr_algorithm  CHECK (algorithm IN ('SHA256','SHA512')),
    CONSTRAINT ck_hr_digest     CHECK (digest ~ '^[0-9a-f]+$')
);

CREATE INDEX ix_hash_records_digest ON hash_records (digest);
