-- 開發樣本資料(docs/spec/14-testing.md §14.7;僅 mvp/dev profile 由 spring.sql.init 載入)
-- 冪等:每次啟動都會執行,全部 INSERT 皆 ON CONFLICT DO NOTHING。
-- 涵蓋:demo tenant、1,020 筆 IOC(六種型別 × CLEAR/GREEN/AMBER/AMBER_STRICT × 四種 status)、
--       indicator_sources、若干 STIX 物件與關聯。不含 TLP:RED、不含真實 secret。

-- 範例 tenant(固定 UUID,冪等)
INSERT INTO tenants (id, slug, name, type, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'demo', 'Demo Organization', 'ORGANIZATION', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- 1,020 筆 IOC:i % 6 決定型別、i % 4 決定 TLP、i % 24 決定 status。
-- CLEAR/GREEN 歸 public tenant,AMBER/AMBER_STRICT 歸 demo tenant(TLP 可見度模型)。
WITH gen AS (
    SELECT i,
           (ARRAY['IPV4','IPV6','DOMAIN','URL','FILE_HASH','EMAIL'])[i % 6 + 1]      AS ioc_type,
           (ARRAY['CLEAR','GREEN','AMBER','AMBER_STRICT'])[i % 4 + 1]                 AS ioc_tlp,
           (ARRAY['MD5','SHA1','SHA256','SHA512'])[(i / 6) % 4 + 1]                   AS fh_algo,
           (ARRAY['INFO','LOW','MEDIUM','HIGH','CRITICAL'])[i % 5 + 1]                AS ioc_severity,
           now() - ((i % 365) || ' days')::interval - interval '2 hours'              AS seen_first
    FROM generate_series(0, 1019) AS s(i)
), shaped AS (
    SELECT i, ioc_type, ioc_tlp, ioc_severity, seen_first,
           seen_first + ((i % 72) || ' hours')::interval AS seen_last,
           CASE WHEN i % 24 = 0 AND ioc_type <> 'FILE_HASH' THEN 'EXPIRED'
                WHEN i % 24 = 1 THEN 'REVOKED'
                WHEN i % 24 = 2 THEN 'FALSE_POSITIVE'
                ELSE 'ACTIVE' END AS ioc_status,
           CASE WHEN ioc_type = 'FILE_HASH' THEN fh_algo END AS ioc_hash_type,
           CASE ioc_type
                WHEN 'IPV4'  THEN '45.' || (10 + i % 200) || '.' || (1 + (i * 3) % 250) || '.' || (1 + (i * 7) % 250)
                WHEN 'IPV6'  THEN '2a06:98c0:' || to_hex(4096 + i) || '::' || to_hex(1 + i * 3)
                WHEN 'DOMAIN' THEN 'mal-' || i || '.ctip-sample.net'
                WHEN 'URL'   THEN 'https://mal-' || i || '.ctip-sample.net/phish/' || i
                WHEN 'FILE_HASH' THEN
                     CASE fh_algo
                          WHEN 'MD5'  THEN md5('ctip-sample-' || i)
                          WHEN 'SHA1' THEN encode(digest('ctip-sample-' || i, 'sha1'), 'hex')
                          WHEN 'SHA256' THEN encode(digest('ctip-sample-' || i, 'sha256'), 'hex')
                          ELSE encode(digest('ctip-sample-' || i, 'sha512'), 'hex')
                     END
                ELSE 'phisher' || i || '@mal-' || (i % 50) || '.ctip-sample.net'
           END AS ioc_value,
           CASE WHEN ioc_tlp IN ('CLEAR','GREEN')
                THEN '00000000-0000-0000-0000-000000000000'::uuid
                ELSE '00000000-0000-0000-0000-000000000001'::uuid
           END AS tenant_id
    FROM gen
)
INSERT INTO indicators (owner_tenant_id, type, hash_type, value, normalized_value, fingerprint,
                        first_seen, last_seen, valid_from, valid_until,
                        confidence, severity, score, tlp, status, tags, source_count)
SELECT tenant_id, ioc_type, ioc_hash_type, ioc_value, lower(ioc_value),
       encode(digest(lower(ioc_value), 'sha256'), 'hex'),
       seen_first, seen_last, seen_first,
       CASE WHEN ioc_type = 'FILE_HASH' THEN NULL
            WHEN ioc_status = 'EXPIRED' THEN seen_last - interval '1 hour'
            WHEN ioc_type = 'IPV4' OR ioc_type = 'IPV6' THEN seen_last + interval '30 days'
            ELSE seen_last + interval '90 days' END,
       (i * 13) % 101, ioc_severity, (i * 29) % 101, ioc_tlp, ioc_status,
       ARRAY['sample', (ARRAY['phishing','malware','c2'])[i % 3 + 1]], 1
FROM shaped
ON CONFLICT DO NOTHING;

-- 每筆樣本 IOC 掛一筆 indicator_sources(以值長度決定 mock 來源,確定性)
INSERT INTO indicator_sources (indicator_id, source_id, source_value, source_confidence,
                               source_severity, source_tlp, source_first_seen, source_last_seen,
                               source_valid_until, redistribution_policy, report_count, status)
SELECT ind.id, s.id, ind.value, ind.confidence, ind.severity, ind.tlp,
       ind.first_seen, ind.last_seen, NULL, s.redistribution_policy, 1,
       CASE ind.status WHEN 'REVOKED' THEN 'RETRACTED'
                       WHEN 'FALSE_POSITIVE' THEN 'FALSE_POSITIVE'
                       WHEN 'EXPIRED' THEN 'EXPIRED'
                       ELSE 'ACTIVE' END
FROM indicators ind
JOIN sources s ON s.source_type =
     (ARRAY['MOCK_OPENPHISH','MOCK_ABUSEIPDB','MOCK_ALIENVAULT'])[length(ind.normalized_value) % 3 + 1]
WHERE 'sample' = ANY (ind.tags)
ON CONFLICT DO NOTHING;

-- 若干 STIX 物件:取 24 筆 public CLEAR ACTIVE 樣本 IOC 的最小 indicator 投影
WITH picked AS (
    SELECT id, normalized_value, tlp, owner_tenant_id, first_seen, last_seen,
           row_number() OVER (ORDER BY normalized_value) AS rn
    FROM indicators
    WHERE 'sample' = ANY (tags)
      AND owner_tenant_id = '00000000-0000-0000-0000-000000000000'::uuid
      AND tlp = 'CLEAR' AND status = 'ACTIVE'
    ORDER BY normalized_value
    LIMIT 24
)
INSERT INTO stix_objects (stix_id, stix_type, owner_tenant_id, indicator_id, tlp,
                          stix_created, stix_modified, content)
SELECT 'indicator--' || id, 'indicator', owner_tenant_id, id, tlp,
       first_seen, last_seen,
       jsonb_build_object(
           'type', 'indicator',
           'spec_version', '2.1',
           'id', 'indicator--' || id,
           'created', to_char(first_seen AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
           'modified', to_char(last_seen AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
           'name', normalized_value,
           'pattern_type', 'stix',
           'pattern', '[network-traffic:value = ''' || normalized_value || ''']',
           'valid_from', to_char(first_seen AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'))
FROM picked
ON CONFLICT DO NOTHING;

-- 若干 STIX 關聯:上述樣本物件兩兩相連(related-to),stix_id 由端點決定(確定性、冪等)
WITH ordered AS (
    SELECT so.stix_id, so.tlp, so.owner_tenant_id, so.stix_created, so.stix_modified,
           row_number() OVER (ORDER BY so.stix_id) AS rn
    FROM stix_objects so
    JOIN indicators ind ON ind.id = so.indicator_id
    WHERE 'sample' = ANY (ind.tags)
), pairs AS (
    SELECT a.stix_id AS source_ref, b.stix_id AS target_ref,
           a.tlp, a.owner_tenant_id, a.stix_created, a.stix_modified,
           md5('ctip-sample-rel:' || a.stix_id || '>' || b.stix_id) AS h
    FROM ordered a
    JOIN ordered b ON b.rn = a.rn + 1
    WHERE a.rn <= 10
)
INSERT INTO stix_relationships (stix_id, relationship_type, source_ref, target_ref,
                                owner_tenant_id, tlp, stix_created, stix_modified)
SELECT 'relationship--' || substr(h, 1, 8) || '-' || substr(h, 9, 4) || '-' || substr(h, 13, 4)
           || '-' || substr(h, 17, 4) || '-' || substr(h, 21, 12),
       'related-to', source_ref, target_ref, owner_tenant_id, tlp, stix_created, stix_modified
FROM pairs
ON CONFLICT DO NOTHING;

-- 方案／訂閱樣本(docs/spec/14-testing.md §14.7;Phase 14)。
-- 四個方案定義本身由 V29__seed_plans_and_permissions.sql 種入(所有環境皆需要);
-- 此處只補 demo tenant 的有效訂閱——需要方案配額的整合測試以此為 fixture。
-- demo tenant 給 PREMIUM:那是唯一同時具備 tenant bloom 容量、手動提交與匯入配額的方案,
-- 涵蓋度最高。provider = MANUAL(M2 不串接金流,方案由 SYSTEM_ADMIN 手動指派)。
INSERT INTO subscriptions (tenant_id, plan_id, status, provider, current_period_start, current_period_end)
SELECT '00000000-0000-0000-0000-000000000001', p.id, 'ACTIVE', 'MANUAL', now(), now() + interval '365 days'
FROM plans p
WHERE p.code = 'PREMIUM'
ON CONFLICT DO NOTHING;
