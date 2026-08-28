# 04 — 資料字典（Data Dictionary）

> **規範等級：強制。** 本檔是所有資料表的唯一定義來源。任何 Flyway migration、JPA entity、Mermaid ERD 都必須與本檔一致。發現不一致時，以本檔為準並回報。
>
> 相關檔案：[02-ddd-model.md](02-ddd-model.md)（聚合邊界）、[03-diagrams.md](03-diagrams.md)（ERD）、[01-architecture.md](01-architecture.md)（模型分層規則）

---

## 4.0 通用約定（強制）

| 項目 | 規則 |
|---|---|
| 主鍵 | 一律 `UUID`，`DEFAULT gen_random_uuid()`（PostgreSQL 18 內建 `pgcrypto`） |
| 時間欄位 | 一律 `TIMESTAMPTZ`。應用層一律 `java.time.Instant` |
| 列舉 | DB 端以 `VARCHAR(n)` + `CHECK` 約束表示。**不使用 PostgreSQL enum 型別**（難以變更） |
| 布林 | `BOOLEAN NOT NULL DEFAULT`，不允許三態 |
| 金額／分數 | 整數，避免浮點。`confidence`／`score` 為 `SMALLINT` 0–100 |
| 租戶欄位 | 所有 tenant-scoped 表的 `tenant_id` 皆 `NOT NULL`，且必須出現在至少一個複合索引的第一欄 |
| `created_at` / `updated_at` | 所有表皆有，`NOT NULL DEFAULT now()`。`updated_at` 由應用層維護（JPA `@PreUpdate`），**不使用 DB trigger** |
| 軟刪除 | 僅 `indicators` 使用（`deleted_at`）。其餘表一律硬刪除或永不刪除 |
| 命名 | 表名複數 snake_case；欄位 snake_case；索引 `ix_<表>_<欄位>`；唯一索引 `ux_<表>_<語意>`；外鍵 `fk_<表>_<參照表>` |

### JSONB 白名單（強制）

**只有以下四個欄位允許 `JSONB`。** 新增 JSONB 欄位視為規格違規，必須改為正規化欄位或獨立表：

1. `indicator_sources.raw_payload`
2. `sources.config`
3. `audit_logs.metadata`
4. `stix_objects.content`

---

## 4.1 表清單與模型分層

模型分層依 [01-architecture.md](01-architecture.md) 的 Q5 判準：

> **判準**：該表是否有跨欄位不變量，或是否有狀態機？
> 有 → 屬於某個聚合 → **三模型**（domain record／JPA entity／DTO）
> 沒有（純參考資料、關聯表、append-only 記錄、衍生投影）→ **兩模型**（JPA entity／DTO，無 domain model）

| # | 表 | Phase | 所屬聚合 | 模型分層 |
|---|---|---|---|---|
| 1 | `tenants` | M1 | **Tenant**（根） | 三模型 |
| 2 | `sources` | M1 | **Source**（根） | 三模型 |
| 3 | `source_sync` | M1 | — | 兩模型 |
| 4 | `indicators` | M1 | **Indicator**（根） | 三模型 |
| 5 | `indicator_sources` | M1 | Indicator（內部實體） | 三模型 |
| 6 | `hash_records` | M1 | Indicator（內部實體） | 三模型 |
| 7 | `ingestion_rejections` | M1 | — | 兩模型 |
| 8 | `stix_objects` | M1 | — （衍生投影） | 兩模型 |
| 9 | `stix_relationships` | M1 | — （衍生投影） | 兩模型 |
| 10 | `users` | M2 | **User**（根） | 三模型 |
| 11 | `roles` | M2 | — （參考資料） | 兩模型 |
| 12 | `permissions` | M2 | — （參考資料） | 兩模型 |
| 13 | `role_permissions` | M2 | — （關聯） | 兩模型 |
| 14 | `tenant_users` | M2 | — （關聯） | 兩模型 |
| 15 | `refresh_tokens` | M2 | User（內部實體） | 三模型 |
| 16 | `api_keys` | M2 | **ApiKey**（根） | 三模型 |
| 17 | `plans` | M2 | — （參考資料） | 兩模型 |
| 18 | `subscriptions` | M2 | **Subscription**（根） | 三模型 |
| 19 | `threats` | M2 | **Threat**（根） | 三模型 |
| 20 | `threat_indicators` | M2 | Threat（內部實體） | 三模型 |
| 21 | `threat_external_references` | M2 | Threat（值物件集合） | 三模型 |
| 22 | `bloom_versions` | M2 | **BloomVersion**（根） | 三模型 |
| 23 | `bloom_artifacts` | M2 | BloomVersion（內部實體） | 三模型 |
| 24 | `webhooks` | M3 | **Webhook**（根） | 三模型 |
| 25 | `webhook_deliveries` | M3 | — | 兩模型 |
| 26 | `notifications` | M3 | — | 兩模型 |
| 27 | `audit_logs` | M3 | — （append-only） | 兩模型 |

> **共 27 張表。** v1.1 的 §36.1 列了 26 張；新增 `threat_external_references`（v1.1 的 §18.1 把它定義為 Threat 內的 `List<ExternalReference>`，但存 JSONB 會違反白名單，§61 的 ERD 又把它畫成獨立實體 — 此處定為獨立表，同時解決三節不一致）。
>
> 九個聚合根：**Tenant、Source、Indicator、User、ApiKey、Subscription、Threat、BloomVersion、Webhook**。

---

## 4.2 M1 資料表

### 1. `tenants` `[Phase 3 · M1]`

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `slug` | VARCHAR(64) | NO | — | URL 安全識別碼，小寫英數與 `-` |
| `name` | VARCHAR(255) | NO | — | 顯示名稱 |
| `type` | VARCHAR(32) | NO | — | `SYSTEM` / `INDIVIDUAL` / `ORGANIZATION` / `ENTERPRISE` |
| `status` | VARCHAR(32) | NO | `'ACTIVE'` | `ACTIVE` / `SUSPENDED` |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |
| `updated_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_tenants_slug        UNIQUE (slug)
CONSTRAINT ck_tenants_type        CHECK (type IN ('SYSTEM','INDIVIDUAL','ORGANIZATION','ENTERPRISE'))
CONSTRAINT ck_tenants_status      CHECK (status IN ('ACTIVE','SUSPENDED'))
CONSTRAINT ck_tenants_slug_format CHECK (slug ~ '^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$')
```

**Public System Tenant（由 `V2__seed_system_tenant.sql` 種入，不可刪除）**

```text
id     = 00000000-0000-0000-0000-000000000000
slug   = public
name   = Public
type   = SYSTEM
status = ACTIVE
```

`V2` 同時建立 `trg_tenants_protect_public` 觸發器（BEFORE UPDATE OR DELETE）：
拒絕對 public tenant 的 DELETE 與 slug/name/type 變更——不變量 T2 的 DB 層深度防禦
（2026-08-21 新增，ADR 0001 決策 1）。domain 層自 Phase 4 起仍為第一道防線。
（此觸發器與 4.0「`updated_at` 不使用 DB trigger」不衝突——該規則僅針對 `updated_at` 維護。）

