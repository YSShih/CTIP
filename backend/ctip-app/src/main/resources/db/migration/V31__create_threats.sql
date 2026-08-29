-- V31: threats、threat_indicators、threat_external_references(docs/spec/04-data-dictionary.md 表 19–21)
-- 版本號依實作順序遞增(ADR 0014);Phase 18 的三張表 + V7 保留的 fk_so_threat。

-- 表 19 threats:Threat 聚合根(H1–H4 由此處的約束強制;
-- H5 由 threat_indicators 只存 indicator_id 強制;H6 是應用層一致性規則,見 ADR 0020)。
CREATE TABLE threats (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    owner_tenant_id UUID         NOT NULL,
    type            VARCHAR(32)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    aliases         TEXT[]       NOT NULL DEFAULT '{}',
    description     TEXT,
    severity        VARCHAR(16)  NOT NULL DEFAULT 'INFO',
    confidence      SMALLINT     NOT NULL DEFAULT 0,
    tlp             VARCHAR(16)  NOT NULL DEFAULT 'CLEAR',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    first_seen      TIMESTAMPTZ  NOT NULL,
    last_seen       TIMESTAMPTZ  NOT NULL,
    tags            TEXT[]       NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_threats          PRIMARY KEY (id),
    CONSTRAINT ux_threats_identity UNIQUE (owner_tenant_id, type, name),
    CONSTRAINT fk_threats_tenant   FOREIGN KEY (owner_tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_threats_type     CHECK (type IN ('CAMPAIGN','MALWARE_FAMILY','THREAT_ACTOR',
                                                   'ATTACK_PATTERN','PHISHING_KIT')),
    CONSTRAINT ck_threats_status   CHECK (status IN ('ACTIVE','DORMANT','RETIRED')),
    CONSTRAINT ck_threats_severity CHECK (severity IN ('INFO','LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_threats_tlp      CHECK (tlp IN ('CLEAR','GREEN','AMBER','AMBER_STRICT','RED')),
    CONSTRAINT ck_threats_conf     CHECK (confidence BETWEEN 0 AND 100),
    CONSTRAINT ck_threats_seen     CHECK (last_seen >= first_seen)
);

CREATE INDEX ix_threats_tenant_status ON threats (owner_tenant_id, status, tlp);
CREATE INDEX ix_threats_aliases       ON threats USING GIN (aliases);
CREATE INDEX ix_threats_last_seen     ON threats (last_seen DESC, id DESC);

-- 表 20 threat_indicators:聚合內部實體。只存 indicator_id(H5),不持有 Indicator 物件。
CREATE TABLE threat_indicators (
    threat_id    UUID        NOT NULL,
    indicator_id UUID        NOT NULL,
    role         VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    added_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_threat_indicators PRIMARY KEY (threat_id, indicator_id),
    CONSTRAINT fk_ti_threat    FOREIGN KEY (threat_id)    REFERENCES threats(id)    ON DELETE CASCADE,
    CONSTRAINT fk_ti_indicator FOREIGN KEY (indicator_id) REFERENCES indicators(id) ON DELETE CASCADE,
    CONSTRAINT ck_ti_role CHECK (role IN ('C2','DELIVERY','PAYLOAD','INFRASTRUCTURE','VICTIM','UNKNOWN'))
);

CREATE INDEX ix_ti_indicator ON threat_indicators (indicator_id);

-- 表 21 threat_external_references:聚合內的值物件集合(不存 JSONB,§4.0 白名單)。
CREATE TABLE threat_external_references (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    threat_id   UUID          NOT NULL,
    source_name VARCHAR(64)   NOT NULL,
    external_id VARCHAR(128),
    url         VARCHAR(2048),
    description TEXT,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_threat_external_references PRIMARY KEY (id),
    CONSTRAINT fk_ter_threat  FOREIGN KEY (threat_id) REFERENCES threats(id) ON DELETE CASCADE,
    CONSTRAINT ck_ter_has_ref CHECK (external_id IS NOT NULL OR url IS NOT NULL)
);

CREATE INDEX ix_ter_external ON threat_external_references (source_name, external_id);

-- H4 的實際強制:external_id 可為 null,而 PostgreSQL 的 UNIQUE 不去重 null
-- (§6.3.6 自己列的地雷)——普通 UNIQUE 在 external_id IS NULL 時完全不生效(ADR 0020)。
CREATE UNIQUE INDEX ux_ter_identity_coalesced
    ON threat_external_references (threat_id, source_name, COALESCE(external_id, ''));

-- V7 保留的 FK:stix_objects.threat_id → threats.id(M1 不建 threats 表,故延到此處)。
ALTER TABLE stix_objects
    ADD CONSTRAINT fk_so_threat FOREIGN KEY (threat_id) REFERENCES threats(id) ON DELETE CASCADE;

-- 04 表 8 只列了 ix_so_indicator。threat_id 同樣是 FK 且帶 ON DELETE CASCADE——
-- 刪一個 threat 會對 stix_objects 做一次全表掃描,查 threat 的投影也一樣(ADR 0027)。
CREATE INDEX ix_so_threat ON stix_objects (threat_id);

-- threat:manage(§10.3;冪等)。§9.1 原本只有三個 GET,平台沒有任何建立 Threat 的管道——
-- threats 表與 Threat.linkIndicator/retire 因此永遠不可達,正是規則 16 禁止的 placeholder。
-- 寫入端點與本權限一併補上(ADR 0027);歸屬 TENANT_ADMIN / SYSTEM_ADMIN:
-- 把 IOC 歸因到 campaign／malware family 是租戶層級的情資策展決策,不是一般使用者的自助操作。
-- 權限總數 22 → 23。此處與 V24/V27/V29 同屬 RBAC 種子,故一併寫在建表的 migration 內
-- (§4.7 已把 V32/V33 指派給 Phase 20/21,另開版本號會讓那兩個 phase 的 migration 變成
--  out-of-order,在既有資料庫上 FlywayValidateException——正是 ADR 0014 修掉的坑)。
INSERT INTO permissions (code, description) VALUES
    ('threat:manage', '建立與維護威脅實體及其 IOC 關聯')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'threat:manage'
WHERE r.code IN ('TENANT_ADMIN', 'SYSTEM_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;
