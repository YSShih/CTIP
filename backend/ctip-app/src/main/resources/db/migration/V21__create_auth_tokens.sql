-- V21: refresh_tokens、api_keys(docs/spec/04-data-dictionary.md 表 15、16)

-- 只存 SHA-256 雜湊(hex 64),絕不存原文(§10.4、不變量 U3/U4)
CREATE TABLE refresh_tokens (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL,
    token_hash     CHAR(64)     NOT NULL,
    family_id      UUID         NOT NULL,
    parent_id      UUID,
    issued_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ  NOT NULL,
    used_at        TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(32),
    user_agent     VARCHAR(512),
    ip             INET,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT ux_rt_hash   UNIQUE (token_hash),
    CONSTRAINT fk_rt_user   FOREIGN KEY (user_id)   REFERENCES users(id)          ON DELETE CASCADE,
    CONSTRAINT fk_rt_parent FOREIGN KEY (parent_id) REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    CONSTRAINT ck_rt_reason CHECK (revoked_reason IS NULL OR revoked_reason IN
                  ('LOGOUT','ROTATED','REUSE_DETECTED','ADMIN','EXPIRED_CLEANUP')),
    CONSTRAINT ck_rt_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX ix_rt_user_family ON refresh_tokens (user_id, family_id);
CREATE INDEX ix_rt_gc          ON refresh_tokens (expires_at);

-- key_prefix 為隨機段前 8 碼(ADR 0012 決策 2):完整格式 ctip_<env>_<32 base62> 的前 8 碼
-- 恆為 ctip_<env>,與 ux_api_keys_prefix 唯一約束及 §10.5「以 prefix 定位單一列」直接衝突。
CREATE TABLE api_keys (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL,
    user_id      UUID         NOT NULL,
    name         VARCHAR(128) NOT NULL,
    key_prefix   CHAR(8)      NOT NULL,
    key_hash     CHAR(64)     NOT NULL,
    scopes       TEXT[]       NOT NULL DEFAULT '{}',
    expires_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_api_keys        PRIMARY KEY (id),
    CONSTRAINT ux_api_keys_hash   UNIQUE (key_hash),
    CONSTRAINT ux_api_keys_prefix UNIQUE (key_prefix),
    CONSTRAINT fk_ak_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_ak_user   FOREIGN KEY (user_id)   REFERENCES users(id),
    -- 不變量 K5 / T3:public tenant 不得有 API key
    CONSTRAINT ck_ak_not_public
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

CREATE INDEX ix_ak_tenant ON api_keys (tenant_id) WHERE revoked_at IS NULL;
