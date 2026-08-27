-- V20: users、roles、permissions、role_permissions、tenant_users
-- (docs/spec/04-data-dictionary.md 表 10–14;§4.7 版本區段 V20–V29 = M2)

CREATE TABLE users (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    email              VARCHAR(320) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    display_name       VARCHAR(255),
    status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    primary_tenant_id  UUID         NOT NULL,
    last_login_at      TIMESTAMPTZ,
    failed_login_count SMALLINT     NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_users        PRIMARY KEY (id),
    CONSTRAINT ux_users_email  UNIQUE (email),
    CONSTRAINT fk_users_tenant FOREIGN KEY (primary_tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','SUSPENDED','PENDING_VERIFICATION')),
    -- 不變量 U1:email 一律小寫儲存
    CONSTRAINT ck_users_email  CHECK (email = lower(email)),
    -- 不變量 U2 / T3:public tenant 不得有使用者
    CONSTRAINT ck_users_not_public
        CHECK (primary_tenant_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

CREATE INDEX ix_users_tenant ON users (primary_tenant_id);

-- 參考資料:由 V24 種入,不由 API 建立
CREATE TABLE roles (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    code          VARCHAR(32) NOT NULL,
    name          VARCHAR(64) NOT NULL,
    description   TEXT,
    tenant_scoped BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_roles      PRIMARY KEY (id),
    CONSTRAINT ux_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_code CHECK (code IN
                  ('ANONYMOUS','USER','PREMIUM_USER','TENANT_ADMIN','SYSTEM_ADMIN'))
);

CREATE TABLE permissions (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    code        VARCHAR(64) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_permissions      PRIMARY KEY (id),
    CONSTRAINT ux_permissions_code UNIQUE (code),
    CONSTRAINT ck_permissions_fmt  CHECK (code ~ '^[a-z]+:[a-z-]+$')
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,

    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id)       REFERENCES roles(id)       ON DELETE CASCADE,
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

-- 一個使用者可屬於多個 tenant,每個 tenant 內有各自角色(PK 使同一 tenant 內恰一個角色)
CREATE TABLE tenant_users (
    tenant_id UUID        NOT NULL,
    user_id   UUID        NOT NULL,
    role_id   UUID        NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_tenant_users PRIMARY KEY (tenant_id, user_id),
    CONSTRAINT fk_tu_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_tu_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_tu_role   FOREIGN KEY (role_id)   REFERENCES roles(id),
    -- 不變量 T3:public tenant 無成員
    CONSTRAINT ck_tu_not_public
        CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

CREATE INDEX ix_tu_user ON tenant_users (user_id);
