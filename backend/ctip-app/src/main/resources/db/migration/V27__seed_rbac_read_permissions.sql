-- V27: 補兩個唯讀權限(docs/spec/10-identity-plans.md §10.3;冪等)
--
-- Phase 13 收尾稽核發現 GET /api/v1/sources(×3)與 GET /api/v1/stats(×2)完全沒有授權宣告,
-- 而 SecurityConfig 是 anyRequest().permitAll() + 純方法層授權 ——「沒標註」等於「全開」,
-- 一把 scope 不含 ioc:read 的 API key 仍讀得到這五個端點,§14.4 條號 6 的保證在此失效。
-- §10.3 原本沒有對應的權限 code,故補 source:read 與 stats:read(ADR 0013)。
--
-- 兩者比照 ioc:read / threat:read / sync:bloom:五個角色全部持有,匿名行為不變。
-- 權限總數 19 → 21(§10.3 清單、04 表 12 同步更新)。

INSERT INTO permissions (code, description) VALUES
    ('source:read', '讀取情資來源清單與健康狀態'),
    ('stats:read',  '讀取公開統計')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = ANY (ARRAY['source:read', 'stats:read'])
WHERE r.code IN ('ANONYMOUS', 'USER', 'PREMIUM_USER', 'TENANT_ADMIN', 'SYSTEM_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;