不變量見 [02-ddd-model.md](02-ddd-model.md#tenant)。

---

### 2. `sources` `[Phase 3 建表 · Phase 5 使用 · M1]`

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `source_type` | VARCHAR(64) | NO | — | 對應 SDK 的 `SourceType`，例：`MOCK_OPENPHISH`、`MANUAL` |
| `display_name` | VARCHAR(255) | NO | — | 來自 `SourceMetadata.displayName` |
| `description` | TEXT | YES | — | |
| `homepage_url` | VARCHAR(2048) | YES | — | |
| `default_tlp` | VARCHAR(16) | NO | `'CLEAR'` | `CLEAR`/`GREEN`/`AMBER`/`AMBER_STRICT`/`RED` |
| `redistribution_policy` | VARCHAR(32) | NO | `'INTERNAL_ONLY'` | 見 4.5 列舉 |
| `reputation` | SMALLINT | NO | `50` | 0–100，合併加權用（[07](07-domain-intel.md)） |
| `enabled` | BOOLEAN | NO | `false` | 真實外部來源預設不啟用 |
| `syncable` | BOOLEAN | NO | `true` | `MANUAL` 來源為 `false`，排除於排程與健康狀態轉換 |
| `recommended_interval_seconds` | INTEGER | YES | — | 來自 `SourceMetadata.recommendedInterval` |
| `requires_credentials` | BOOLEAN | NO | `false` | |
| `config` | JSONB | NO | `'{}'` | 來源設定。**憑證不存於此**，僅存環境變數名稱參照 |
| `status` | VARCHAR(32) | NO | `'ACTIVE'` | `ACTIVE`/`DEGRADED`/`FAILED`/`DISABLED` |
| `consecutive_failures` | INTEGER | NO | `0` | 狀態轉換依據 |
| `last_sync_at` | TIMESTAMPTZ | YES | — | |
| `last_success_at` | TIMESTAMPTZ | YES | — | |
| `last_failure_at` | TIMESTAMPTZ | YES | — | |
| `last_error_message` | TEXT | YES | — | 不得含憑證 |
| `avg_latency_ms` | INTEGER | YES | — | 移動平均 |
| `total_records_ingested` | BIGINT | NO | `0` | |
| `next_cursor` | VARCHAR(1024) | YES | — | 來源自訂續抓游標 |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |
| `updated_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_sources_source_type UNIQUE (source_type)
CONSTRAINT ck_sources_tlp         CHECK (default_tlp IN ('CLEAR','GREEN','AMBER','AMBER_STRICT','RED'))
CONSTRAINT ck_sources_redist      CHECK (redistribution_policy IN
              ('PUBLIC_REDISTRIBUTABLE','ATTRIBUTION_REQUIRED','DERIVED_ONLY','INTERNAL_ONLY'))
CONSTRAINT ck_sources_status      CHECK (status IN ('ACTIVE','DEGRADED','FAILED','DISABLED'))
CONSTRAINT ck_sources_reputation  CHECK (reputation BETWEEN 0 AND 100)

CREATE INDEX ix_sources_enabled_status ON sources (enabled, status) WHERE syncable = true;
```

**種子資料**：`MANUAL`（`syncable=false`、`reputation=50`、`redistribution_policy=INTERNAL_ONLY`、`enabled=true`）+ 三個 mock 來源。

---

### 3. `source_sync` `[Phase 6 · M1]`

每次 ingestion 執行一列。append-only——精確語意（2026-08-25 Phase 6 實測釐清，ADR 0004）：
列於同步**開始時**以 `result = 'RUNNING'` 建立（fetch 前即可見，獨立交易），**結束時回寫一次**終態
（`SUCCESS`/`PARTIAL`/`FAILURE` + 計數 + `finished_at`）；終態之後不再更新。
`finished_at IS NULL` 即表示仍在執行或異常中斷——這正是本表要建立即寫入的理由。

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `source_id` | UUID | NO | — | FK → `sources.id` |
| `started_at` | TIMESTAMPTZ | NO | — | |
| `finished_at` | TIMESTAMPTZ | YES | — | null 表示仍在執行或異常中斷 |
| `duration_ms` | INTEGER | YES | — | |
| `result` | VARCHAR(32) | NO | `'RUNNING'` | `RUNNING`/`SUCCESS`/`PARTIAL`/`FAILURE` |
| `records_fetched` | INTEGER | NO | `0` | |
| `records_accepted` | INTEGER | NO | `0` | |
| `records_rejected` | INTEGER | NO | `0` | |
| `records_merged` | INTEGER | NO | `0` | 命中既有 indicator 而合併的筆數 |
| `error_message` | TEXT | YES | — | |
| `trace_id` | VARCHAR(64) | YES | — | 對應日誌與 API 錯誤回應 |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT fk_source_sync_sources FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE
CONSTRAINT ck_source_sync_result  CHECK (result IN ('RUNNING','SUCCESS','PARTIAL','FAILURE'))

CREATE INDEX ix_source_sync_source_started ON source_sync (source_id, started_at DESC);
```

---

### 4. `indicators` `[Phase 4 · M1]`

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `owner_tenant_id` | UUID | NO | — | FK → `tenants.id`。公開情資為 public tenant |
| `type` | VARCHAR(16) | NO | — | `IPV4`/`IPV6`/`DOMAIN`/`URL`/`FILE_HASH`/`EMAIL` |
| `hash_type` | VARCHAR(16) | YES | — | 僅 `type='FILE_HASH'` 時非 null：`MD5`/`SHA1`/`SHA256`/`SHA512` |
| `value` | VARCHAR(2048) | NO | — | 原始值，保留來源樣貌 |
| `normalized_value` | VARCHAR(2048) | NO | — | 正規化後的 canonical value（[07](07-domain-intel.md)） |
| `fingerprint` | CHAR(64) | NO | — | `SHA-256(normalized_value)` 十六進位小寫 |
| `first_seen` | TIMESTAMPTZ | NO | — | `MIN(source_first_seen)` |
| `last_seen` | TIMESTAMPTZ | NO | — | `MAX(source_last_seen)` |
| `valid_from` | TIMESTAMPTZ | NO | — | 等於 `first_seen` |
| `valid_until` | TIMESTAMPTZ | YES | — | 見 4.4 過期規則。null = 永不過期 |
| `confidence` | SMALLINT | NO | `0` | 0–100，聚合後 |
| `severity` | VARCHAR(16) | NO | `'INFO'` | `INFO`/`LOW`/`MEDIUM`/`HIGH`/`CRITICAL` |
| `score` | SMALLINT | NO | `0` | 0–100，由 `ThreatScorer` 計算 |
| `tlp` | VARCHAR(16) | NO | `'CLEAR'` | 取所有來源中最嚴格者 |
| `status` | VARCHAR(16) | NO | `'ACTIVE'` | `ACTIVE`/`EXPIRED`/`REVOKED`/`FALSE_POSITIVE` |
| `tags` | TEXT[] | NO | `'{}'` | 所有來源 tags 的聯集 |
| `source_count` | SMALLINT | NO | `0` | 獨立 ACTIVE 來源數，快取值 |
| `deleted_at` | TIMESTAMPTZ | YES | — | 軟刪除（保留政策，[13](13-platform-ops.md)） |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |
| `updated_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT fk_indicators_tenant   FOREIGN KEY (owner_tenant_id) REFERENCES tenants(id)
CONSTRAINT ck_indicators_type     CHECK (type IN ('IPV4','IPV6','DOMAIN','URL','FILE_HASH','EMAIL'))
CONSTRAINT ck_indicators_hashtype CHECK (
    (type = 'FILE_HASH' AND hash_type IN ('MD5','SHA1','SHA256','SHA512'))
 OR (type <> 'FILE_HASH' AND hash_type IS NULL))
CONSTRAINT ck_indicators_tlp      CHECK (tlp IN ('CLEAR','GREEN','AMBER','AMBER_STRICT','RED'))
CONSTRAINT ck_indicators_severity CHECK (severity IN ('INFO','LOW','MEDIUM','HIGH','CRITICAL'))
CONSTRAINT ck_indicators_status   CHECK (status IN ('ACTIVE','EXPIRED','REVOKED','FALSE_POSITIVE'))
CONSTRAINT ck_indicators_conf     CHECK (confidence BETWEEN 0 AND 100)
CONSTRAINT ck_indicators_score    CHECK (score BETWEEN 0 AND 100)
CONSTRAINT ck_indicators_seen     CHECK (last_seen >= first_seen)
CONSTRAINT ck_indicators_fp       CHECK (fingerprint ~ '^[0-9a-f]{64}$')
```

**索引（強制）**

```sql
CREATE UNIQUE INDEX ux_indicators_identity
  ON indicators (type, normalized_value, owner_tenant_id);

CREATE INDEX ix_indicators_fingerprint   ON indicators (fingerprint);
CREATE INDEX ix_indicators_tenant_status ON indicators (owner_tenant_id, status, tlp);
CREATE INDEX ix_indicators_last_seen     ON indicators (last_seen DESC, id DESC);
CREATE INDEX ix_indicators_valid_until   ON indicators (valid_until) WHERE status = 'ACTIVE';
CREATE INDEX ix_indicators_tags          ON indicators USING GIN (tags);
CREATE INDEX ix_indicators_value_trgm    ON indicators USING GIN (normalized_value gin_trgm_ops);
```

> `ix_indicators_last_seen` 的複合排序鍵是 cursor 分頁（[09-api.md](09-api.md)）所必需，**不可移除**。
> `pg_trgm` extension 由 `V1__initial_schema.sql` 建立。

不變量見 [02-ddd-model.md](02-ddd-model.md#indicator)。

---

### 5. `indicator_sources` `[Phase 4 建表 · Phase 7 使用 · M1]`

每個 `(indicator, source)` **一列**。同來源再次回報時 **UPSERT** 該列（更新 `source_last_seen`、`source_confidence`、`raw_payload`、`report_count`）。跨來源永不互相覆寫。

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `indicator_id` | UUID | NO | — | FK → `indicators.id` |
| `source_id` | UUID | NO | — | FK → `sources.id` |
| `source_value` | VARCHAR(2048) | NO | — | 該來源回報的原始值 |
| `source_confidence` | SMALLINT | YES | — | 0–100，來源未提供則 null |
| `source_severity` | VARCHAR(16) | YES | — | 來源未提供則 null |
| `source_tlp` | VARCHAR(16) | NO | — | 來源未提供則取 `sources.default_tlp` |
| `source_first_seen` | TIMESTAMPTZ | NO | — | |
| `source_last_seen` | TIMESTAMPTZ | NO | — | |
| `source_valid_until` | TIMESTAMPTZ | YES | — | **僅在來源明示時非 null**（見 4.4） |
| `redistribution_policy` | VARCHAR(32) | NO | — | **ingestion 當下的快照**，不隨 `sources` 變更 |
| `report_count` | INTEGER | NO | `1` | 該來源回報此 IOC 的累計次數 |
| `raw_payload` | JSONB | YES | — | 保留 30 天後清空（[13](13-platform-ops.md)） |
| `status` | VARCHAR(16) | NO | `'ACTIVE'` | `ACTIVE`/`EXPIRED`/`RETRACTED`/`FALSE_POSITIVE` |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |
| `updated_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_indicator_sources UNIQUE (indicator_id, source_id)
CONSTRAINT fk_is_indicator FOREIGN KEY (indicator_id) REFERENCES indicators(id) ON DELETE CASCADE
CONSTRAINT fk_is_source    FOREIGN KEY (source_id)    REFERENCES sources(id)
CONSTRAINT ck_is_tlp       CHECK (source_tlp IN ('CLEAR','GREEN','AMBER','AMBER_STRICT','RED'))
CONSTRAINT ck_is_status    CHECK (status IN ('ACTIVE','EXPIRED','RETRACTED','FALSE_POSITIVE'))
CONSTRAINT ck_is_redist    CHECK (redistribution_policy IN
              ('PUBLIC_REDISTRIBUTABLE','ATTRIBUTION_REQUIRED','DERIVED_ONLY','INTERNAL_ONLY'))
CONSTRAINT ck_is_conf      CHECK (source_confidence IS NULL OR source_confidence BETWEEN 0 AND 100)
CONSTRAINT ck_is_seen      CHECK (source_last_seen >= source_first_seen)
CONSTRAINT ck_is_count     CHECK (report_count >= 1)

CREATE INDEX ix_is_source_status ON indicator_sources (source_id, status);
CREATE INDEX ix_is_payload_gc    ON indicator_sources (updated_at) WHERE raw_payload IS NOT NULL;
```

---

### 6. `hash_records` `[Phase 7 · M1]`

指紋記錄。保留多演算法擴充點（M1 僅 `SHA256`）。

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `indicator_id` | UUID | NO | — | FK → `indicators.id` |
| `source_id` | UUID | YES | — | FK → `sources.id`。null = 平台計算 |
| `algorithm` | VARCHAR(16) | NO | — | `SHA256`/`SHA512`（`FingerprintAlgorithm`） |
| `digest` | VARCHAR(128) | NO | — | 十六進位小寫 |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_hash_records  UNIQUE (algorithm, digest, indicator_id)
CONSTRAINT fk_hr_indicator  FOREIGN KEY (indicator_id) REFERENCES indicators(id) ON DELETE CASCADE
CONSTRAINT fk_hr_source     FOREIGN KEY (source_id)    REFERENCES sources(id)
CONSTRAINT ck_hr_algorithm  CHECK (algorithm IN ('SHA256','SHA512'))
CONSTRAINT ck_hr_digest     CHECK (digest ~ '^[0-9a-f]+$')

CREATE INDEX ix_hash_records_digest ON hash_records (digest);
```

> **命名釐清**：本表存的是**去重指紋**（`FingerprintAlgorithm`），與 `indicators.hash_type`（`IocHashType`，IOC 本身是檔案雜湊）是**兩件不同的事**。見 [07-domain-intel.md](07-domain-intel.md)。

---

### 7. `ingestion_rejections` `[Phase 6 · M1]`

被拒絕的記錄，供品質分析。append-only，保留 30 天。

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `source_id` | UUID | NO | — | FK → `sources.id` |
| `source_sync_id` | UUID | YES | — | FK → `source_sync.id` |
| `raw_value` | VARCHAR(4096) | NO | — | 截斷至 4096 字元 |
| `declared_type` | VARCHAR(16) | YES | — | 來源宣告的型別 |
| `reason` | VARCHAR(64) | NO | — | 見下方列舉 |
| `detail` | TEXT | YES | — | 人類可讀說明 |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |

```text
reason: MALFORMED_VALUE | PRIVATE_OR_RESERVED_IP | ALLOWLISTED_DOMAIN
      | LENGTH_EXCEEDED  | HASH_LENGTH_MISMATCH   | UNKNOWN_TYPE
      | DUPLICATE_IN_BATCH | QUOTA_EXCEEDED
```

```sql
CONSTRAINT fk_ir_source FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE
CONSTRAINT fk_ir_sync   FOREIGN KEY (source_sync_id) REFERENCES source_sync(id) ON DELETE SET NULL
CONSTRAINT ck_ir_reason CHECK (reason IN ('MALFORMED_VALUE','PRIVATE_OR_RESERVED_IP',
              'ALLOWLISTED_DOMAIN','LENGTH_EXCEEDED','HASH_LENGTH_MISMATCH','UNKNOWN_TYPE',
              'DUPLICATE_IN_BATCH','QUOTA_EXCEEDED'))

CREATE INDEX ix_ir_source_created ON ingestion_rejections (source_id, created_at DESC);
CREATE INDEX ix_ir_gc             ON ingestion_rejections (created_at);
```

---

### 8. `stix_objects` `[Phase 8 · M1]`

STIX 2.1 物件的**衍生投影**。PostgreSQL 的 domain model 才是 source of truth；本表可隨時由 domain 重建。

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK（內部） |
| `stix_id` | VARCHAR(128) | NO | — | STIX ID，例：`indicator--<uuid>` |
| `stix_type` | VARCHAR(64) | NO | — | 見 [07](07-domain-intel.md) 支援清單 |
| `spec_version` | VARCHAR(8) | NO | `'2.1'` | |
| `owner_tenant_id` | UUID | NO | — | FK → `tenants.id` |
| `indicator_id` | UUID | YES | — | FK → `indicators.id`，來源 domain 物件 |
| `threat_id` | UUID | YES | — | FK → `threats.id`，來源 domain 物件 |
| `tlp` | VARCHAR(16) | NO | — | 繼承自來源 domain 物件，過濾用 |
| `stix_created` | TIMESTAMPTZ | NO | — | STIX `created` |
| `stix_modified` | TIMESTAMPTZ | NO | — | STIX `modified` |
| `content` | JSONB | NO | — | 完整 STIX 物件 |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |
| `updated_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_stix_objects_stix_id UNIQUE (stix_id)
CONSTRAINT fk_so_tenant    FOREIGN KEY (owner_tenant_id) REFERENCES tenants(id)
CONSTRAINT fk_so_indicator FOREIGN KEY (indicator_id) REFERENCES indicators(id) ON DELETE CASCADE
-- fk_so_threat 不在 V7 建立:threats 表屬 M2(V31),M1 不得建立。
-- V31 建立 threats 後以 ALTER TABLE 補上(見 §4.7;ADR 0001 決策 2、ADR 0014):
--   ALTER TABLE stix_objects ADD CONSTRAINT fk_so_threat
--     FOREIGN KEY (threat_id) REFERENCES threats(id) ON DELETE CASCADE;
CONSTRAINT ck_so_tlp       CHECK (tlp IN ('CLEAR','GREEN','AMBER','AMBER_STRICT','RED'))
CONSTRAINT ck_so_origin    CHECK (
      (indicator_id IS NOT NULL AND threat_id IS NULL)
   OR (indicator_id IS NULL AND threat_id IS NOT NULL)
   OR (indicator_id IS NULL AND threat_id IS NULL))   -- marking-definition 等無 domain 來源者

CREATE INDEX ix_so_tenant_tlp  ON stix_objects (owner_tenant_id, tlp);
CREATE INDEX ix_so_type        ON stix_objects (stix_type);
CREATE INDEX ix_so_indicator   ON stix_objects (indicator_id);
```

> **M1 只產生 `indicator`、`marking-definition`、`bundle` 三種**（`threat_id` 相關者為 M2）。理由與映射規則見 [07-domain-intel.md](07-domain-intel.md)。

---

### 9. `stix_relationships` `[Phase 8 · M1]`

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `stix_id` | VARCHAR(128) | NO | — | `relationship--<uuid>` |
| `relationship_type` | VARCHAR(64) | NO | — | 例：`indicates`、`related-to` |
| `source_ref` | VARCHAR(128) | NO | — | 來源物件的 STIX ID |
| `target_ref` | VARCHAR(128) | NO | — | 目標物件的 STIX ID |
| `owner_tenant_id` | UUID | NO | — | FK → `tenants.id` |
| `tlp` | VARCHAR(16) | NO | — | 取兩端最嚴格者 |
| `stix_created` | TIMESTAMPTZ | NO | — | |
| `stix_modified` | TIMESTAMPTZ | NO | — | |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_stix_rel_stix_id UNIQUE (stix_id)
CONSTRAINT ux_stix_rel_triple  UNIQUE (relationship_type, source_ref, target_ref)
CONSTRAINT fk_sr_tenant FOREIGN KEY (owner_tenant_id) REFERENCES tenants(id)
CONSTRAINT ck_sr_tlp    CHECK (tlp IN ('CLEAR','GREEN','AMBER','AMBER_STRICT','RED'))
CONSTRAINT ck_sr_no_self CHECK (source_ref <> target_ref)

CREATE INDEX ix_sr_source ON stix_relationships (source_ref);
CREATE INDEX ix_sr_target ON stix_relationships (target_ref);
```

---

## 4.3 M2 資料表

### 10. `users` `[Phase 13 · M2]`

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `email` | VARCHAR(320) | NO | — | 小寫正規化後儲存 |
| `password_hash` | VARCHAR(255) | NO | — | BCrypt cost 12 或 Argon2id |
| `display_name` | VARCHAR(255) | YES | — | |
| `status` | VARCHAR(32) | NO | `'ACTIVE'` | `ACTIVE`/`SUSPENDED`/`PENDING_VERIFICATION` |
| `primary_tenant_id` | UUID | NO | — | FK → `tenants.id`。**不可為 public tenant** |
| `last_login_at` | TIMESTAMPTZ | YES | — | |
| `failed_login_count` | SMALLINT | NO | `0` | |
| `locked_until` | TIMESTAMPTZ | YES | — | 暴力破解防護 |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |
| `updated_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_users_email  UNIQUE (email)
CONSTRAINT fk_users_tenant FOREIGN KEY (primary_tenant_id) REFERENCES tenants(id)
CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','SUSPENDED','PENDING_VERIFICATION'))
CONSTRAINT ck_users_email  CHECK (email = lower(email))
CONSTRAINT ck_users_not_public
    CHECK (primary_tenant_id <> '00000000-0000-0000-0000-000000000000'::uuid)

CREATE INDEX ix_users_tenant ON users (primary_tenant_id);
```

> `ck_users_not_public` 以 DB 層強制 §24.2 的「Public tenant 無使用者、不可登入」。

---

### 11. `roles` / 12. `permissions` / 13. `role_permissions` `[Phase 13 · M2]`

**`roles`**（參考資料，由 migration 種入）

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `code` | VARCHAR(32) | NO | — | `ANONYMOUS`/`USER`/`PREMIUM_USER`/`TENANT_ADMIN`/`SYSTEM_ADMIN` |
| `name` | VARCHAR(64) | NO | — | 顯示名稱 |
| `description` | TEXT | YES | — | |
| `tenant_scoped` | BOOLEAN | NO | `true` | `SYSTEM_ADMIN` 為 `false` |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_roles_code UNIQUE (code)
CONSTRAINT ck_roles_code CHECK (code IN
    ('ANONYMOUS','USER','PREMIUM_USER','TENANT_ADMIN','SYSTEM_ADMIN'))
```

**`permissions`**（參考資料）

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `code` | VARCHAR(64) | NO | — | 例：`ioc:read`、`ioc:export` |
| `description` | TEXT | YES | — | |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_permissions_code UNIQUE (code)
CONSTRAINT ck_permissions_fmt  CHECK (code ~ '^[a-z]+:[a-z-]+$')
```

完整權限清單（種子資料，共 21 項）：

```text
ioc:read      ioc:export     ioc:submit      ioc:import      ioc:report-fp   ioc:publish
threat:read   stix:export
source:read   stats:read
sync:bloom    sync:delta
apikey:create apikey:revoke
webhook:manage
tenant:manage user:manage
audit:read
source:manage source:sync
system:admin
```

> `ioc:submit`、`ioc:import`、`ioc:report-fp`、`source:sync` 為 v2.0 新增，對應 [09-api.md](09-api.md) 的寫入端點。
>
> **實作回饋修訂（2026-08-28，Phase 13 收尾稽核；ADR 0013 決策 1）**：本清單原寫「共 19 項」但
> 實際只列了 18 個——漏掉 [10 §10.3](10-identity-plans.md#103-使用者與-rbac-phase-13--m2) 有的
> `ioc:publish`，已補回。另新增 `source:read`、`stats:read` 兩個唯讀權限（`GET /sources`、`GET /stats`
> 原本完全沒有授權宣告，而 filter chain 對路徑一律 `permitAll`），種子由
> `V27__seed_rbac_read_permissions.sql` 補入。合計 21 項。

**`role_permissions`**（關聯）

| 欄位 | 型別 | NULL | 說明 |
|---|---|---|---|
| `role_id` | UUID | NO | FK → `roles.id` |
| `permission_id` | UUID | NO | FK → `permissions.id` |

```sql
CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id)
CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
```

---

### 14. `tenant_users` `[Phase 13 · M2]`

一個使用者可屬於多個 tenant，每個 tenant 內有各自角色。

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `tenant_id` | UUID | NO | — | FK → `tenants.id` |
| `user_id` | UUID | NO | — | FK → `users.id` |
| `role_id` | UUID | NO | — | FK → `roles.id` |
| `joined_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT pk_tenant_users PRIMARY KEY (tenant_id, user_id)
CONSTRAINT fk_tu_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
CONSTRAINT fk_tu_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE
CONSTRAINT fk_tu_role   FOREIGN KEY (role_id)   REFERENCES roles(id)
CONSTRAINT ck_tu_not_public
    CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'::uuid)

