-- V29: 四個方案種子 + subscription:read 權限(docs/spec/04-data-dictionary.md 表 17、
-- docs/spec/10-identity-plans.md §10.3/§10.6;皆冪等)
--
-- 兩件事放在同一支 migration:phase-14 的執行單只配到 V28–V29 兩個版本號,
-- 而「新增權限」與「新增方案」都是本 phase 的種子(ADR 0023)。
--
-- 配額值逐格對應 §10.6 的配額表。0 = 停用,NULL = 無限制(ADR 0019):
--   ANONYMOUS.stix_export_max_objects = 0    → 匿名不得匯出 bundle
--   ENTERPRISE.stix_export_max_objects = NULL→ 無限制
--   ENTERPRISE.requests_per_day        = NULL→ 依合約
-- 已存在者不覆寫(DO NOTHING):方案值於 M2 由 SYSTEM_ADMIN 調整,重跑 migration 不得沖掉。

INSERT INTO plans (code, name, tier,
                   requests_per_minute, requests_per_day, max_page_size, max_batch_lookup,
                   min_sync_interval_seconds, public_bloom_enabled, tenant_bloom_capacity,
                   websocket_enabled, max_webhooks, max_api_keys, custom_feed_enabled,
                   stix_export_max_objects, max_manual_submissions_per_day, max_import_rows_per_file)
VALUES
    ('ANONYMOUS',  'Anonymous',  0,   60,   1000,   50,   20, 86400, true, NULL,     false,  0,   0, false,     0,     0,      0),
    ('FREE',       'Free',       1,  300,  20000,  100,  100, 21600, true, NULL,     false,  0,   1, false,  1000,     0,      0),
    ('PREMIUM',    'Premium',    2, 1200, 500000,  500, 1000,   300, true,  1000000,  true,  5,  10, false, 50000,  1000,  10000),
    ('ENTERPRISE', 'Enterprise', 3, 6000,   NULL, 1000, 5000,    60, true, 10000000,  true, 50, 100,  true,  NULL, 50000, 500000)
ON CONFLICT (code) DO NOTHING;

-- subscription:read —— GET /subscription 與 /subscription/usage 的授權碼(ADR 0019 第 9 節)。
-- §10.3「實作要求」明訂每個 handler 都必須宣告授權,而原本沒有對應的 code。
-- 歸屬 LOGGED_IN:USER 以上皆可看自己的方案;匿名沒有訂閱可看。
INSERT INTO permissions (code, description) VALUES
    ('subscription:read', '讀取自身租戶的方案與用量')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'subscription:read'
WHERE r.code IN ('USER', 'PREMIUM_USER', 'TENANT_ADMIN', 'SYSTEM_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;
