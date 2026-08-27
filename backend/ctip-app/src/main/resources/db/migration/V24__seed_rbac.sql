-- V24: RBAC 種子(docs/spec/10-identity-plans.md §10.3 矩陣;冪等)
-- 權限清單以 §10.3 的程式碼區塊為準:19 個字串(該節標題誤寫「18 項」,見 ADR 0012 決策 1)

INSERT INTO roles (code, name, description, tenant_scoped) VALUES
    ('ANONYMOUS',    'Anonymous',     '未登入訪客,僅公開情資唯讀',                 true),
    ('USER',         'User',          '一般登入使用者',                             true),
    ('PREMIUM_USER', 'Premium User',  '可提交與匯入 IOC、可管理 webhook',           true),
    ('TENANT_ADMIN', 'Tenant Admin',  '租戶管理者,可管理成員與檢視稽核',           true),
    ('SYSTEM_ADMIN', 'System Admin',  '平台營運者,跨租戶',                         false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, description) VALUES
    ('ioc:read',       '讀取 IOC'),
    ('ioc:export',     '匯出 IOC'),
    ('ioc:submit',     '手動提交 IOC'),
    ('ioc:import',     '批次匯入 IOC'),
    ('ioc:report-fp',  '回報誤判'),
    ('ioc:publish',    '將提交的 IOC 標為 CLEAR/GREEN(平台營運決策)'),
    ('threat:read',    '讀取威脅'),
    ('stix:export',    '匯出 STIX bundle'),
    ('sync:bloom',     '下載 Bloom filter'),
    ('sync:delta',     '下載 Bloom delta'),
    ('apikey:create',  '建立與檢視 API key'),
    ('apikey:revoke',  '撤銷 API key'),
    ('webhook:manage', '管理 webhook'),
    ('tenant:manage',  '管理租戶設定'),
    ('user:manage',    '管理租戶成員'),
    ('audit:read',     '檢視稽核日誌'),
    ('source:manage',  '管理情資來源'),
    ('source:sync',    '手動觸發來源同步'),
    ('system:admin',   '平台管理')
ON CONFLICT (code) DO NOTHING;

-- §10.3 矩陣逐格展開。上層角色包含下層角色的全部權限。
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = ANY (
    CASE r.code
        WHEN 'ANONYMOUS' THEN ARRAY[
            'ioc:read','threat:read','sync:bloom']
        WHEN 'USER' THEN ARRAY[
            'ioc:read','threat:read','sync:bloom',
            'ioc:export','stix:export','sync:delta','ioc:report-fp',
            'apikey:create','apikey:revoke']
        WHEN 'PREMIUM_USER' THEN ARRAY[
            'ioc:read','threat:read','sync:bloom',
            'ioc:export','stix:export','sync:delta','ioc:report-fp',
            'apikey:create','apikey:revoke',
            'ioc:submit','ioc:import','webhook:manage']
        WHEN 'TENANT_ADMIN' THEN ARRAY[
            'ioc:read','threat:read','sync:bloom',
            'ioc:export','stix:export','sync:delta','ioc:report-fp',
            'apikey:create','apikey:revoke',
            'ioc:submit','ioc:import','webhook:manage',
            'user:manage','tenant:manage','audit:read']
        WHEN 'SYSTEM_ADMIN' THEN ARRAY[
            'ioc:read','threat:read','sync:bloom',
            'ioc:export','stix:export','sync:delta','ioc:report-fp',
            'apikey:create','apikey:revoke',
            'ioc:submit','ioc:import','webhook:manage',
            'user:manage','tenant:manage','audit:read',
            'ioc:publish','source:manage','source:sync','system:admin']
    END
)
ON CONFLICT (role_id, permission_id) DO NOTHING;
