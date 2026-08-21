# 08 — 攝取管線 · Plugin SDK · 韌性 · 排程

> **規範等級：強制。** SDK 契約、pipeline stage 列表、韌性參數、排程表為規範性內容。
>
> 相關檔案：[07-domain-intel.md](07-domain-intel.md)（正規化與拒絕規則）、[02-ddd-model.md](02-ddd-model.md#25-shared-kernelctip-sdk)

---

## 8.1 Plugin SDK 契約（`ctip-sdk`）

```java
public interface ThreatSourceAdapter {
    SourceType sourceType();
    SourceMetadata metadata();
    FetchResult fetch(FetchContext context);
}
```

```java
public record SourceMetadata(
    String displayName,
    String description,
    String homepageUrl,
    Set<IocType> supportedIocTypes,
    Tlp defaultTlp,
    RedistributionPolicy redistributionPolicy,
    Duration recommendedInterval,
    boolean requiresCredentials
) {}

public record FetchContext(
    Instant since,               // 上次成功同步時間，首次為 null
    String cursor,               // 來源自訂的續抓游標
    Map<String, String> config,  // 來自環境設定，含憑證
    int maxRecords
) {}

public record FetchResult(
    List<RawThreatRecord> records,
    String nextCursor,
    boolean hasMore
) {}

public record RawThreatRecord(
    String rawValue,
    IocType declaredType,        // 來源宣告的型別，可為 null 由平台推斷
    IocHashType declaredHashType,// 僅 FILE_HASH 有意義，可為 null
    Instant observedAt,
    Integer sourceConfidence,    // 0-100，可為 null
    Severity sourceSeverity,     // 可為 null
    Instant validUntil,          // 僅在來源明示時非 null
    Set<String> tags,
    Map<String, Object> rawPayload
) {}
```

> `RawThreatRecord` 相對 v1.1 新增三個欄位：`declaredHashType`、`sourceSeverity`、`validUntil`。
> 前兩者是 `indicator_sources` 的必要輸入（v1.1 的 record 無法填滿該表）；`validUntil` 是 [07](07-domain-intel.md) 三步過期計算的「來源明示」輸入，沒有它整個機制無法區分「來源說永不過期」與「來源沒說」。

### SDK 邊界（ArchUnit 強制）

`ctip-sdk` **不得**出現：任何 `org.springframework.*`、任何 JPA/Hibernate 型別、任何與 CTIP 內部持久化模型耦合的型別。

第三方開發者實作 adapter 時只需依賴 `ctip-sdk`，不需修改核心 ingestion 邏輯。

### Adapter 註冊

**不寫 Factory 類別。** 以 Spring 注入集合即可：

```java
@Component
public class AdapterRegistry {
    private final Map<SourceType, ThreatSourceAdapter> adapters;

    public AdapterRegistry(List<ThreatSourceAdapter> all) {
        this.adapters = all.stream()
            .collect(toUnmodifiableMap(ThreatSourceAdapter::sourceType, identity()));
    }

    public Optional<ThreatSourceAdapter> find(SourceType type) {
        return Optional.ofNullable(adapters.get(type));
    }
}
```

啟動時若同一 `SourceType` 有兩個實作，`toUnmodifiableMap` 會拋出 `IllegalStateException`——這是預期行為，不要改成「後者覆蓋前者」。

### SDK 文件 `[M2]`

`docs/development/plugin-sdk.md` 必須依序說明：實作 adapter、宣告 metadata（含 TLP 與 redistribution policy）、解析來源資料、正規化輸出、註冊 adapter、設定憑證、測試 adapter。
同時提供 `ExampleThreatSourceAdapter` **完整可編譯**範例，並在 CI 中實際編譯。

---

## 8.2 攝取管線

### Pipeline 圖

```text
Threat Source
     ↓  Adapter               (ctip-adapters)
Raw Threat Record
     ↓  1 Parse               (source-specific，adapter 內)
     ↓  2 Validate            (拒絕規則 §7.3 → ingestion_rejections)
     ↓  3 Normalize           (正規化規則 §7.2)
     ↓  4 Fingerprint         (SHA-256 of normalized value)
Domain Indicator (candidate)
     ↓  5 Deduplicate         (依 (type, normalized, tenant) 查既有)
     ↓  6 Merge               (IndicatorMergePolicy §7.5)
     ↓  7 Score               (ThreatScorer §7.6)
     ↓  8 StixProject         (STIX 映射 §7.8)
     ↓  9 Persist             (PostgreSQL — source of truth)
     ↓ 10 BloomUpdate                                        [M2]
     ↓ 11 SearchIndex         (Elasticsearch)                 [M2]
     ↓ 12 PublishEvent        (DomainEvent → ApplicationEventPublisher；M3 轉 Kafka)
```

### 實作方式：明確的 Stage 串接（**禁止繼承**）

```java
public interface IngestionStage {
    String name();
    IngestionContext execute(IngestionContext context);
}
```

`IngestionPipeline` 持有 `List<IngestionStage>`，依註冊順序執行。

**禁止使用抽象基底類別 + 繼承來實作 pipeline。** 理由：

- 順序在一個地方看得完整，不需要追繼承鏈
- 每個 stage 可獨立單元測試
- 新增 stage 不需修改既有類別
- 每個 stage 的耗時與失敗率可獨立度量（見 [13-platform-ops.md](13-platform-ops.md)）

Stage 註冊集中於 `IngestionPipelineConfig`，**順序以顯式 `List.of(...)` 表達，不依賴 `@Order`**：

```java
@Bean
IngestionPipeline ingestionPipeline(ParseStage p, ValidateStage v, NormalizeStage n,
        FingerprintStage f, DeduplicateStage d, MergeStage m, ScoreStage s,
        StixProjectionStage x, PersistStage pe, EventPublishStage ev) {
    return new IngestionPipeline(List.of(p, v, n, f, d, m, s, x, pe, ev));
}
```

M2 起在 `pe` 之後插入 `BloomUpdateStage` 與 `SearchIndexStage`——**只改這一個 `List.of`**。

### 批次與交易邊界

| 規則 |
|---|
| 外部抓取（adapter fetch）**絕不**包在資料庫交易內 |
| 抓取完成後以固定批次（`INGESTION_BATCH_SIZE`，預設 500）進入 pipeline，**每批一個交易** |
| 單筆失敗只丟棄該筆並記錄至 `ingestion_rejections`，**不使整批 rollback** |
| 每次 ingestion 產生一筆 `source_sync` 記錄（成功數／失敗數／合併數／耗時） |
| Domain event 於 `AFTER_COMMIT` 發佈 |

「單筆失敗不 rollback 整批」的實作方式：每筆在 stage 5–9 以 try/catch 包住，失敗則記錄並 `continue`。**不使用 `@Transactional(noRollbackFor=...)`**（語意不對，且容易誤用）。

---

## 8.3 必要的 Mock Adapter `[Phase 5 · M1]`

| Adapter | 型別 |
|---|---|
| `MockOpenPhishAdapter` | URL / Domain |
| `MockAbuseIPDBAdapter` | IPv4 / IPv6 |
| `MockAlienVaultAdapter` | 混合型別 + STIX 風格 payload |

要求：

1. **確定性輸出**：固定 seed，同樣的 `FetchContext` 產生同樣結果。**禁止使用 `Math.random()` 或無 seed 的 `Random`**
2. 必須包含**刻意的髒資料**，且至少覆蓋 [07-domain-intel.md](07-domain-intel.md#73-拒絕規則強制) 的每一種 `reason`：大小寫不一致、前後空白、零寬字元、無效 IP、私有 IP、超長 URL、雜湊長度不符、批次內重複值
3. 三個 mock 之間必須有**刻意重疊的 IOC**（至少 10 個），以驗證多來源合併：其中須包含 confidence 差異大的、severity 不同的、TLP 不同的、以及一個被某來源標為 `RETRACTED` 的
4. **MVP 階段只啟用 `MockOpenPhishAdapter`**（`sources.enabled`），另兩個由 DoD-MVP 的合併測試以測試組態啟用

### `ManualSubmissionAdapter` `[Phase 14 · M2]`

手動提交與匯入**複用同一條 pipeline**，實作為一個 `ThreatSourceAdapter`：

- `sourceType() = MANUAL`
- `metadata().redistributionPolicy = INTERNAL_ONLY`
- `metadata().defaultTlp = AMBER`
- `fetch()` 從 `FetchContext.config` 取出待處理的提交批次

這樣手動提交自動獲得完整的驗證、正規化、去重、合併與稽核，**不需要第二套資料品質邏輯**。

---

## 8.4 未來的真實 Adapter

架構須支援：OpenPhish、AbuseIPDB、AlienVault OTX、URLhaus、Spamhaus、VirusTotal、MISP、TAXII 2.1、custom HTTP feed、STIX feed、JSON feed、CSV feed。

- 真實外部服務**預設不啟用**（`sources.enabled = false`）
- 憑證一律來自環境設定，**絕不進 `sources.config`**（該欄只存環境變數名稱）
- 每個真實 adapter 上線前必須確認並記錄其 ToS 的再散布限制，寫入 `sources.redistribution_policy`

---

## 8.5 韌性（Resilience4j）

外部來源 adapter 必須支援：

| 機制 | 預設值 |
|---|---|
| Timeout | connect 5s / read 30s |
| Retry | 3 次，指數退避（1s、2s、4s），**加上 jitter** |
| Circuit breaker | 失敗率 50%（滑動視窗 20 次）→ 開啟 60s |
| Bulkhead | 每個來源最多 2 個並行抓取 |

**單一來源故障不得使整個 ingestion 系統停止。** 排程任務逐一處理各來源，互不影響（`SourceSyncService` 對每個來源獨立 try/catch）。

Resilience4j 的裝配集中於 `ctip-adapters/http/`，以組態方式套用於所有 HTTP adapter，**不要求每個 adapter 自己加註解**。

---

## 8.6 來源健康

追蹤欄位見 [04-data-dictionary.md](04-data-dictionary.md)（`sources` 表）。

```text
SourceStatus: ACTIVE | DEGRADED | FAILED | DISABLED
```

轉換規則（`SourceHealth` 值物件內，不變量 S2–S4）：

| 條件 | 轉換 |
|---|---|
| 連續失敗 3 次 | → `DEGRADED` |
| 連續失敗 10 次 | → `FAILED`，並發出 `SourceFailed` 事件 |
| 一次成功 | → `ACTIVE`，`consecutiveFailures = 0` |
| 管理員操作 | → `DISABLED`（**只能**手動進入與離開） |
| `syncable = false` | 不參與轉換，恆為 `ACTIVE` |

錯誤訊息寫入 `last_error_message` 前**必須經過遮罩**（S5）：移除任何看似 token／key／password 的片段。

---

## 8.7 <a id="排程"></a>排程

使用 Spring `@Scheduled`（**不引入 Quartz**，M1–M3 皆為單一實例）。

| 任務 | Phase | 預設排程 | 環境變數 |
|---|---|---|---|
| 來源同步 | M1 | 每來源依 `recommendedInterval` | `SOURCE_SYNC_CRON` |
| IOC 過期標記 | M1 | 每日 03:00 | `IOC_EXPIRY_CRON` |
| 失敗 ingestion 重試 | M1 | 每 15 分鐘 | `INGESTION_RETRY_CRON` |
| Bloom full snapshot | M2 | 每日 04:00 | `BLOOM_SNAPSHOT_CRON` |
| Bloom delta 生成 | M2 | 每小時 | `BLOOM_DELTA_CRON` |
| Elasticsearch reconciliation | M2 | 每日 05:00 | `ES_RECONCILE_CRON` |
| 過期 token 清理 | M2 | 每日 02:00 | `TOKEN_CLEANUP_CRON` |
| 通知重試 | M3 | 每 5 分鐘 | `NOTIFICATION_RETRY_CRON` |
| 稽核保留清理 | M3 | 每週日 01:00 | `AUDIT_CLEANUP_CRON` |
| 原始 payload 清理 | M3 | 每日 01:30 | `PAYLOAD_CLEANUP_CRON` |
| 拒絕記錄清理 | M3 | 每日 01:40 | `REJECTION_CLEANUP_CRON` |
| Bloom artifact 清理 | M3 | 每日 01:50 | `BLOOM_ARTIFACT_CLEANUP_CRON` |

規則：
- 全部排程由 `SCHEDULER_ENABLED` 總開關控制（測試環境關閉）
- 每個任務的 cron 皆可由環境變數覆寫
- 每個排程任務**只做一件事，且必須呼叫 application service 的方法**，任務類別本身不含業務邏輯
- 若未來需要多實例，改用 ShedLock（保留擴充點，M1–M3 不實作）

> **與多實例限流的關係**：[15-dod-gates.md](15-dod-gates.md) 的 DoD-Phase2 有一條「Redis 限流在多實例下正確」。該項驗證的是**限流實作**在多實例下的正確性（以兩個 app 容器測試），與排程假設單一實例並不衝突——限流是請求路徑，排程是背景任務。若正式環境要跑多實例，排程必須先引入 ShedLock，這一點須寫入 `docs/deployment/`。

---

*檔案結束。上次校對：2026-08-21。*