CREATE INDEX ix_tu_user ON tenant_users (user_id);
```

---

### 15. `refresh_tokens` `[Phase 13 · M2]`

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `user_id` | UUID | NO | — | FK → `users.id` |
| `token_hash` | CHAR(64) | NO | — | `SHA-256(token)`。**絕不存原文** |
| `family_id` | UUID | NO | — | 輪替家族。重用偵測以此撤銷整族 |
| `parent_id` | UUID | YES | — | FK → `refresh_tokens.id`，輪替前一枚 |
| `issued_at` | TIMESTAMPTZ | NO | `now()` | |
| `expires_at` | TIMESTAMPTZ | NO | — | 預設 `issued_at + 30 天` |
| `used_at` | TIMESTAMPTZ | YES | — | 非 null = 已使用，再次出現即為重用 |
| `revoked_at` | TIMESTAMPTZ | YES | — | |
| `revoked_reason` | VARCHAR(32) | YES | — | `LOGOUT`/`ROTATED`/`REUSE_DETECTED`/`ADMIN`/`EXPIRED_CLEANUP` |
| `user_agent` | VARCHAR(512) | YES | — | |
| `ip` | INET | YES | — | |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_rt_hash   UNIQUE (token_hash)
CONSTRAINT fk_rt_user   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
CONSTRAINT fk_rt_parent FOREIGN KEY (parent_id) REFERENCES refresh_tokens(id) ON DELETE SET NULL
CONSTRAINT ck_rt_reason CHECK (revoked_reason IS NULL OR revoked_reason IN
              ('LOGOUT','ROTATED','REUSE_DETECTED','ADMIN','EXPIRED_CLEANUP'))
CONSTRAINT ck_rt_expiry CHECK (expires_at > issued_at)

CREATE INDEX ix_rt_user_family ON refresh_tokens (user_id, family_id);
CREATE INDEX ix_rt_gc          ON refresh_tokens (expires_at);
```

