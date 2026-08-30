-- V33: audit_logs(docs/spec/04-data-dictionary.md 表 27)與保留清理角色的授權
--      (docs/spec/13-platform-ops.md §13.4 保留政策、§13.5 稽核規則 1、2)
--
-- 本表 append-only:沒有 updated_at 欄位(§13.5 規則 6),且應用角色的 UPDATE/DELETE 由
-- 下方的 REVOKE 在 **資料庫層** 擋掉(§13.5 規則 1;M3-09 明文要求不是應用碼擋)。
-- 角色名稱以 Flyway placeholder 帶入,與 compose/.env 的 POSTGRES_APP_USER、
-- POSTGRES_RETENTION_USER 對應(environment/config/postgres/01-app-roles.sh 建立角色)。
CREATE TABLE audit_logs (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_type    VARCHAR(16)  NOT NULL,
    actor_id      UUID,
    tenant_id     UUID         NOT NULL,
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(64),
    resource_id   UUID,
    ip            INET,
    user_agent    VARCHAR(512),
    result        VARCHAR(16)  NOT NULL,
    trace_id      VARCHAR(64),
    metadata      JSONB,

    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_al_tenant  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_al_actor   CHECK (actor_type IN ('ANONYMOUS','USER','API_KEY','SYSTEM')),
    CONSTRAINT ck_al_result  CHECK (result IN ('SUCCESS','FAILURE','DENIED')),
    -- §4.5 的 26 種稽核行為。§4.0 通用約定要求列舉以 VARCHAR + CHECK 對應;
    -- 表 27 原本沒列這條,但少了它,拼錯的 action 會靜靜寫進一張永不更新的表(規格回寫 §0.28)
    CONSTRAINT ck_al_action  CHECK (action IN (
        'LOGIN','LOGIN_FAILED','LOGOUT','TOKEN_REFRESH','TOKEN_REUSE_DETECTED',
        'API_ACCESS','IOC_QUERY','IOC_DOWNLOAD','IOC_SUBMIT','IOC_IMPORT','IOC_REPORT_FP',
        'STIX_EXPORT','SYNC_MANIFEST','SYNC_BLOOM','SYNC_DELTA',
        'INGESTION_STARTED','INGESTION_COMPLETED','INGESTION_FAILED',
        'ADMIN_ACTION','TENANT_CREATED','USER_CREATED',
        'API_KEY_CREATED','API_KEY_REVOKED',
        'SUBSCRIPTION_CHANGED','WEBHOOK_CREATED','WEBHOOK_DELETED'))
);

CREATE INDEX ix_al_tenant_time ON audit_logs (tenant_id, occurred_at DESC);
CREATE INDEX ix_al_actor       ON audit_logs (actor_type, actor_id, occurred_at DESC);
CREATE INDEX ix_al_action      ON audit_logs (action, occurred_at DESC);
CREATE INDEX ix_al_gc          ON audit_logs (occurred_at);

-- §13.5 規則 1:append-only 由 DB 強制。
-- ALTER DEFAULT PRIVILEGES(01-app-roles.sh)已把新表的四種 DML 都給了應用角色,
-- 因此這裡必須明確收回 UPDATE 與 DELETE;SELECT/INSERT 保留(寫入稽核 + GET /audit-logs)。
REVOKE UPDATE, DELETE ON audit_logs FROM ${appRole};
GRANT SELECT, INSERT ON audit_logs TO ${appRole};

-- §13.5 規則 2:保留清理走專用角色。PostgreSQL 對 DELETE/UPDATE 的 WHERE 子句
-- 仍要求所引用欄位的 SELECT 權限,因此以**欄位層級**授權——該角色讀不到稽核內容
-- (action/metadata/ip 一概沒有 SELECT),只讀得到判斷保留期所需的鍵與時間欄位。
GRANT SELECT (id, occurred_at) ON audit_logs TO ${retentionRole};
GRANT DELETE ON audit_logs TO ${retentionRole};

GRANT SELECT (id, created_at) ON ingestion_rejections TO ${retentionRole};
GRANT DELETE ON ingestion_rejections TO ${retentionRole};

GRANT SELECT (id, created_at) ON webhook_deliveries TO ${retentionRole};
GRANT DELETE ON webhook_deliveries TO ${retentionRole};

-- raw_payload 只清空該欄位、保留其餘欄位(§13.4),因此是 UPDATE 而非 DELETE
GRANT SELECT (id, updated_at, raw_payload) ON indicator_sources TO ${retentionRole};
GRANT UPDATE (raw_payload) ON indicator_sources TO ${retentionRole};

-- EXPIRED indicator 於保留期後軟刪除(§13.4)
GRANT SELECT (id, status, updated_at, deleted_at) ON indicators TO ${retentionRole};
GRANT UPDATE (deleted_at) ON indicators TO ${retentionRole};
