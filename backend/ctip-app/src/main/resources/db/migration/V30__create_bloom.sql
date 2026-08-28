-- V30: bloom_versions、bloom_artifacts(docs/spec/04-data-dictionary.md 表 22、23)
-- 版本號依實作順序遞增(ADR 0014);Phase 15 的兩張表。

-- 表 22 bloom_versions:BloomVersion 聚合根。
-- dataset_version 每次 full snapshot +1、bloom_version 每次 delta +1;
-- 兩個版號並存的唯一理由是「Bloom 無法刪除元素」(11 §11.3)。
CREATE TABLE bloom_versions (
    id                    UUID             NOT NULL DEFAULT gen_random_uuid(),
    scope                 VARCHAR(16)      NOT NULL,
    tenant_id             UUID             NOT NULL,
    dataset_version       BIGINT           NOT NULL,
    bloom_version         BIGINT           NOT NULL,
    fingerprint_algorithm VARCHAR(16)      NOT NULL DEFAULT 'SHA256',
    hash_function_count   SMALLINT         NOT NULL,
    bit_size              BIGINT           NOT NULL,
    capacity              BIGINT           NOT NULL,
    false_positive_rate   DOUBLE PRECISION NOT NULL,
    member_count          BIGINT           NOT NULL,
    is_full_snapshot      BOOLEAN          NOT NULL,
    base_bloom_version    BIGINT,
    generated_at          TIMESTAMPTZ      NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ      NOT NULL DEFAULT now(),

    CONSTRAINT pk_bloom_versions PRIMARY KEY (id),
    CONSTRAINT ux_bv_version UNIQUE (scope, tenant_id, dataset_version, bloom_version),
    CONSTRAINT fk_bv_tenant  FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_bv_scope   CHECK (scope IN ('PUBLIC','TENANT')),
    CONSTRAINT ck_bv_algo    CHECK (fingerprint_algorithm IN ('SHA256','SHA512')),
    CONSTRAINT ck_bv_fpr     CHECK (false_positive_rate > 0 AND false_positive_rate < 1),
    -- 不變量 L2:is_full_snapshot ⟺ base_bloom_version IS NULL
    CONSTRAINT ck_bv_base    CHECK ((is_full_snapshot AND base_bloom_version IS NULL)
                                 OR (NOT is_full_snapshot AND base_bloom_version IS NOT NULL)),
    -- 不變量 L1:scope = PUBLIC 一律綁在 public tenant 上
    CONSTRAINT ck_bv_public_tenant CHECK (
        scope <> 'PUBLIC' OR tenant_id = '00000000-0000-0000-0000-000000000000'::uuid)
);

CREATE INDEX ix_bv_lookup ON bloom_versions (scope, tenant_id, bloom_version DESC);

-- 表 23 bloom_artifacts:實際位元陣列的儲存位置與校驗資訊。
-- checksum 是「未壓縮 payload」的 SHA-256(不變量 L5);delta 的 payload 是 addedBits 的
-- varint 編碼、full 的是位元陣列本身。resulting_checksum 只有 delta 有(不變量 L6)。
CREATE TABLE bloom_artifacts (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    bloom_version_id        UUID          NOT NULL,
    storage_kind            VARCHAR(16)   NOT NULL DEFAULT 'FILESYSTEM',
    storage_path            VARCHAR(1024) NOT NULL,
    compression             VARCHAR(8)    NOT NULL DEFAULT 'ZSTD',
    size_bytes              BIGINT        NOT NULL,
    uncompressed_size_bytes BIGINT        NOT NULL,
    checksum                CHAR(64)      NOT NULL,
    resulting_checksum      CHAR(64),
    download_count          BIGINT        NOT NULL DEFAULT 0,
    expires_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_bloom_artifacts PRIMARY KEY (id),
    CONSTRAINT ux_ba_version UNIQUE (bloom_version_id),
    CONSTRAINT fk_ba_version FOREIGN KEY (bloom_version_id)
                  REFERENCES bloom_versions(id) ON DELETE CASCADE,
    CONSTRAINT ck_ba_kind    CHECK (storage_kind IN ('FILESYSTEM','S3','INLINE')),
    CONSTRAINT ck_ba_comp    CHECK (compression IN ('GZIP','ZSTD','NONE')),
    CONSTRAINT ck_ba_sum     CHECK (checksum ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_ba_gc ON bloom_artifacts (created_at);
