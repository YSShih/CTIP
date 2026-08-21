-- V4: 種子來源(docs/spec/04-data-dictionary.md 表 2;冪等)
-- MANUAL + 三個 mock 來源;MVP 只啟用 MOCK_OPENPHISH(docs/spec/08-ingestion-sdk.md §8.3)。
INSERT INTO sources (source_type, display_name, description, default_tlp, redistribution_policy,
                     reputation, enabled, syncable, recommended_interval_seconds, requires_credentials)
VALUES
    ('MANUAL', 'Manual Submission',
     '使用者手動提交與檔案匯入(排除於排程與健康狀態轉換)',
     'AMBER', 'INTERNAL_ONLY', 50, true, false, NULL, false),
    ('MOCK_OPENPHISH', 'Mock OpenPhish',
     '確定性 mock 來源:URL / Domain 型釣魚情資(含刻意髒資料)',
     'CLEAR', 'ATTRIBUTION_REQUIRED', 70, true, true, 3600, false),
    ('MOCK_ABUSEIPDB', 'Mock AbuseIPDB',
     '確定性 mock 來源:IPv4 / IPv6 惡意位址(含刻意髒資料)',
     'GREEN', 'DERIVED_ONLY', 65, false, true, 3600, false),
    ('MOCK_ALIENVAULT', 'Mock AlienVault OTX',
     '確定性 mock 來源:混合型別 + STIX 風格 payload(含刻意髒資料)',
     'CLEAR', 'PUBLIC_REDISTRIBUTABLE', 60, false, true, 3600, false)
ON CONFLICT (source_type) DO NOTHING;