---

### 16. `api_keys` `[Phase 13 · M2]`

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `tenant_id` | UUID | NO | — | FK → `tenants.id` |
| `user_id` | UUID | NO | — | FK → `users.id`，建立者 |
| `name` | VARCHAR(128) | NO | — | 使用者自訂標籤 |
| `key_prefix` | CHAR(8) | NO | — | **隨機段**前 8 碼明碼，供 UI 辨識與定位（見 [10 §10.5](10-identity-plans.md#105-api-key-phase-13--m2)） |
| `key_hash` | CHAR(64) | NO | — | `SHA-256(full key)` |
| `scopes` | TEXT[] | NO | `'{}'` | 必為 `permissions.code` 的子集 |
| `expires_at` | TIMESTAMPTZ | YES | — | null = 不過期 |
| `last_used_at` | TIMESTAMPTZ | YES | — | 非同步更新，容許最多 60 秒延遲 |
| `revoked_at` | TIMESTAMPTZ | YES | — | |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |
| `updated_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_api_keys_hash   UNIQUE (key_hash)
CONSTRAINT ux_api_keys_prefix UNIQUE (key_prefix)
CONSTRAINT fk_ak_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
CONSTRAINT fk_ak_user   FOREIGN KEY (user_id)   REFERENCES users(id)
CONSTRAINT ck_ak_not_public
    CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'::uuid)

CREATE INDEX ix_ak_tenant ON api_keys (tenant_id) WHERE revoked_at IS NULL;
```

- 完整 key 格式：`ctip_<env>_<32 random base62>`（`env` ∈ `mvp|dev|stg|prod`）
- **完整 key 僅在建立當下回傳一次**，之後永不可查
- `ux_api_keys_prefix` 讓前綴可直接定位單一 key，避免全表雜湊比對

---

### 17. `plans` `[Phase 14 · M2]`

§23.2 的配額表**必須全部存於本表**，不得 hard-code。`.env` 可覆寫（見 [10-identity-plans.md](10-identity-plans.md)）。

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `code` | VARCHAR(32) | NO | — | `ANONYMOUS`/`FREE`/`PREMIUM`/`ENTERPRISE` |
| `name` | VARCHAR(64) | NO | — | |
| `tier` | SMALLINT | NO | — | 排序用，0=ANONYMOUS … 3=ENTERPRISE |
| `requests_per_minute` | INTEGER | NO | — | |
| `requests_per_day` | INTEGER | YES | — | null = 依合約（ENTERPRISE） |
| `max_page_size` | INTEGER | NO | — | |
| `max_batch_lookup` | INTEGER | NO | — | 批次精確驗證單次上限 |
| `min_sync_interval_seconds` | INTEGER | NO | — | |
| `public_bloom_enabled` | BOOLEAN | NO | `true` | |
| `tenant_bloom_capacity` | BIGINT | YES | — | null = 無 tenant bloom |
| `websocket_enabled` | BOOLEAN | NO | `false` | |
| `max_webhooks` | INTEGER | NO | `0` | |
| `max_api_keys` | INTEGER | NO | `0` | |
| `custom_feed_enabled` | BOOLEAN | NO | `false` | |
| `stix_export_max_objects` | INTEGER | YES | — | null = 無限制；0 = 不允許 |
| `max_manual_submissions_per_day` | INTEGER | NO | `0` | 本版新增 |
| `max_import_rows_per_file` | INTEGER | NO | `0` | 本版新增 |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |
| `updated_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_plans_code UNIQUE (code)
CONSTRAINT ux_plans_tier UNIQUE (tier)
CONSTRAINT ck_plans_code CHECK (code IN ('ANONYMOUS','FREE','PREMIUM','ENTERPRISE'))
CONSTRAINT ck_plans_tier CHECK (tier BETWEEN 0 AND 3)
```

**種子資料（`V29__seed_plans.sql`，冪等）**

| 欄位 | ANONYMOUS | FREE | PREMIUM | ENTERPRISE |
|---|---|---|---|---|
| `tier` | 0 | 1 | 2 | 3 |
| `requests_per_minute` | 60 | 300 | 1200 | 6000 |
| `requests_per_day` | 1000 | 20000 | 500000 | *null* |
| `max_page_size` | 50 | 100 | 500 | 1000 |
| `max_batch_lookup` | 20 | 100 | 1000 | 5000 |
| `min_sync_interval_seconds` | 86400 | 21600 | 300 | 60 |
| `public_bloom_enabled` | true | true | true | true |
| `tenant_bloom_capacity` | *null* | *null* | 1000000 | 10000000 |
| `websocket_enabled` | false | false | true | true |
| `max_webhooks` | 0 | 0 | 5 | 50 |
| `max_api_keys` | 0 | 1 | 10 | 100 |
| `custom_feed_enabled` | false | false | false | true |
| `stix_export_max_objects` | 0 | 1000 | 50000 | *null* |
| `max_manual_submissions_per_day` | 0 | 0 | 1000 | 50000 |
| `max_import_rows_per_file` | 0 | 0 | 10000 | 500000 |

> ⚠️ **本表刻意沒有 `max_tlp` 或任何 TLP 相關欄位。** TLP 可見度由「認證狀態 + 資料歸屬」決定，與方案完全解耦（見 [07-domain-intel.md](07-domain-intel.md#tlp-可見度) 與 [02-ddd-model.md](02-ddd-model.md)）。新增此類欄位視為規格違規。

---

### 18. `subscriptions` `[Phase 14 · M2]`

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `tenant_id` | UUID | NO | — | FK → `tenants.id` |
| `plan_id` | UUID | NO | — | FK → `plans.id` |
| `status` | VARCHAR(32) | NO | `'ACTIVE'` | `ACTIVE`/`PAST_DUE`/`CANCELLED`/`EXPIRED` |
| `provider` | VARCHAR(32) | NO | `'NONE'` | `NONE`/`STRIPE`/`MANUAL`。MVP 與 M2 皆為 `NONE` 或 `MANUAL` |
| `external_subscription_id` | VARCHAR(255) | YES | — | 供未來金流串接 |
| `current_period_start` | TIMESTAMPTZ | NO | `now()` | |
| `current_period_end` | TIMESTAMPTZ | YES | — | null = 無期限 |
| `cancelled_at` | TIMESTAMPTZ | YES | — | |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |
| `updated_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_subscriptions_active UNIQUE (tenant_id) WHERE status = 'ACTIVE'  -- 部分唯一索引
CONSTRAINT fk_sub_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
CONSTRAINT fk_sub_plan   FOREIGN KEY (plan_id)   REFERENCES plans(id)
CONSTRAINT ck_sub_status CHECK (status IN ('ACTIVE','PAST_DUE','CANCELLED','EXPIRED'))
CONSTRAINT ck_sub_prov   CHECK (provider IN ('NONE','STRIPE','MANUAL'))
```

> PostgreSQL 的部分唯一約束需寫成 `CREATE UNIQUE INDEX ux_subscriptions_active ON subscriptions (tenant_id) WHERE status = 'ACTIVE';`（不能寫在 `CONSTRAINT` 子句中）。此約束強制「一個 tenant 同時只有一份有效訂閱」。

---

### 18b. `import_jobs` `[Phase 14 · M2]`

> **本表 2026-08-28 新增（[ADR 0019](../architecture/decisions/0019-phase14-16-spec-resolutions.md)）**：
> [09 §9.7](09-api.md#97-寫入端點細節-m2) 的 `POST /iocs/import` 定義為「非同步處理，回 `202` +
> `importJobId`，以 `GET /iocs/import/{jobId}` 查詢進度」，而
> [13 §13.5](13-platform-ops.md) 的稽核也已把 `import_job` 當成 `resource_type`——
> 但**沒有任何資料表能承載 job 狀態**。編號用 `18b` 以免動到既有表號。

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK；即對外的 `importJobId` |
| `tenant_id` | UUID | NO | — | FK → `tenants.id` |
| `submitted_by` | UUID | NO | — | FK → `users.id` |
| `status` | VARCHAR(16) | NO | `'PENDING'` | `PENDING`/`RUNNING`/`SUCCESS`/`PARTIAL`/`FAILURE` |
| `format` | VARCHAR(16) | NO | — | `CSV`/`STIX_BUNDLE` |
| `total_rows` | INTEGER | YES | — | 解析後的總筆數；`PENDING` 時為 null |
| `accepted_count` | INTEGER | NO | `0` | 新建立 |
| `merged_count` | INTEGER | NO | `0` | 合併進既有 IOC |
| `rejected_count` | INTEGER | NO | `0` | 逐筆 rejection 明細在 `ingestion_rejections` |
| `error_message` | VARCHAR(1024) | YES | — | 整批失敗的原因（解析錯誤等） |
| `started_at` | TIMESTAMPTZ | YES | — | |
| `finished_at` | TIMESTAMPTZ | YES | — | 終態才寫入 |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |
| `updated_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT pk_import_jobs PRIMARY KEY (id)
CONSTRAINT fk_ij_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
CONSTRAINT fk_ij_user   FOREIGN KEY (submitted_by) REFERENCES users(id)
CONSTRAINT ck_ij_status CHECK (status IN ('PENDING','RUNNING','SUCCESS','PARTIAL','FAILURE'))
CONSTRAINT ck_ij_format CHECK (format IN ('CSV','STIX_BUNDLE'))
CONSTRAINT ck_ij_counts CHECK (accepted_count >= 0 AND merged_count >= 0 AND rejected_count >= 0)

CREATE INDEX ix_import_jobs_tenant ON import_jobs (tenant_id, created_at DESC);
```

> 逐筆 rejection 沿用既有的 `ingestion_rejections`（表 6），以 `import_job_id` 關聯——
> Phase 14 須為該表加上此欄位（nullable，來源同步的 rejection 仍為 null）。

---

### 19. `threats` `[Phase 18 · M2]`

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `owner_tenant_id` | UUID | NO | — | FK → `tenants.id` |
| `type` | VARCHAR(32) | NO | — | `CAMPAIGN`/`MALWARE_FAMILY`/`THREAT_ACTOR`/`ATTACK_PATTERN`/`PHISHING_KIT` |
| `name` | VARCHAR(255) | NO | — | |
| `aliases` | TEXT[] | NO | `'{}'` | |
| `description` | TEXT | YES | — | |
| `severity` | VARCHAR(16) | NO | `'INFO'` | |
| `confidence` | SMALLINT | NO | `0` | 0–100 |
| `tlp` | VARCHAR(16) | NO | `'CLEAR'` | |
| `status` | VARCHAR(16) | NO | `'ACTIVE'` | `ACTIVE`/`DORMANT`/`RETIRED` |
| `first_seen` | TIMESTAMPTZ | NO | — | |
| `last_seen` | TIMESTAMPTZ | NO | — | |
| `tags` | TEXT[] | NO | `'{}'` | |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |
| `updated_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_threats_identity UNIQUE (owner_tenant_id, type, name)
CONSTRAINT fk_threats_tenant   FOREIGN KEY (owner_tenant_id) REFERENCES tenants(id)
CONSTRAINT ck_threats_type     CHECK (type IN ('CAMPAIGN','MALWARE_FAMILY','THREAT_ACTOR',
                                              'ATTACK_PATTERN','PHISHING_KIT'))
CONSTRAINT ck_threats_status   CHECK (status IN ('ACTIVE','DORMANT','RETIRED'))
CONSTRAINT ck_threats_severity CHECK (severity IN ('INFO','LOW','MEDIUM','HIGH','CRITICAL'))
CONSTRAINT ck_threats_tlp      CHECK (tlp IN ('CLEAR','GREEN','AMBER','AMBER_STRICT','RED'))
CONSTRAINT ck_threats_conf     CHECK (confidence BETWEEN 0 AND 100)
CONSTRAINT ck_threats_seen     CHECK (last_seen >= first_seen)

CREATE INDEX ix_threats_tenant_status ON threats (owner_tenant_id, status, tlp);
CREATE INDEX ix_threats_aliases       ON threats USING GIN (aliases);
CREATE INDEX ix_threats_last_seen     ON threats (last_seen DESC, id DESC);
```

> **M1 不建立本表。** 由 `V31__create_threats.sql` 於 M2 建立。

---

### 20. `threat_indicators` `[Phase 18 · M2]`

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `threat_id` | UUID | NO | — | FK → `threats.id` |
| `indicator_id` | UUID | NO | — | FK → `indicators.id` |
| `role` | VARCHAR(32) | NO | `'UNKNOWN'` | `C2`/`DELIVERY`/`PAYLOAD`/`INFRASTRUCTURE`/`VICTIM`/`UNKNOWN` |
| `added_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT pk_threat_indicators PRIMARY KEY (threat_id, indicator_id)
CONSTRAINT fk_ti_threat    FOREIGN KEY (threat_id)    REFERENCES threats(id)    ON DELETE CASCADE
CONSTRAINT fk_ti_indicator FOREIGN KEY (indicator_id) REFERENCES indicators(id) ON DELETE CASCADE
CONSTRAINT ck_ti_role CHECK (role IN ('C2','DELIVERY','PAYLOAD','INFRASTRUCTURE','VICTIM','UNKNOWN'))

CREATE INDEX ix_ti_indicator ON threat_indicators (indicator_id);
```

---

### 21. `threat_external_references` `[Phase 18 · M2]`

Threat 聚合內的值物件集合。**本版新增**（v1.1 定義為 `List<ExternalReference>`，但存 JSONB 會違反 4.0 白名單）。

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `threat_id` | UUID | NO | — | FK → `threats.id` |
| `source_name` | VARCHAR(64) | NO | — | 例：`mitre-attack`、`cve`、`nvd` |
| `external_id` | VARCHAR(128) | YES | — | 例：`T1566`、`CVE-2026-1234` |
| `url` | VARCHAR(2048) | YES | — | |
| `description` | TEXT | YES | — | |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
-- H4 的唯一性:external_id 可為 null,而 PostgreSQL 的 UNIQUE 不去重 null
-- (§6.3.6 自己列的地雷)。必須以 COALESCE 建唯一索引,否則 external_id IS NULL 時
-- H4 完全不被強制(ADR 0020)
CONSTRAINT ux_ter_identity UNIQUE (threat_id, source_name, external_id)  -- 見下方 CREATE UNIQUE INDEX
CONSTRAINT fk_ter_threat   FOREIGN KEY (threat_id) REFERENCES threats(id) ON DELETE CASCADE
CONSTRAINT ck_ter_has_ref  CHECK (external_id IS NOT NULL OR url IS NOT NULL)

CREATE INDEX ix_ter_external ON threat_external_references (source_name, external_id);

-- H4 的實際強制(取代上面那條 UNIQUE 的 null 語意缺口):
CREATE UNIQUE INDEX ux_ter_identity_coalesced
  ON threat_external_references (threat_id, source_name, COALESCE(external_id, ''));
```

---

### 22. `bloom_versions` `[Phase 15 · M2]`

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `scope` | VARCHAR(16) | NO | — | `PUBLIC`/`TENANT` |
| `tenant_id` | UUID | NO | — | FK → `tenants.id`。`scope=PUBLIC` 時為 public tenant |
| `dataset_version` | BIGINT | NO | — | full snapshot 遞增 |
| `bloom_version` | BIGINT | NO | — | delta 遞增 |
| `fingerprint_algorithm` | VARCHAR(16) | NO | `'SHA256'` | |
| `hash_function_count` | SMALLINT | NO | — | k |
| `bit_size` | BIGINT | NO | — | m |
| `capacity` | BIGINT | NO | — | n |
| `false_positive_rate` | DOUBLE PRECISION | NO | — | |
| `member_count` | BIGINT | NO | — | |
| `is_full_snapshot` | BOOLEAN | NO | — | true = full，false = delta |
| `base_bloom_version` | BIGINT | YES | — | delta 的基底版本；full 為 null |
| `generated_at` | TIMESTAMPTZ | NO | `now()` | |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_bv_version UNIQUE (scope, tenant_id, dataset_version, bloom_version)
CONSTRAINT fk_bv_tenant  FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
CONSTRAINT ck_bv_scope   CHECK (scope IN ('PUBLIC','TENANT'))
CONSTRAINT ck_bv_algo    CHECK (fingerprint_algorithm IN ('SHA256','SHA512'))
CONSTRAINT ck_bv_fpr     CHECK (false_positive_rate > 0 AND false_positive_rate < 1)
CONSTRAINT ck_bv_base    CHECK ((is_full_snapshot AND base_bloom_version IS NULL)
                             OR (NOT is_full_snapshot AND base_bloom_version IS NOT NULL))
CONSTRAINT ck_bv_public_tenant CHECK (
    scope <> 'PUBLIC' OR tenant_id = '00000000-0000-0000-0000-000000000000'::uuid)

CREATE INDEX ix_bv_lookup ON bloom_versions (scope, tenant_id, bloom_version DESC);
```

---

### 23. `bloom_artifacts` `[Phase 15 · M2]`

實際位元陣列的儲存位置與校驗資訊。**本版補完定義**（v1.1 僅在表清單中出現）。

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `bloom_version_id` | UUID | NO | — | FK → `bloom_versions.id` |
| `storage_kind` | VARCHAR(16) | NO | `'FILESYSTEM'` | `FILESYSTEM`/`S3`/`INLINE` |
| `storage_path` | VARCHAR(1024) | NO | — | 檔案路徑或物件鍵 |
| `compression` | VARCHAR(8) | NO | `'ZSTD'` | `GZIP`/`ZSTD`/`NONE` |
| `size_bytes` | BIGINT | NO | — | 壓縮後大小 |
| `uncompressed_size_bytes` | BIGINT | NO | — | |
| `checksum` | CHAR(64) | NO | — | 未壓縮位元陣列的 `SHA-256` |
| `resulting_checksum` | CHAR(64) | YES | — | delta 套用後的預期 checksum；full 為 null |
| `download_count` | BIGINT | NO | `0` | |
| `expires_at` | TIMESTAMPTZ | YES | — | 下載 URL 有效期 |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_ba_version UNIQUE (bloom_version_id)
CONSTRAINT fk_ba_version FOREIGN KEY (bloom_version_id)
              REFERENCES bloom_versions(id) ON DELETE CASCADE
CONSTRAINT ck_ba_kind    CHECK (storage_kind IN ('FILESYSTEM','S3','INLINE'))
CONSTRAINT ck_ba_comp    CHECK (compression IN ('GZIP','ZSTD','NONE'))
CONSTRAINT ck_ba_sum     CHECK (checksum ~ '^[0-9a-f]{64}$')

CREATE INDEX ix_ba_gc ON bloom_artifacts (created_at);
```

---

## 4.4 M3 資料表

### 24. `webhooks` `[Phase 20 · M3]`

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `tenant_id` | UUID | NO | — | FK → `tenants.id` |
| `created_by_user_id` | UUID | NO | — | FK → `users.id` |
| `name` | VARCHAR(128) | NO | — | |
| `target_url` | VARCHAR(2048) | NO | — | 必須為 HTTPS |
| `secret_encrypted` | BYTEA | NO | — | HMAC 簽章密鑰,**以 AES-GCM 加密儲存**(金鑰 `WEBHOOK_SECRET_KEK`)。原文僅建立時回傳一次。2026-08-28 由 `secret_hash CHAR(64)` 改——只存雜湊時伺服器重建不出 secret,無法計算送達簽章([ADR 0021](../architecture/decisions/0021-phase20-23-spec-resolutions.md)) |
| `event_types` | TEXT[] | NO | `'{}'` | 訂閱的事件型別，見 4.5 |
| `filter_ioc_types` | TEXT[] | NO | `'{}'` | 空 = 不限 |
| `filter_min_severity` | VARCHAR(16) | YES | — | 門檻，null = 不限 |
| `filter_tags` | TEXT[] | NO | `'{}'` | 空 = 不限 |
| `filter_source_ids` | UUID[] | NO | `'{}'` | 空 = 不限 |
| `status` | VARCHAR(16) | NO | `'ACTIVE'` | `ACTIVE`/`SUSPENDED`/`DISABLED` |
| `consecutive_failures` | SMALLINT | NO | `0` | 達 5 次 → `DISABLED` 並通知 |
| `last_delivery_at` | TIMESTAMPTZ | YES | — | |
| `last_success_at` | TIMESTAMPTZ | YES | — | |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |
| `updated_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT fk_wh_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
CONSTRAINT fk_wh_user   FOREIGN KEY (created_by_user_id) REFERENCES users(id)
CONSTRAINT ck_wh_status CHECK (status IN ('ACTIVE','SUSPENDED','DISABLED'))
CONSTRAINT ck_wh_https  CHECK (target_url LIKE 'https://%')
CONSTRAINT ck_wh_sev    CHECK (filter_min_severity IS NULL OR
              filter_min_severity IN ('INFO','LOW','MEDIUM','HIGH','CRITICAL'))

CREATE INDEX ix_wh_tenant_status ON webhooks (tenant_id, status);
```

> 每租戶的 webhook 數量上限由 `plans.max_webhooks` 於應用層強制（DB 不設約束，因上限可調）。

---

### 25. `webhook_deliveries` `[Phase 20 · M3]`

append-only，保留 30 天。

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `webhook_id` | UUID | NO | — | FK → `webhooks.id` |
| `event_id` | UUID | NO | — | 對應 domain event 的 `eventId`，冪等鍵 |
| `event_type` | VARCHAR(64) | NO | — | |
| `attempt` | SMALLINT | NO | `1` | 1–5 |
| `status` | VARCHAR(16) | NO | — | `PENDING`/`SUCCESS`/`FAILED`/`ABANDONED` |
| `http_status` | SMALLINT | YES | — | |
| `response_time_ms` | INTEGER | YES | — | |
| `error_message` | TEXT | YES | — | |
| `next_retry_at` | TIMESTAMPTZ | YES | — | 指數退避 |
| `delivered_at` | TIMESTAMPTZ | YES | — | |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_wd_idempotent UNIQUE (webhook_id, event_id, attempt)
CONSTRAINT fk_wd_webhook FOREIGN KEY (webhook_id) REFERENCES webhooks(id) ON DELETE CASCADE
CONSTRAINT ck_wd_status  CHECK (status IN ('PENDING','SUCCESS','FAILED','ABANDONED'))
CONSTRAINT ck_wd_attempt CHECK (attempt BETWEEN 1 AND 5)

CREATE INDEX ix_wd_retry ON webhook_deliveries (next_retry_at) WHERE status = 'FAILED';
CREATE INDEX ix_wd_gc    ON webhook_deliveries (created_at);
```

---

### 26. `notifications` `[Phase 20 · M3]`

站內通知（WebSocket / 通知中心頁）。

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `tenant_id` | UUID | NO | — | FK → `tenants.id` |
| `user_id` | UUID | YES | — | FK → `users.id`。null = 全租戶廣播 |
| `event_id` | UUID | NO | — | 冪等鍵 |
| `event_type` | VARCHAR(64) | NO | — | 見 4.5 |
| `title` | VARCHAR(255) | NO | — | |
| `body` | TEXT | YES | — | |
| `severity` | VARCHAR(16) | NO | `'INFO'` | |
| `resource_type` | VARCHAR(64) | YES | — | 例：`indicator`、`source` |
| `resource_id` | UUID | YES | — | |
| `read_at` | TIMESTAMPTZ | YES | — | |
| `created_at` | TIMESTAMPTZ | NO | `now()` | |

```sql
CONSTRAINT ux_notif_idempotent UNIQUE (event_id, tenant_id, user_id)
CONSTRAINT fk_notif_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
CONSTRAINT fk_notif_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE
CONSTRAINT ck_notif_sev    CHECK (severity IN ('INFO','LOW','MEDIUM','HIGH','CRITICAL'))

CREATE INDEX ix_notif_user_unread ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;
```

> `ux_notif_idempotent`：PostgreSQL 的 `UNIQUE` 對 null 不去重，因此廣播型通知（`user_id IS NULL`）需改用 `CREATE UNIQUE INDEX ... ON notifications (event_id, tenant_id, COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::uuid));`

---

### 27. `audit_logs` `[Phase 21 · M3]`

**append-only。** DB 層以 `REVOKE UPDATE, DELETE` 強制；僅保留清理任務使用專用角色。

| 欄位 | 型別 | NULL | 預設 | 說明 |
|---|---|---|---|---|
| `id` | UUID | NO | `gen_random_uuid()` | PK |
| `occurred_at` | TIMESTAMPTZ | NO | `now()` | |
| `actor_type` | VARCHAR(16) | NO | — | `ANONYMOUS`/`USER`/`API_KEY`/`SYSTEM` |
| `actor_id` | UUID | YES | — | user_id 或 api_key_id |
| `tenant_id` | UUID | NO | — | FK → `tenants.id` |
| `action` | VARCHAR(64) | NO | — | 見 4.5 |
| `resource_type` | VARCHAR(64) | YES | — | |
| `resource_id` | UUID | YES | — | |
| `ip` | INET | YES | — | |
| `user_agent` | VARCHAR(512) | YES | — | |
| `result` | VARCHAR(16) | NO | — | `SUCCESS`/`FAILURE`/`DENIED` |
| `trace_id` | VARCHAR(64) | YES | — | |
| `metadata` | JSONB | YES | — | **絕不含憑證、token 原文、密碼** |

```sql
CONSTRAINT fk_al_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
CONSTRAINT ck_al_actor  CHECK (actor_type IN ('ANONYMOUS','USER','API_KEY','SYSTEM'))
CONSTRAINT ck_al_result CHECK (result IN ('SUCCESS','FAILURE','DENIED'))

CREATE INDEX ix_al_tenant_time ON audit_logs (tenant_id, occurred_at DESC);
CREATE INDEX ix_al_actor       ON audit_logs (actor_type, actor_id, occurred_at DESC);
CREATE INDEX ix_al_action      ON audit_logs (action, occurred_at DESC);
CREATE INDEX ix_al_gc          ON audit_logs (occurred_at);
```

**無 `updated_at` 欄位**——本表永不更新，加上該欄位即為設計錯誤。

---

## 4.5 共用列舉（單一定義來源）

以下列舉**定義於 `ctip-sdk`**（Shared Kernel，見 [01-architecture.md](01-architecture.md)）。DB 端以 `VARCHAR` + `CHECK` 對應。

```text
IocType              IPV4 | IPV6 | DOMAIN | URL | FILE_HASH | EMAIL
IocHashType          MD5 | SHA1 | SHA256 | SHA512
FingerprintAlgorithm SHA256 | SHA512
Tlp                  CLEAR | GREEN | AMBER | AMBER_STRICT | RED
Severity             INFO | LOW | MEDIUM | HIGH | CRITICAL
RedistributionPolicy PUBLIC_REDISTRIBUTABLE | ATTRIBUTION_REQUIRED
                     | DERIVED_ONLY | INTERNAL_ONLY
```

以下列舉定義於 `ctip-core/domain`：

```text
IndicatorStatus      ACTIVE | EXPIRED | REVOKED | FALSE_POSITIVE
SourceRecordStatus   ACTIVE | EXPIRED | RETRACTED | FALSE_POSITIVE
SourceStatus         ACTIVE | DEGRADED | FAILED | DISABLED
SyncResult           RUNNING | SUCCESS | PARTIAL | FAILURE
TenantType           SYSTEM | INDIVIDUAL | ORGANIZATION | ENTERPRISE
ThreatType           CAMPAIGN | MALWARE_FAMILY | THREAT_ACTOR
                     | ATTACK_PATTERN | PHISHING_KIT
ThreatStatus         ACTIVE | DORMANT | RETIRED
IndicatorRole        C2 | DELIVERY | PAYLOAD | INFRASTRUCTURE | VICTIM | UNKNOWN
BloomScope           PUBLIC | TENANT
RejectionReason      （見表 7）
WebhookStatus        ACTIVE | SUSPENDED | DISABLED
DeliveryStatus       PENDING | SUCCESS | FAILED | ABANDONED
SubscriptionStatus   ACTIVE | PAST_DUE | CANCELLED | EXPIRED
```

**事件型別**（`notifications.event_type`、`webhooks.event_types`、Kafka payload）

```text
NEW_IOC | THREAT_UPDATED | IOC_REVOKED | SOURCE_FAILURE
SUBSCRIPTION_CHANGED | SYNC_SNAPSHOT_READY | SYSTEM_ALERT
```

**稽核行為**（`audit_logs.action`）

```text
LOGIN | LOGIN_FAILED | LOGOUT | TOKEN_REFRESH | TOKEN_REUSE_DETECTED
API_ACCESS | IOC_QUERY | IOC_DOWNLOAD | IOC_SUBMIT | IOC_IMPORT | IOC_REPORT_FP
STIX_EXPORT | SYNC_MANIFEST | SYNC_BLOOM | SYNC_DELTA
INGESTION_STARTED | INGESTION_COMPLETED | INGESTION_FAILED
ADMIN_ACTION | TENANT_CREATED | USER_CREATED
API_KEY_CREATED | API_KEY_REVOKED
SUBSCRIPTION_CHANGED | WEBHOOK_CREATED | WEBHOOK_DELETED
```

> `IOC_SUBMIT`、`IOC_IMPORT`、`IOC_REPORT_FP` 為本版新增，對應寫入端點。

---

## 4.6 過期與 TTL 規則（強制，跨表）

### 型別預設 TTL

| `IocType` | 預設 TTL |
|---|---|
| `IPV4` / `IPV6` | 30 天 |
| `DOMAIN` | 90 天 |
| `URL` | 90 天 |
| `EMAIL` | 90 天 |
| `FILE_HASH` | **不過期**（null） |

### `valid_until` 計算（三步，強制順序）

> 此規則取代 v1.1 §19.3 的「任一來源為 null 則為 null」——該寫法會讓絕大多數 IOC 永不過期（因為多數來源不回報 `valid_until`），使整個過期機制、每日過期排程、以及 Bloom full snapshot 的存在理由全部失效。

1. `indicator_sources.source_valid_until` **僅在來源明示時**為非 null
2. 每筆來源記錄計算有效期：
   ```text
   effective_valid_until =
       COALESCE(source_valid_until, source_last_seen + defaultTtl(indicator.type))
   ```
   其中 `defaultTtl(FILE_HASH)` 為 null，`COALESCE` 的結果因此為 null
3. 聚合：`indicators.valid_until = MAX(effective_valid_until)`
   — 只有當**所有**來源的 `effective_valid_until` 皆為 null 時，結果才是 null

### 過期排程

每日 03:00（`SCHEDULER_ENABLED` 控制）將 `valid_until < now() AND status = 'ACTIVE'` 的記錄改為 `EXPIRED`。
`EXPIRED` 的 IOC：不進入 Bloom filter、API 預設不回傳（可用 `?includeExpired=true`）。

---

## 4.7 Flyway migration 對應

**版本號一律遞增，依實作順序指派——不預留區段。**

> **實作回饋修訂（2026-08-28，Phase 13 收尾稽核之後；ADR 0014）**
> 本節原本依「表的分組」預留區段（`V1–V19` = M1、`V20–V29` = M2、`V30+` = M3），
> 但 Flyway 是**依版本號排序套用**：Phase 13 用掉 `V20`/`V21`/`V24`/`V27` 後，
> Phase 14 若照原表用 `V22`，既有資料庫在啟動時會直接
> `FlywayValidateException`（實測：驗證失敗、應用起不來，該 migration 不會被套用）。
> Phase 15（`V26`）與 Phase 18（`V25`）同樣低於 `V27`，會踩同一個坑。
> 預留區段與 Flyway 的排序語意天生衝突，故廢除區段、改為依實作順序遞增。
> **已套用的 `V1`–`V7`、`V20`、`V21`、`V24`、`V27` 一律不動**（改動會使 checksum 失效）；
> 其中 `V7`、`V20` 的註解仍寫著舊版本號與舊區段規則，以本表為準。

| Migration | 內容 | 表 |
|---|---|---|
| `V1__initial_schema.sql` | extension（`pgcrypto`、`pg_trgm`）+ `tenants` | 1 |
| `V2__seed_system_tenant.sql` | public system tenant（冪等） | — |
| `V3__create_sources.sql` | `sources`、`source_sync` | 2, 3 |
| `V4__seed_sources.sql` | `MANUAL` + 三個 mock 來源（冪等） | — |
| `V5__create_indicators.sql` | `indicators`、`indicator_sources`、`hash_records` | 4, 5, 6 |
| `V6__create_ingestion_rejections.sql` | `ingestion_rejections` | 7 |
| `V7__create_stix.sql` | `stix_objects`、`stix_relationships`（`threat_id` 欄位保留、FK 延至 `V31`） | 8, 9 |
| `V20__create_users_and_rbac.sql` | `users`、`roles`、`permissions`、`role_permissions`、`tenant_users` | 10–14 |
| `V21__create_auth_tokens.sql` | `refresh_tokens`、`api_keys` | 15, 16 |
| `V28__create_plans.sql` | `plans`、`subscriptions`、`import_jobs` + `ingestion_rejections.import_job_id` | 17, 18, 18b |
| `V29__seed_plans.sql` | 四個方案（冪等） | — |
| `V24__seed_rbac.sql` | 五個角色、19 個權限、角色權限對應（冪等） | — |
| `V31__create_threats.sql` | `threats`、`threat_indicators`、`threat_external_references` + `ALTER TABLE stix_objects ADD CONSTRAINT fk_so_threat …` | 19–21 |
| `V30__create_bloom.sql` | `bloom_versions`、`bloom_artifacts` | 22, 23 |
| `V27__seed_rbac_read_permissions.sql` | 補 `source:read`、`stats:read` 兩個權限與其角色對應（冪等，ADR 0013） | — |
| `V32__create_notifications.sql` | `webhooks`、`webhook_deliveries`、`notifications` | 24–26 |
| `V33__create_audit_logs.sql` | `audit_logs` + `REVOKE UPDATE, DELETE` | 27 |

規則見 [05-environment.md](05-environment.md#59-flyway)。**絕不修改已套用的 migration**，一律新增。

---

*檔案結束。表數：27。上次校對：2026-08-21。*
