# 14 — 測試策略

> **規範等級：強制。** 分層策略、覆蓋率門檻、ArchUnit 規則、安全測試清單為規範性內容。
>
> 相關檔案：[15-dod-gates.md](15-dod-gates.md)（可執行 DoD）、[01-architecture.md](01-architecture.md#19-archunit-規則強制共-11-條)

---

## 14.1 分層策略

Testcontainers 啟動 Kafka / ES 很慢，必須分層，否則 CI 會變成瓶頸。

| 層級 | 內容 | JUnit `@Tag` | 何時執行 | 目標時間 |
|---|---|---|---|---|
| **L1 單元** | 純 domain 邏輯，**無 Spring context** | `unit` | 每次 push | < 60s |
| **L2 切片** | `@WebMvcTest`、`@DataJpaTest`（Testcontainers PostgreSQL） | `slice` | 每次 push | < 5min |
| **L3 整合** | 完整 Spring context + PostgreSQL + Redis | `integration` | 每次 PR | < 10min |
| **L4 重量** | Kafka、Elasticsearch Testcontainers | `heavy` | nightly + release 前 | 不限 |

Maven profile 控制執行範圍：

```bash
./mvnw test                          # L1 only（預設）
./mvnw verify -Ptest-slice           # L1 + L2
./mvnw verify -Ptest-integration     # L1 + L2 + L3
./mvnw verify -Ptest-all             # 全部
```

L1 測試**不得**使用 `@SpringBootTest`、`@MockBean` 或任何 Spring 註解。domain 物件必須可以 `new` 出來直接測——這是 ArchUnit 規則 9（禁止 domain 直接呼叫 `Instant.now()`）存在的理由。

---

> **測試 context 的連線池上限（2026-08-29，Phase 20；[ADR 0029](../architecture/decisions/0029-phase20-kafka-and-notifications.md) 第 9 節）**：
> Spring 的 test context 是**快取的**——整批測試跑完之前，每一個不同組態的 context 都還活著，
> 而每個都有自己的 HikariCP 池。預設的 10 條乘上二十幾個 context 會撞上 PostgreSQL 的
> `max_connections`（預設 100）。
>
> 症狀極具誤導性：`FATAL: remaining connection slots are reserved` 出現在**後面**才載入的
> 那個 context 上，看起來完全像是那個測試自己的問題。
> `AbstractPostgresIntegrationTest` 因此把池上限固定為 4（整合測試是單執行緒的）；
> 另起實例的測試（`DistributedRateLimitTest`）必須同步設定。

## 14.2 後端必須包含的測試

| 類型 | 重點 |
|---|---|
| 單元測試 | 九個聚合的**每一條不變量**（[02-ddd-model.md](02-ddd-model.md#23-聚合不變量)），共 60+ 條 |
| **合併政策測試** | `IndicatorMergePolicy` 的每一欄聚合規則 + `status` 判定的四條分支 |
| **正規化測試** | [07-domain-intel.md](07-domain-intel.md#72-正規化規則強制) 的每一列，含髒資料 |
| **拒絕規則測試** | 八種 `RejectionReason` 各至少一案例 |
| **過期計算測試** | 三步 `valid_until` 計算，含「來源未明示」與 `FILE_HASH` 兩個關鍵分支 |
| **STIX 映射測試** | 六種 `IocType` 的 pattern 模板、四種 hash 演算法對應、五個 TLP marking UUID 完全相符、產出以 STIX 2.1 JSON Schema 驗證 |
| Repository 測試 | `@DataJpaTest` + Testcontainers，驗證所有必要索引存在 |
| Service 測試 | application service 的交易邊界與事件發佈時機 |
| Controller 測試 | `@WebMvcTest`，驗證錯誤結構、分頁契約、標頭 |
| Adapter 測試 | 三個 mock adapter 的確定性（同樣輸入產生同樣輸出） |
| 韌性測試 | circuit breaker 開啟、retry 次數、單一來源失敗不影響其他來源 |
| **跨租戶隔離測試** | 見 14.4 |
| **TLP 過濾測試** | 見 14.4 |
| Bloom 測試 | 位元序、雙雜湊索引、delta 套用後 `resultingChecksum` 相符、`GREEN` 不進 public bloom |
| Migration 測試 | 從空資料庫執行全部 migration 成功（Testcontainers） |
| 日誌測試 | 見 14.4 第 8 條 |

---

## 14.3 覆蓋率門檻

| 模組／套件 | 行覆蓋率下限 |
|---|---|
| `ctip-core` 的 `com.ctip.domain..` | **85%** |
| `ctip-core` 的 `com.ctip.application..` | 75% |
| `ctip-sdk` | 70% |
| `ctip-adapters` | 70% |
| `ctip-app` | 60% |

以 JaCoCo `check` goal 強制，未達標 **CI fail**。

> `ctip-core` 是單一 Maven module 但有兩個門檻，因此 JaCoCo 規則需以 `<includes>` 依套件分別設定兩條 `<rule>`，而非依 module 設定。

**排除於覆蓋率計算**：`ctip-app` 的 `config/`（Spring 組態類）、MapStruct 產生的 `*MapperImpl`、`CtipApplication`。

---

## 14.4 安全測試（強制，共 9 條）

每一條都必須有對應的測試方法，且方法名須含條號以便追溯。

| # | 測試 |
|---|---|
| 1 | 匿名身分無法取得非 `CLEAR` TLP 的資料（含 `GREEN`） |
| 2 | 已登入身分可取得 public tenant 的 `CLEAR` + `GREEN`，但**不能**取得其他 tenant 的 `AMBER` |
| 3 | Tenant A 無法存取 Tenant B 的任何資源——**所有**端點皆回 `404`（非 403） |
| 4 | 過期 token 被拒 |
| 5 | 撤銷的 refresh token 被拒，且**重用會觸發該 family 全面撤銷** |
| 6 | 撤銷的 API key 被拒；API key 的 scope 無法超出建立者權限 |
| 7 | 超出限流回 `429`，且帶 `X-RateLimit-*` 與 `Retry-After` |
| 8 | 日誌中不出現密碼、JWT secret、API key 原文、refresh token 原文、`Authorization` 標頭值 |
| 9 | `INTERNAL_ONLY` 來源的資料**不出現在非擁有租戶的任何回應與任何 bloom 中**，但**擁有租戶看得到全貌** |

> 第 2、9 條為本版新增。第 2 條驗證 [07](07-domain-intel.md#tlp-可見度) 的 TLP 解耦決定；第 9 條驗證再散布過濾的作用域修正（若寫成「不出現在任何回應」，租戶會看不到自己提交的資料）。
>
> 第 3 條的「所有端點」必須以參數化測試涵蓋 [09-api.md](09-api.md#91-端點清單) 中每一個 tenant-scoped 端點——**不可只測幾個代表**。這是 retrofit 安全層最常見的漏網之處。

---

## 14.5 ArchUnit（11 條）

完整清單見 [01-architecture.md](01-architecture.md#19-archunit-規則強制共-11-條)。位於 `ctip-app/src/test/java/com/ctip/architecture/ArchitectureTest.java`（需在 `ctip-app` 執行才能跨模組掃描）。

標記 `@Tag("unit")`——ArchUnit 不需要 Spring context，應在 L1 執行以求快速回饋。

---

## 14.6 前端測試

見 [12-frontend.md](12-frontend.md#128-前端測試)。

覆蓋率門檻：`features/**` 與 `components/**` 行覆蓋率 ≥ 70%（Vitest coverage，`v8` provider）。
`src/api/generated/**` 排除。

> **E2E（Playwright）不在上表的 L1–L4 之內**（2026-08-28，Phase 16；
> [ADR 0025](../architecture/decisions/0025-phase16-sync-api-decisions.md)）：它跑在瀏覽器裡、
> 由 `frontend/playwright.config.ts` 而非 Maven profile 驅動，因此 `@Tag` 分層與 JaCoCo 門檻都不適用。
> Vitest 的 `test.include` 必須排除 `e2e/`（否則它會把 Playwright 的 spec 當成自己的測試而失敗）。
> 位置、API 邊界與對整套環境執行的方式見 [12 §12.8](12-frontend.md#128-前端測試)。

---

## 14.7 測試資料

### 樣本資料（`dev`/`mvp` profile）

- public system tenant
- 1 個範例 tenant（`slug = demo`）
- 1 個範例使用者（M2 起）
- 四個方案定義（M2 起）¹
- 4 個範例 threat source（`MANUAL` + 三個 mock）

> ¹ **實作回饋修訂（2026-08-28；[ADR 0016](../architecture/decisions/0016-phase1-13-spec-backfill.md)）**：
> `db/seed/sample_data.sql` 目前沒有方案／訂閱樣本——因為 `plans` 表要到 Phase 14 的 `V28` 才存在。
> **Phase 14 的交付物必須同時補上 seed**，否則 `SyncEndToEndTest`（Phase 16）與所有需要方案配額的
> 整合測試都沒有 fixture 可用。已寫進 `phases/phase-14.md`。
- 約 **1,000 筆範例 IOC**，涵蓋所有型別、`CLEAR`/`GREEN`/`AMBER`/`AMBER_STRICT` 四種 TLP、四種 status
- 若干 STIX 物件與關聯

**不得種入任何真實 secret。** 範例使用者密碼由環境變數指定，預設為明顯的測試值。

> ⚠️ 樣本資料**不得含 `TLP:RED`**——[07](07-domain-intel.md#red-的處理) 規定 `RED` 不進入平台，且須有一條測試驗證資料庫中無 `RED` 資料。

### 測試專用建構器

`ctip-core/src/test/java/com/ctip/testing/` 提供 `IndicatorTestBuilder` 等 fixture builder，**僅供測試使用**，不得出現在 main source。
時間一律以固定的 `FixedClockPort` 注入，**測試中不得出現 `Instant.now()`**。

---

*檔案結束。上次校對：2026-08-21。*
