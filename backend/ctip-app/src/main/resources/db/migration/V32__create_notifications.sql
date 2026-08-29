-- V32: webhooks、webhook_deliveries、notifications(docs/spec/04-data-dictionary.md 表 24–26)
-- Phase 20(M3)。版本號由 §4.7 的 Flyway 對應表指派。

-- 表 24 webhooks:Webhook 聚合根(W1、W3 的欄位由此處的約束強制;
-- W2 見下方 secret_encrypted 的說明;W4/W5/W6 是應用層規則)。
CREATE TABLE webhooks (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL,
    created_by_user_id  UUID          NOT NULL,
    name                VARCHAR(128)  NOT NULL,
    target_url          VARCHAR(2048) NOT NULL,
    -- W2 定調(ADR 0021):簽章密鑰以 AES-GCM 加密儲存,不是 SHA-256 雜湊——
    -- 每次送達都要以原文計算 HMAC,只存摘要的話伺服器重建不出 secret。
    secret_encrypted    BYTEA         NOT NULL,
    event_types         TEXT[]        NOT NULL DEFAULT '{}',
    filter_ioc_types    TEXT[]        NOT NULL DEFAULT '{}',
    filter_min_severity VARCHAR(16),
    filter_tags         TEXT[]        NOT NULL DEFAULT '{}',
    filter_source_ids   UUID[]        NOT NULL DEFAULT '{}',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    consecutive_failures SMALLINT     NOT NULL DEFAULT 0,
    last_delivery_at    TIMESTAMPTZ,
    last_success_at     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_webhooks  PRIMARY KEY (id),
    CONSTRAINT fk_wh_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_wh_user   FOREIGN KEY (created_by_user_id) REFERENCES users(id),
    CONSTRAINT ck_wh_status CHECK (status IN ('ACTIVE','SUSPENDED','DISABLED')),
    CONSTRAINT ck_wh_https  CHECK (target_url LIKE 'https://%'),
    CONSTRAINT ck_wh_sev    CHECK (filter_min_severity IS NULL OR
                                   filter_min_severity IN ('INFO','LOW','MEDIUM','HIGH','CRITICAL'))
);

-- 每租戶的 webhook 數量上限由 plans.max_webhooks 於應用層強制(上限可調,DB 不設約束)。
CREATE INDEX ix_wh_tenant_status ON webhooks (tenant_id, status);

-- 表 25 webhook_deliveries:append-only,保留 30 天(DELIVERY_RETENTION_DAYS,清理任務見 Phase 21)。
CREATE TABLE webhook_deliveries (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    webhook_id       UUID        NOT NULL,
    event_id         UUID        NOT NULL,
    event_type       VARCHAR(64) NOT NULL,
    attempt          SMALLINT    NOT NULL DEFAULT 1,
    status           VARCHAR(16) NOT NULL,
    http_status      SMALLINT,
    response_time_ms INTEGER,
    error_message    TEXT,
    next_retry_at    TIMESTAMPTZ,
    delivered_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_webhook_deliveries PRIMARY KEY (id),
    -- 冪等鍵:同一個 webhook 對同一個 eventId 的第 n 次嘗試只會有一列。
    -- 重送同一個 eventId 時第 1 次嘗試會撞上它,這就是 §13.1 規則 5 的去重表。
    CONSTRAINT ux_wd_idempotent UNIQUE (webhook_id, event_id, attempt),
    CONSTRAINT fk_wd_webhook FOREIGN KEY (webhook_id) REFERENCES webhooks(id) ON DELETE CASCADE,
    CONSTRAINT ck_wd_status  CHECK (status IN ('PENDING','SUCCESS','FAILED','ABANDONED')),
    CONSTRAINT ck_wd_attempt CHECK (attempt BETWEEN 1 AND 5)
);

CREATE INDEX ix_wd_retry ON webhook_deliveries (next_retry_at) WHERE status = 'FAILED';
CREATE INDEX ix_wd_gc    ON webhook_deliveries (created_at);

-- 表 26 notifications:站內通知(WebSocket / 通知中心頁)。
CREATE TABLE notifications (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID        NOT NULL,
    user_id       UUID,
    event_id      UUID        NOT NULL,
    event_type    VARCHAR(64) NOT NULL,
    title         VARCHAR(255) NOT NULL,
    body          TEXT,
    severity      VARCHAR(16) NOT NULL DEFAULT 'INFO',
    resource_type VARCHAR(64),
    resource_id   UUID,
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notif_tenant  FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_notif_user    FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT ck_notif_sev     CHECK (severity IN ('INFO','LOW','MEDIUM','HIGH','CRITICAL'))
);

-- 04 表 26 的註記:PostgreSQL 的 UNIQUE 對 null 不去重,而廣播型通知的 user_id 就是 null
-- ——普通 UNIQUE (event_id, tenant_id, user_id) 對廣播完全不生效,同一個 eventId 重送會插入第二列。
-- 冪等(§13.1 規則 5)因此改用 COALESCE 的唯一索引。
CREATE UNIQUE INDEX ux_notif_idempotent
    ON notifications (event_id, tenant_id, COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::uuid));

CREATE INDEX ix_notif_user_unread ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;

-- 通知中心的預設查詢是「本租戶(含公開平台通知)最新在前」,而 ix_notif_user_unread 是
-- 部分索引且以 user_id 開頭,對廣播列(user_id IS NULL)完全用不上。
CREATE INDEX ix_notif_tenant_created ON notifications (tenant_id, created_at DESC, id DESC);

-- notification:read —— GET /notifications 與 PATCH /notifications/{id}/read 的授權碼
-- (ADR 0021 第 5 節定調由 Phase 20 新增;§10.3「實作要求」明訂每個 handler 都必須宣告授權,
--  而 09 §9.1 的這兩個端點原本沒有對應的 code)。歸屬 LOGGED_IN:通知一律屬於某個租戶,
-- 匿名沒有可讀的通知。權限總數 23 → 24。RBAC 種子與建表同在一支 migration 的理由見 V31。
INSERT INTO permissions (code, description) VALUES
    ('notification:read', '讀取與標記自身租戶的站內通知')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'notification:read'
WHERE r.code IN ('USER', 'PREMIUM_USER', 'TENANT_ADMIN', 'SYSTEM_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;
