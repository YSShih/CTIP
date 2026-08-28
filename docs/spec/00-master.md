# 00 — 主綱（Master）

> **CTIP Master Specification v2.0 — 2026-08-21**
>
> 本檔是規格書的**索引與強制契約摘要**。實質內容在各主題檔與 Phase 執行單中。
> 開始任何工作前，先讀本檔，再讀對應的 Phase 執行單。

---

## 0.1 這份規格的使用方式

```text
1. 讀 00-master.md（本檔）        了解強制契約與 Phase 順序
2. 讀你被指派的 phases/phase-NN.md  取得本階段的前置條件、交付物、完成判準
3. 依執行單指向的主題檔          取得詳細規則
4. 產生程式碼 → 編譯 → 測試      不得在未編譯驗證下產生數百個檔案
5. 執行該 Phase 的完成判準        全綠才 commit
6. 里程碑結束時執行 DoD Gate      ./environment/scripts/dod.sh <gate>
```

**不需要一次讀完全部檔案。** 這是 v2.0 拆檔的目的——v1.1 是單一 99KB 檔案，配上「產生程式碼前先完整讀完本規格」的要求，在多 session 執行下是最貴且最不可靠的一環。

---

## 0.2 檔案索引

| 檔案 | 內容 | 何時讀 |
|---|---|---|
| **00-master.md** | 本檔：強制契約、Phase 順序、執行規則 | 每次 |
| [01-architecture.md](01-architecture.md) | 分層、Maven module、抽象判準、ArchUnit、可讀性規則 | 每次 |
| [02-ddd-model.md](02-ddd-model.md) | 九個聚合、不變量、Ubiquitous Language、Domain Event | 涉及 domain 時 |
| [03-diagrams.md](03-diagrams.md) | 模組依賴圖、聚合圖、ERD、ingestion sequence、前端四圖 | 涉及結構時 |
| [04-data-dictionary.md](04-data-dictionary.md) | 27 張表完整 schema、列舉、TTL 規則、Flyway 對應 | 涉及持久化時 |
| [05-environment.md](05-environment.md) | compose、Dockerfile、env 變數、Spring 設定、腳本、hot reload | Phase 1–2 及部署 |
| [06-tech-stack.md](06-tech-stack.md) | 版本表與支援窗口、版本政策、linter、編譯地雷 | Phase 1 及升版時 |
| [07-domain-intel.md](07-domain-intel.md) | IOC 模型、正規化、拒絕規則、去重、合併、評分、TLP、STIX 映射 | Phase 4、6–8 |
| [08-ingestion-sdk.md](08-ingestion-sdk.md) | SDK 契約、pipeline stages、mock adapter、韌性、來源健康、排程 | Phase 5–6 |
| [09-api.md](09-api.md) | 端點清單、認證、cursor 分頁、錯誤碼、DTO、OpenAPI、寫入端點 | Phase 9–10、14 |
| [10-identity-plans.md](10-identity-plans.md) | 租戶隔離、RBAC、JWT、API key、方案配額、限流 | Phase 4、13–14、17 |
| [11-sync-bloom.md](11-sync-bloom.md) | 兩層 Bloom、位元格式、delta、client 契約 | Phase 15–16 |
| [12-frontend.md](12-frontend.md) | 結構、feature 依賴、狀態歸屬、型別產生、頁面、UI | Phase 11–12 |
| [13-platform-ops.md](13-platform-ops.md) | Kafka、通知、安全、隱私與保留、稽核、可觀測性、搜尋、CI/CD | Phase 12、19–23 |
| [14-testing.md](14-testing.md) | L1–L4 分層、覆蓋率、安全測試、測試資料 | 每次 |
| [15-dod-gates.md](15-dod-gates.md) | 三個可執行 DoD Gate（90 項） | 里程碑結束 |
| `phases/phase-NN.md` | 23 份執行單 | 你被指派的那一份 |
| `archive/v1.1-master-codex.md` | v1.1 原始單檔規格（僅供出處追溯，**不得依此開發**） | 不讀 |

---

## 0.3 強制契約（Coding LLM 不得自行變更）

| 契約 | 位置 |
|---|---|
| 儲存庫結構 | [05-environment.md §5.1](05-environment.md#51-儲存庫結構契約強制) |
| 單一 compose 檔 + 四 profile | [05-environment.md §5.2](05-environment.md#52-唯一的-compose-檔) |
| Dockerfile 契約（含 build context） | [05-environment.md §5.3](05-environment.md#53-dockerfile-契約) |
| 版本表與版本政策 | [06-tech-stack.md](06-tech-stack.md) |
| 分層依賴方向 + 9 條 ArchUnit 規則 | [01-architecture.md](01-architecture.md#19-archunit-規則強制共-9-條) |
| 27 張表 schema | [04-data-dictionary.md](04-data-dictionary.md) |
| 九個聚合的不變量 | [02-ddd-model.md](02-ddd-model.md#23-聚合不變量) |
| TLP 可見度（與方案解耦） | [07-domain-intel.md](07-domain-intel.md#tlp-可見度) |
| STIX marking UUID（五個固定值） | [07-domain-intel.md](07-domain-intel.md#784-tlp-20-marking-definition固定值不得自行產生) |
| Bloom 位元格式 | [11-sync-bloom.md](11-sync-bloom.md#114-位元陣列格式強制互通性關鍵) |
| Phase 順序 | 本檔 §0.5 |
| DoD Gates | [15-dod-gates.md](15-dod-gates.md) |

---

## 0.4 Coding LLM 執行規則

1. 開始任何 Phase 前，先完整讀完該 Phase 執行單與它指向的主題檔章節
2. 將本規格書視為 **single source of truth**
3. 維持儲存庫結構，**不得自行增減頂層目錄**
4. 遵守 Phase 順序，**一次只推進一個里程碑**
5. 未通過該里程碑的 DoD Gate，**不得**開始下一個里程碑
6. 遵守版本表；**不得自行升版任何 Maven／npm 相依**，發現過期只能回報
7. 不得建立任何環境專屬的 Compose 檔或 Dockerfile
8. 不得 commit secret
9. 不得將 JPA entity 直接暴露於 API
10. 不得在 controller 中放業務規則
11. 不得讓 domain 層依賴任何基礎設施套件
12. 不得在 `ctip-sdk` 中出現 Spring 相依
13. **不得將 Bloom 命中視為確定惡意**，亦不得將 Bloom miss 視為安全
14. 不得跳過資料庫 migration
15. 不得跳過測試；功能與測試同時產生
16. **不得留下假的 TODO 或 placeholder 實作**，也不得留下永不可達的 enum 值或欄位
17. 不得靜默移除規格中的需求；若某項無法實作，**必須明確回報**
18. 不得為了展示設計模式而加入抽象
19. 每個 Phase 結束必須執行 build、test 與該 Phase 的完成判準
20. 必須先修完編譯錯誤才能繼續
21. **不得使用 Lombok**（見 [06-tech-stack.md](06-tech-stack.md#631-不使用-lombok強制)）
22. 產生前端程式碼時**不得使用 class component**
23. domain 層**不得**直接呼叫 `Instant.now()` / `UUID.randomUUID()`（用 port 注入）
24. 命名一律依 [02-ddd-model.md](02-ddd-model.md#21-ubiquitous-language-詞彙表中英對照) 的詞彙表

### 遇到規格模糊時

依以下優先序選擇實作方式：

```text
1. 安全性
2. 可維護性 / 可讀性
3. 可測試性
4. 可擴充性
5. 向後相容
6. Clean Architecture 邊界
```

> v1.1 把「可擴充性」排在第一、「安全性」排在第三。本版調整為安全優先——這是一個處理威脅情資、有多租戶隔離與法遵限制的平台，把可擴充性放在安全之前會導致 AI 為了「未來可能的需求」而在安全邊界上開洞。

然後在 `docs/architecture/decisions/` 新增一則 ADR 記錄決策，並在**回報中明確指出**你做了這個決定。

---

## 0.5 Phase Plan（強制執行順序）

### M1 — MVP（可 demo 的最小可用產品）

| Phase | 內容 | 主要主題檔 |
|---|---|---|
| **1** | Repository skeleton（multi-module pom、.noop、.gitignore、linter 設定） | 05, 06, 01 |
| **2** | Environment + Docker（compose、Dockerfile、四份 .env、scripts） | 05 |
| **3** | Spring Boot 啟動 + PostgreSQL + Flyway（M1 的 9 張表）+ 種子資料 | 04, 05 |
| **4** | Domain：Indicator、Tenant、Source、TLP、值物件 **＋最小安全層** | 02, 07, 10, 01 |
| **5** | SDK + Mock Adapter + Resilience + Source Health | 08 |
| **6** | Ingestion pipeline + 資料品質 + 排程 **＋記憶體限流** | 08, 07, 10 |
| **7** | 去重、合併、指紋、評分 | 07 |
| **8** | STIX 正規化與匯出（`indicator`/`marking-definition`/`bundle`） | 07 |
| **9** | REST API + DTO/Mapper + 錯誤處理 + cursor 分頁 | 09 |
| **10** | OpenAPI / Swagger | 09 |
| **11** | React 前端骨架 + 型別產生 + 版面 | 12 |
| **12** | IOC Search / Detail / Dashboard 頁面 + PostgreSQL 搜尋 | 12, 13 |

→ **驗收 `dod.sh mvp`（38 項）。未通過不得進入 Phase 13。**

### M2 — Platform

| Phase | 內容 | 主要主題檔 |
|---|---|---|
| **13** | 認證、RBAC、API Key、租戶隔離強制 | 10 |
| **14** | Plan、Subscription、配額 **＋ IOC 寫入端點** | 10, 09, 08 |
| **15** | Bloom Filter（兩層、snapshot、delta） | 11 |
| **16** | 增量同步 API 與 client 契約 | 11 |
| **17** | Redis（快取 + 分散式限流） | 10 |
| **18** | Threat 實體與關聯 + M2 的 STIX 物件 | 02, 04, 07 |
| **19** | Elasticsearch 搜尋 + reconciliation + 降級 | 13 |

→ **驗收 `dod.sh phase2`（27 項）。**

### M3 — Production

| Phase | 內容 | 主要主題檔 |
|---|---|---|
| **20** | Kafka + 通知（WebSocket / SSE / Webhook） | 13 |
| **21** | Audit Log + 資料保留 | 13 |
| **22** | 監控、日誌、追蹤 | 13 |
| **23** | CI/CD 完整化、安全掃描、文件 | 13, 15 |

→ **驗收 `dod.sh full`（25 項）。**

**標註 `[M2]` 或 `[M3]` 的內容，在 M1 階段只需保留擴充點（介面／port），不得實作。**
特別是：不得為了「符合最終規格」而預先建立 Kafka / Elasticsearch / 通知相關程式碼。

---

## 0.6 相對 v1.1 的變更摘要

### 修正的建置阻斷缺陷（4 項）

| 缺陷 | 症狀 |
|---|---|
| Dockerfile `COPY` 路徑與 compose 的 `context: ..` 不符 | 兩個 image 都建不起來 |
| healthcheck 依賴 base image 不保證內含的 `curl`；Dockerfile 的 HEALTHCHECK 是 `java -version` | 容器永遠 unhealthy，`depends_on` 卡死 |
| `MAVEN_CACHE_SRC=maven-cache` 但頂層 `volumes:` 未宣告 | `docker compose config` 失敗 |
| compose 未傳入 `ENVIRONMENT` | 所有 prod 啟動守衛永不觸發 |

### 修正的版本錯誤（3 項）

| 項目 | v1.1 | v2.0 | 原因 |
|---|---|---|---|
| Elasticsearch | 9.3.x | **9.5.x** | 9.3 已於 2026-08-04 EOL |
| Nginx | 1.29-alpine | **1.30-alpine** | 1.29 是 mainline（奇數 minor）且已退役 |
| Kafka | 4.2.0 | **4.2.1** | 4.2.1 為現行 bugfix |

### 修正的規格衝突（10 項）

| # | 衝突 | 解決 |
|---|---|---|
| 1 | `IocType`/`Tlp`/`Severity` 在 §10.1 屬 core，但 §13.1 的 SDK 簽章用到它們——**編譯不過** | 下移至 `ctip-sdk`，明確定位為 Shared Kernel |
| 2 | §35.1「不要再包一層」字面上禁止 `application/port` 的 Repository，會把 spring-data 拖進 core | port 手寫於 core，Spring Data 在 infrastructure |
| 3 | `SearchPort` 回傳 Spring Data `Page`（需 COUNT query，且違反模組依賴） | 改為 core 自有的 `CursorPage` |
| 4 | §19.3 的 `valid_until`「任一為 null 則 null」使過期機制全面失效 | 三步 `COALESCE` 計算 |
| 5 | §19.2「不覆寫」與 `UNIQUE(indicator_id, source_id)` 矛盾 | 明確為「跨來源不覆寫」+ UPSERT + `report_count` |
| 6 | TLP 矩陣中 Free 與 Premium 完全相同；「Tenant 成員」列與 Free 列語意重疊 | TLP 與方案**完全解耦** |
| 7 | §25.1「附加 `tenant_id` 條件」（單數）使登入者看不到公開情資 | 過濾改為 `IN (current, public)` |
| 8 | `TLP:GREEN` 無棲息地（§24.2 限 public tenant 為 `CLEAR`） | public tenant 持有 `CLEAR` + `GREEN` |
| 9 | Premium 有 tenant bloom 容量卻無管道產生私有 IOC；`FALSE_POSITIVE` 永不可達 | 新增 IOC 寫入端點（Phase 14） |
| 10 | §16.1 要求 M1 支援 `malware`/`attack-pattern`，但 §18.3 說 M1 不實作 Threat | M1 STIX 範圍收斂至三種物件 |

### 補完的規格缺口

| 缺口 | v2.0 |
|---|---|
| 27 張表中 19 張無欄位定義 | [04-data-dictionary.md](04-data-dictionary.md) 全數補完（含新增 `threat_external_references`） |
| DDD 只有分層，無聚合／不變量／詞彙表／領域事件 | [02-ddd-model.md](02-ddd-model.md) |
| 無前後端關聯圖 | [03-diagrams.md](03-diagrams.md)，17 張 Mermaid 圖，逐圖標規範等級 |
| STIX 章節缺 pattern 語法與 TLP marking UUID | [07-domain-intel.md §7.8](07-domain-intel.md#78-stix-21-映射)，含查證過的五個官方 UUID |
| Bloom delta 格式不足以互通 | [11-sync-bloom.md §11.4](11-sync-bloom.md#114-位元陣列格式強制互通性關鍵) |
| §35.4 七條可讀性規則有五條無工具可執行 | Spotless + Checkstyle，綁進 `mvn verify` |
| 環境變數宣告與使用不對稱（6 個變數只出現單邊） | [05-environment.md](05-environment.md) 補齊 |
| 無 Spring 設定契約（env → property 對應） | [05-environment.md §5.7](05-environment.md#57-spring-設定對應本版新增) |
| Dashboard 頁面無對應端點 | 新增 `/api/v1/stats/*` |
| DoD 為散文，AI 自我評分必然通過 | [15-dod-gates.md](15-dod-gates.md)，90 項可執行 + 6 項明確標為需人工確認 |
| 三處錯誤交叉引用（§17.4→§34、§30.4→§34、§12.2→§51） | 全數修正 |
| 移除 Lombok | 連帶移除 annotation processor 順序陷阱 |
| `/sync/check` 與 `/iocs/lookup` 功能重複 | 移除前者 |

---

## 0.7 實作回饋修訂（2026-08-21，Phase 2–3 實測後回寫）

v2.0 初版在 Phase 2–3 實作時發現以下衝突／缺陷，**已全數修入對應主題檔**（規格維持 single source
of truth）；完整決策記錄見 `docs/architecture/decisions/0001-phase3-spec-conflict-resolutions.md`。

### 規格自身衝突（2 項）

| # | 衝突 | 解決 | 修訂處 |
|---|---|---|---|
| 1 | 表 8（M1 的 V7）定義 `fk_so_threat` 引用 M2 才建立的 `threats` | V7 保留 `threat_id` 欄位、FK 由 threats migration 以 `ALTER TABLE` 補上（版本號 2026-08-28 由 V25 改為 `V31`，見 §0.16） | [04 表 8、§4.7](04-data-dictionary.md) |
| 2 | mvp/dev 的 `BACKEND_JAVA_OPTS` 填 JDWP，但 `JAVA_TOOL_OPTIONS` 作用於 Maven 與 forked app 兩個 JVM，5005 雙綁必然啟動失敗 | JDWP 移至 spring-boot:run 的 `<jvmArguments>`；env 一律留空 | [05 §5.5 註 ¹、§5.6、§5.8.1](05-environment.md#581-實作回饋修正2026-08-21phase-23-實測發現詳見-adr-0001) |

### 照字面實作必失敗的缺陷（3 項，補列於 05 §5.8.1）

| # | 缺陷 | 解決 |
|---|---|---|
| 3 | postgres volume 掛 `…/data`，`postgres:18` 直接拒絕啟動 | 掛載點改 `/var/lib/postgresql` |
| 4 | dev 綁定掛載把 host（macOS）的 `node_modules` 帶進 Linux 容器，原生 binding 載入必失敗 | `NODE_MODULES_*` named volume 遮罩 + `up.sh` 首次 `npm ci` 預熱 |
| 5 | dev 容器離線跑 `mvnw -o`，但 maven-cache volume 初始為空 | `up.sh` 對 development target 增加預熱步驟（05 §5.10 第 5 步） |

### 版本／工具鏈地雷（1 組，補列於 06 §6.3.6）

Boot 4 模組化（缺 `spring-boot-flyway` 則 migration **靜默不執行**）、Testcontainers 2.x 座標與套件改名、
`spring.sql.init` 先於 Flyway 的順序、多 module 下 `-Dtest` 需 `failIfNoSpecifiedTests=false`。

### 規格新增（1 項）

public tenant 不變量 T2 增加 DB 層深度防禦觸發器 `trg_tenants_protect_public`（V2 建立；[04 表 1](04-data-dictionary.md)）。

---

## 0.8 實作回饋修訂（2026-08-25，Phase 5–6 實測後回寫）

Phase 5–6 實作時發現以下衝突／缺口，**已全數以引用區塊註記進對應主題檔**；完整決策記錄見
`docs/architecture/decisions/0003-phase5-sdk-adapter-decisions.md` 與 `0004-phase6-ingestion-pipeline-decisions.md`。

### 規格自身衝突（3 項）

| # | 衝突 | 解決 | 修訂處 |
|---|---|---|---|
| 1 | phase-06 要求「十個 stage」，但 `StixProjectionStage` 同時是 phase-08 的明列交付物；Phase 6 放空殼違反規則 16 | Phase 6 裝配 9 個 stage，Phase 8 插入 Stix（只改一個 `List.of`）；`ThreatScorer` 先行完成 | [08 §8.2](08-ingestion-sdk.md#82-攝取管線)、phase-06 註 ¹ |
| 2 | §8.2 的 pipeline bean 範例有 10 個參數，違反規格自身 checkstyle `ParameterNumber ≤ 5`（01 §1.8） | 單一 @Bean 方法內聯建構，順序仍以顯式 `List.of` 一處可見 | [08 §8.2](08-ingestion-sdk.md#82-攝取管線) |
| 3 | 結構契約把 `SourceSyncService` 放 core、`AdapterRegistry` 放 app，但 core ↛ app | core 新增 `AdapterRegistryPort`，AdapterRegistry 加 `implements`（§8.1 語意不變） | [08 §8.1](08-ingestion-sdk.md#81-plugin-sdk-契約ctip-sdk) |

### 照字面實作會出錯的缺口（4 項）

| # | 缺口 | 解決 | 修訂處 |
|---|---|---|---|
| 4 | `Indicator` 重建後 reputations 為空，直接合併使既有來源以中性值 50 計權（實測 confidence 55 vs 60） | 合併必須補入全部涉及來源的信譽（`mergeFrom` 三參數多載；MergeStage 查 `sources.reputation`） | [07 §7.5](07-domain-intel.md#75-多來源合併indicatormergepolicy) |
| 5 | `RawThreatRecord` 無撤回欄位，mock 要求「標為 RETRACTED」無管道 | 約定 STIX 風格 `rawPayload["revoked"] == true` → 來源記錄 RETRACTED | [08 §8.3](08-ingestion-sdk.md#83-必要的-mock-adapter-phase-5--m1) |
| 6 | `QUOTA_EXCEEDED` 在 M1 無 feed 觸發路徑，但八種 reason 是測試契約 | `BatchState.remainingQuota` 擴充點（feed 為 null）+ 單元測試覆蓋 | [07 §7.3](07-domain-intel.md#73-拒絕規則強制) |
| 7 | `source_sync`「append-only，不更新」與 `result` 預設 `RUNNING`、`finished_at` null 語意矛盾 | 開始建 RUNNING 列、結束回寫一次終態，之後不再更新 | [04 表 3](04-data-dictionary.md) |

### 版本／工具鏈（3 項）

| # | 項目 | 處置 | 修訂處 |
|---|---|---|---|
| 8 | 規格點名 IDNA2008，JDK 只有 IDNA2003（`java.net.IDN`），版本表無 ICU4J | 以 IDNA2003 實作並回報（差異僅 ß、ZWJ 等極少數字元） | [07 §7.2](07-domain-intel.md#72-正規化規則強制) |
| 9 | `RedisRateLimiter` 至 Phase 17 才存在，dev/staging/prod 樣板卻預設 `redis` | M1 暫以記憶體實作代替並 WARN（單一實例語意等價，限流仍生效） | [10 §10.7](10-identity-plans.md#107-限流) |
| 10 | Boot 4 又拆一個測試模組：`@AutoConfigureMockMvc` 不在 starter-test | 需另加 `spring-boot-webmvc-test`，套件改 `…webmvc.test.autoconfigure` | [06 §6.3.6](06-tech-stack.md) |

### 釐清（非衝突）

Retry「3 次」= 3 次重試（`maxAttempts = 4`；08 §8.5 註 ¹）；`SOURCE_SYNC_CRON` 是掃描節奏、
到期與否由各來源 `recommendedInterval` 決定（08 §8.7 註 ¹）；需 canonical 值的拒絕規則於
Normalize 後判定（08 §8.2、07 §7.3）；M1 限流僅匿名 IP 維度、`/actuator` 不套用、匿名數值以
property 預設承載至 Phase 14（10 §10.7）；mock 確定性以固定手寫資料集實作（08 §8.3）。

---

## 0.9 實作回饋修訂（2026-08-26，Phase 7–8 實測後回寫）

Phase 7 無規格偏離（既有實作經判準測試驗證即符合規格）。Phase 8 的修訂**已註記進對應主題檔**；
完整決策記錄見 `docs/architecture/decisions/0005-phase8-stix-projection-decisions.md`。

| # | 項目 | 解決 | 修訂處 |
|---|---|---|---|
| 1 | stage 8 在 Persist 前，但 `stix_objects` 的 FK 指向 `indicators`；且投影失敗不得使 ingestion 失敗 | stage 8 只建構投影，批次交易提交後逐筆寫出（失敗隔離） | [08 §8.2](08-ingestion-sdk.md#82-攝取管線) |
| 2 | STIX `created`/`modified` 對照的 DB 欄位在 stage 8 尚不存在 | 既有投影 created（UPSERT 保留）+ 當下時間為 modified 近似 | [07 §7.8.2](07-domain-intel.md#782-indicator-映射強制對照表) |
| 3 | OASIS schema 拒絕只有 `source_name` 的 external-reference | 附固定 `description`（M2 補 homepage 後改 `url`） | [07 §7.8.2](07-domain-intel.md#782-indicator-映射強制對照表) |
| 4 | Boot 4 的 Jackson 是 3.x（`tools.jackson.*`、unchecked 例外） | 編譯地雷清單補第 6 條 | [06 §6.3.6](06-tech-stack.md#636-spring-boot-4-模組化與-testcontainers-2x編譯地雷) |

其餘實作決策（marking 由常數供應不落 `stix_objects`、bundle 端點 M1 對匿名 403、匯出上限以
property 承載、STIX 2.1 JSON Schema 驗證器以 test scope 引入 networknt + vendored OASIS schema）
屬規格自由度內的選擇，僅記錄於 ADR 0005，未修改主題檔正文。

---

## 0.10 實作回饋修訂（2026-08-26，Phase 9 實測後回寫）

Phase 9 發現一項**安全性缺陷**並依 §0.4 優先序（安全性最先）修正，已註記進對應主題檔；
完整決策記錄見 `docs/architecture/decisions/0006-phase9-rest-api-decisions.md`（共八項，
其餘七項屬規格自由度內的實作選擇，僅記錄於 ADR）。

| # | 項目 | 解決 | 修訂處 |
|---|---|---|---|
| 1 | §7.9 作用域修正偽碼寫 `viewer == owner` 即豁免，但匿名綁定的 viewer 就是 public tenant、feed 情資的 owner 也是 public tenant——照字面實作，再散布過濾（規則 3/4/5）對公開輸出完全失效 | 豁免排除 public tenant（public 無成員，對 public 資料的存取一律是公開輸出）；domain I14、query 層、輸出層三處同一規則 | [07 §7.9](07-domain-intel.md#79-再散布政策法遵強制) |

---

## 0.11 實作回饋修訂（2026-08-26，Phase 10 實測後回寫）

完整決策記錄見 `docs/architecture/decisions/0007-phase10-openapi-decisions.md`。

| # | 項目 | 解決 | 修訂處 |
|---|---|---|---|
| 1 | `up.sh` 預熱守衛只認「volume 為空」，偵測不到 pom 相依漂移——後續 phase 新增相依後，離線 dev 容器必然啟動失敗 | 守衛改為離線 `dependency:go-offline` 探測，首次與相依變更後自動重新預熱 | [05 §5.10](05-environment.md#510-腳本契約) |

其餘 Phase 10 實作決策（`docs/api/openapi.json` 由 `OpenApiCompletenessTest` 產生——版本表無
springdoc maven plugin；破壞性比對用自寫 `openapi-breaking-check.py`——版本表無 oasdiff；
OpenAPI 註解集中於 `interfaces/rest/openapi/*Api` 文件介面由 controller 實作——checkstyle 300 行限制）
屬規格自由度內的選擇，僅記錄於 ADR 0007。

---

## 0.12 實作回饋修訂（2026-08-26，Phase 11–12 實測後回寫）

Phase 11 無規格正文偏離（版本表未列的必要配套相依與 shadcn 手寫等價等實作決策記錄於 ADR 0008）。
Phase 12 的修訂**已註記進對應主題檔**；完整決策記錄見
`docs/architecture/decisions/0009-phase12-search-pages-decisions.md`。

| # | 項目 | 解決 | 修訂處 |
|---|---|---|---|
| 1 | §13.7 的 `SearchPort` 簽章與 §1.11「可見度是查詢輸入」衝突；`IndicatorSummary` 無消費者 | 實作簽章 `searchByValue(term, filter, visibility, cursor, limit)` 定形（Phase 9 既成偏離補註記） | [13 §13.7](13-platform-ops.md#137-搜尋-phase-12--m1postgresqlphase-19--m2elasticsearch) |
| 2 | 排序能力與 keyset cursor 分頁互斥 | M1 固定 `lastSeen DESC, id DESC`，自由排序留待 M2 + ES | [13 §13.7](13-platform-ops.md#137-搜尋-phase-12--m1postgresqlphase-19--m2elasticsearch) |
| 3 | Hibernate 將 `String[]` 綁 `varchar[]`，`text[] @> varchar[]` 直接報錯 | 自訂 HQL 函式顯式 `cast(? as text[])`（`PostgresFunctionContributor`） | [13 §13.7](13-platform-ops.md#137-搜尋-phase-12--m1postgresqlphase-19--m2elasticsearch) |
| 4 | `CORS_ALLOWED_ORIGINS` 只有屬性與啟動守衛，無 MVC 接線——瀏覽器跨源全擋 | 新增 `WebCorsConfig`（`/api/**`、GET/POST、expose `X-RateLimit-*`） | [05 §5.7](05-environment.md#57-spring-設定對應本版新增) |
| 5 | `up.sh` frontend 預熱守衛只認 vite 存在，偵測不到 lockfile 漂移 | 改 `package-lock` 戳記比對（同 Phase 10 backend 守衛前例） | [05 §5.10](05-environment.md#510-腳本契約) |
| 6 | springdoc：record query 參數缺 `@ParameterObject`、List 回應誤用單物件 `@Schema`——文件與行為不符 | 補 `@ParameterObject` 與 `@ArraySchema`，重產 openapi.json（破壞性檢查 PASS） | [09 §9.6](09-api.md#96-openapi--swagger) |
| 7 | DevTools「classpath 變更即重啟」+ host/container 共享 target/classes：host 建置使容器 app 死於半寫入 classpath（閘門後根治，ADR 0010） | restart 改由 trigger file 觸發（`.devtools/.reloadtrigger` + reload.sh 編譯成功後 touch）；Boot 4 plugin `directories` 更名地雷回寫 06 | [05 §5.11](05-environment.md#511-hot-reload-契約本版修正)、[06 §6.3.6](06-tech-stack.md#636-spring-boot-4-模組化與-testcontainers-2x編譯地雷) |

---

## 0.13 實作回饋修訂（2026-08-27，M1 總複查後回寫）

M1 閘門後、Phase 13 前，以四個獨立視角（安全/TLP、攝取/合併、STIX/API、前端/環境）
對 Phase 1–12 全部產出重新複查。修正**已註記進對應主題檔**；完整決策與延後清單見
`docs/architecture/decisions/0011-m1-review-fixes.md`。

| # | 項目 | 解決 | 修訂處 |
|---|---|---|---|
| 1 | 同來源 UPSERT 無條件把來源記錄設回 `ACTIVE`——全量重同步/失敗重試會沖掉撤回，使 §7.5 規則 1 的 `REVOKED` 失效 | UPSERT status 規則定形：`RETRACTED` 一律生效且不被 `ACTIVE` 復活、`FALSE_POSITIVE` 不被例行同步清除、`EXPIRED` 因新觀測復活 | [07 §7.5](07-domain-intel.md#75-多來源合併indicatormergepolicy) |
| 2 | §7.7「RED 不進入平台」的 ingestion 拒收守門**實作缺漏**（只有 DB 無 RED 的狀態測試） | `ValidateStage` 補守門（`MALFORMED_VALUE`／"TLP:RED not accepted"）+ 行為測試（規格原文未變） | — |
| 3 | cleaned 通過驗證但 raw／normalized 超過 VARCHAR(2048)——JPA flush 期炸掉整批交易（含拒絕記錄） | raw、normalized 各補一道 `LENGTH_EXCEEDED` 守門 | — |
| 4 | cursor 內部編碼截到毫秒，微秒級 `last_seen` 會使 keyset 翻頁漏列（M2 真實 feed 必踩） | 內部格式改 `epochSecond.nano:id`（對外 ISO 格式不變） | — |
| 5 | 限流：minute 超限仍扣 day 配額；429 body 的 path 未跳脫；`/actuator` 豁免可被 `..` 前綴繞過 | 依 §10.7 順序短路、最小 JSON 跳脫、含 `..` 不豁免 | — |
| 6 | §7.8.4 註記誤稱 GREEN/AMBER/RED UUID 與 TLP 1.0 相同（表格值本身正確） | 註記改寫（五個 UUID 皆為 TLP 2.0 官方值） | [07 §7.8.4](07-domain-intel.md) |
| 7 | trend 統計時區錯位：`date_trunc` 依 session TimeZone 切日、Java 端以 UTC 對桶——非 UTC 環境會漏計（CI 測不到的時刻依賴 flake） | 改 `to_char(timezone('UTC', last_seen), 'YYYY-MM-DD')` 分組 | — |
| 8 | 其餘防禦性修正：CORS origins trim + mvp/dev 樣板補 127.0.0.1、空 bundle 省略 `objects`（OASIS minItems）、allowlist 項目正規化、前端 homepage scheme 白名單與 sources 錯誤呈現 | 見 ADR 0011 | — |

> ⚠️ **延後至 M2 的已知缺陷**（詳見 ADR 0011 延後表與 `docs/progress.md`）：staging/prod 前端
> `VITE_API_URL` 進不了 bundle（§5.6 規格層缺陷，M2-25 前必修）、`sourceId` 查詢側信道、
> `/stats/sources` 統計口徑、`MAX_PAGES_PER_RUN` 截斷後的 cursor/since 語意。

---

## 0.14 實作回饋修訂（2026-08-27，Phase 13 實測後回寫）

Phase 13（認證、RBAC、API Key、租戶隔離）發現兩項規格自身衝突，已註記進對應主題檔；
完整決策記錄（共 14 項，其餘為規格未定義而必須決定的實作選擇）見
`docs/architecture/decisions/0012-phase13-auth-rbac-decisions.md`。

### 規格自身衝突（2 項）

| # | 衝突 | 解決 | 修訂處 |
|---|---|---|---|
| 1 | §10.3 標題與 04 表 12 皆寫「權限 18 項」，但兩處列出的 code 實際有 **19 個** | 以清單為準種入 19 個（清單是矩陣與 `@PreAuthorize` 的實際依據）；計數改為 19 | [10 §10.3](10-identity-plans.md#103-使用者與-rbac-phase-13--m2)、[04 表 12、§4.7](04-data-dictionary.md) |
| 2 | key 格式 `ctip_<env>_<32 base62>` 的前 8 碼恆為環境常數，與 `key_prefix CHAR(8)` + `ux_api_keys_prefix` UNIQUE 直接衝突——同環境第二把 key 必撞，且 §10.5「以前綴定位單一列」永不成立 | 前綴改取**隨機段**前 8 碼（唯一自洽讀法，仍可公開顯示） | [10 §10.5](10-identity-plans.md#105-api-key-phase-13--m2)、[04 表 16](04-data-dictionary.md) |

### 規格未定義而補齊的契約（2 項寫入主題檔）

| # | 缺口 | 解決 | 修訂處 |
|---|---|---|---|
| 3 | phase-13 要求「`AuthState` 擴充為完整身分」，但同一執行單又禁止改動 TLP 過濾邏輯——而 `AuthState` 正是該邏輯的軸 | `AuthState` 保留兩態；完整身分改由 `AuthenticatedIdentity` 承載，`TlpSpecifications`／`Visibility` 零修改 | [01 §1.11](01-architecture.md#111-m1-最小安全層強制phase-4) |
| 4 | §9.1 的 `/auth/*` 與 `/api-keys` 只有路徑與權限，無 DTO、無匿名標註、無狀態碼 | `/auth/*` 標為匿名（refresh／logout 以主體 token 自我認證）；DTO 依 §9.5 慣例由實作定義，契約以 `docs/api/openapi.json` 為準；狀態碼明列 | [09 §9.1](09-api.md#91-端點清單) |

> ⚠️ **收尾複查發現的安全缺陷(4 項,詳見 ADR 0012 決策 16–19)**:認證失敗完全繞過限流
> (我在本 phase 引入的迴歸,實測 75 次無效 token 零個 429)、登入回應時間洩漏帳號是否存在
> (實測 440ms vs 7ms)、refresh token 輪替的消耗與持久化不同交易、API key 雜湊非常數時間比對。
> 其中限流順序已回寫 [10 §10.7](10-identity-plans.md#107-限流):**IP 維度必須在認證之前檢查**。

> ⚠️ **實作期發現的實質缺陷（非規格問題，但足以使不變量失效）**：
> `@Transactional` 方法內「寫入失敗紀錄 → 丟例外」會使該寫入隨交易 rollback——
> 登入失敗計數（U7 鎖定）與重用偵測的 family 全撤（U5）因此完全失效。
> 修正：失敗以回傳值交出，交易在協作者內提交，例外只在交易之外丟出（ADR 0012 決策 9）。
> 任何「失敗仍需留下副作用」的流程（Phase 14 的配額扣減亦同）都適用此規則。

> **依規則 17 回報**：`06 §6.2.2` 版本表**沒有列任何 JWT 函式庫**。本 phase 採 Spring Security
> 自帶的 `spring-security-oauth2-jose`（Nimbus，同由 Boot BOM 納管），未新增任何版本 property，
> 因此不觸犯規則 6；建議版本表補列一列「JWT — 隨 Spring Security（Nimbus JOSE+JWT）」。

---

## 0.15 實作回饋修訂（2026-08-28，Phase 13 收尾稽核後回寫）

使用者要求「逐端點對照 §10.3 矩陣稽核 + 資深架構師視角 + 資訊安全專家視角」的複查結果。
處置與取捨見 [ADR 0013](../architecture/decisions/0013-phase13-audit-fixes.md)。

| # | 發現 | 處置 | 回寫位置 |
|---|---|---|---|
| 1 | 判準只涵蓋「權限 × 角色」95 格；**「端點 → 需要哪個權限」這條軸完全沒有守門**，21 個 handler 只有 3 個路徑在該測試裡 | 新增 `EndpointAuthorizationTest` 逐 handler 檢查；§10.3 實作要求加一列 | [10 §10.3](10-identity-plans.md#103-使用者與-rbac-phase-13--m2) |
| 2 | `GET /sources`（×3）、`GET /stats`（×2）完全沒有 `@PreAuthorize`，而 filter chain 是 `anyRequest().permitAll()`——**scope 窄的 API key 可繞過**，§14.4 條號 6 在此失效 | 新增 `source:read`、`stats:read`（權限 19 → **21**，矩陣 95 → **105 格**），五個角色全持有，匿名行為不變；種子 `V27` | [10 §10.3](10-identity-plans.md#103-使用者與-rbac-phase-13--m2)、[04 表 12、§4.7](04-data-dictionary.md)、[09 §9.1](09-api.md#91-端點清單) |
| 3 | 04 表 12 的權限清單寫「共 19 項」但只列了 18 個（漏 `ioc:publish`） | 補回並更新計數 | [04 表 12](04-data-dictionary.md) |
| 4 | **停權與移除成員資格對既有憑證完全無效**：refresh 輪替與 API key 驗證都不看 `UserStatus`，成員資格查無時退回 `USER` 角色 | `AccountAccessPolicy` 為單一判定點，規則統一為 fail-closed | [10 §10.4](10-identity-plans.md#104-jwt-phase-13--m2) |
| 5 | refresh token family 無絕對存活上限——竊得一枚後每 30 天輪替一次即可**永久維持存取** | family 上限 90 天（`ctip.jwt.refresh-token-family-max-days`），逾期整組撤銷 | [10 §10.4](10-identity-plans.md#104-jwt-phase-13--m2) |
| 6 | 登入鎖定回 `Account temporarily locked`、密碼錯回 `Invalid credentials`，**可區分即可列舉帳號**（抵銷 §0.14 才修掉的時間側信道） | 統一訊息，鎖定只記伺服器端 | [10 §10.4](10-identity-plans.md#104-jwt-phase-13--m2) |
| 7 | 宣告的密碼政策 12–256 **字元**在 BCrypt 下不可實現（Spring Security 7 對 > 72 bytes 丟例外），80 字元的密碼會變成無說明的 400 | 上限改為 UTF-8 **72 bytes**，DTO 同步 | [10 §10.4](10-identity-plans.md#104-jwt-phase-13--m2) |
| 8 | `/api-keys` **沒有任何數量上限**，`countActive` 是無呼叫端的死程式（違反規則 16） | 比照 §0.8 匿名限流前例先以 `ctip.api-key.max-per-tenant`（預設 10）承載，Phase 14 移入 `plans` | [10 §10.5](10-identity-plans.md#105-api-key-phase-13--m2) |
| 9 | `last_used_at` 走整列覆寫的 `save`，併發時會把剛寫入的 `revoked_at` **沖回 null**（與 §0.13 的 `mergeReport` 沖掉撤回同一類） | port 新增 `markUsed`，`@Modifying` JPQL 只寫該欄 | [10 §10.5](10-identity-plans.md#105-api-key-phase-13--m2) |
| 10 | `Authorization: bearer <token>`（小寫）被靜默降級為匿名（RFC 7235 的 scheme 大小寫不敏感） | scheme 比對改大小寫不敏感；非 Bearer 回 401，不降級 | [09 §9.2](09-api.md#92-認證方式) |
| 11 | 註冊的 email／slug 唯一性都是 TOCTOU，併發時回 500 | `DataIntegrityViolationException` → `CONFLICT` | — |
| 12 | `ApiKeyCreateRequest.expiresAt` 無驗證，可建出「出生即死」的金鑰 | 加 `@FutureOrPresent` | [10 §10.5](10-identity-plans.md#105-api-key-phase-13--m2) |

> ✅ **查證後確認無漏洞**：JWT algorithm confusion（Nimbus 在 `JWSHeader.parse` 即拒絕 `alg:none`，
> `MACVerifier` 對非 HMAC 演算法丟例外）——已以否定案例測試釘住，因為那是相依函式庫的行為。
> 另：refresh/API key 的熵、常數時間比對、前端 token 只存記憶體、`ApiKeyController` 無 IDOR、
> `/auth/*` 確實受限流涵蓋，均查證通過。

> ⚠️ **給 Phase 14 的交叉檢查**:自助註冊即得 `TENANT_ADMIN`(ADR 0012 決策 5),而該角色持有
> `ioc:submit`／`ioc:import`／`webhook:manage`。**方案配額是唯一阻止「免費取得 PREMIUM 能力」的閘門**
> ——`plans.manual_submissions_per_day` 對 FREE 必須是 0 且必須真的被檢查,否則權限本身就足以提交。

---

## 0.16 實作回饋修訂（2026-08-28，Flyway 版本號）

Phase 13 收尾稽核留給 Phase 14 的地雷，使用者指示先處理掉。
處置與實測見 [ADR 0014](../architecture/decisions/0014-flyway-monotonic-versions.md)。

| # | 發現 | 處置 | 回寫位置 |
|---|---|---|---|
| 1 | §4.7 依「表的分組」預留版本號區段，但 Flyway **依版本號排序套用**——Phase 13 已用掉 `V24`/`V27`，Phase 14（`V22`）、Phase 15（`V26`）、Phase 18（`V25`）在既有資料庫上都會 `FlywayValidateException` 而**啟動失敗**（已實測） | **廢除區段預留**，版本號一律遞增、依實作順序指派：plans → `V28`/`V29`、bloom → `V30`、threats → `V31`、notifications → `V32`、audit_logs → `V33` | [04 §4.7](04-data-dictionary.md#47-flyway-migration-對應)、[05 §5.9](05-environment.md#59-flyway)、phase-14/15/18/20/21、[13](13-platform-ops.md) |
| 2 | 04 表 17 內文寫 `V22__seed_plans.sql`，§4.7 的表寫 `V23__seed_plans.sql`——同一份規格自相矛盾 | 兩處統一為 `V29__seed_plans.sql` | [04 表 17、§4.7](04-data-dictionary.md) |
| 3 | `migrate.sh` 呼叫 `flyway:migrate`，但專案**從未加入 flyway-maven-plugin**（Phase 2 就記了待辦）——實際執行以「No plugin found for prefix 'flyway'」失敗 | plugin 宣告在 parent pom（無 `<executions>`，不綁 lifecycle），`migrate.sh` 改用 `-N`；版本沿用 Boot 納管的 `${flyway.version}` / `${postgresql.version}`，不新增版本 property | [05 §5.10](05-environment.md#510-腳本契約) |

> ⚠️ **已套用的 migration 一律不動**（改動會使 checksum 失效）。副作用是
> `V7__create_stix.sql` 的註解仍寫 `V25__create_threats.sql`、`V20__create_users_and_rbac.sql`
> 的註解仍寫舊的區段規則——兩處**刻意保持過時**，以 §4.7 為準。
> `V8`–`V19`、`V22`、`V23`、`V25`、`V26` 這些號碼永遠不會有檔案。

> **依規則 17 回報**：`06 §6.2` 版本表沒有列 `flyway-maven-plugin`，
> 建議補列一列「Flyway Maven Plugin — 隨 Spring Boot BOM」。

---

## 0.17 實作回饋修訂（2026-08-28，先行清掉後續 phase 的已知缺口）

使用者指示把「之後的 phase 會遇到的問題」先修掉。處置與**刻意仍不修的理由**見
[ADR 0015](../architecture/decisions/0015-future-phase-hardening.md)。

| # | 發現 | 處置 | 回寫位置 |
|---|---|---|---|
| 1 | `/stats/sources` 的筆數不經可見度過濾——**Phase 14 手動提交上線後,租戶私有情資的提交量會即時出現在匿名可讀的公開統計裡** | `StatsPort.sources(Visibility)`，以 IndicatorEntity 為 root 再 join sources，重用同一套 `TlpSpecifications` | [09 §9.1](09-api.md#91-端點清單) |
| 2 | `sourceId` 查詢參數是還原被遮蔽來源歸屬的 oracle——輸出遮蔽 `DERIVED_ONLY` 的來源明細，查詢卻能用該來源過濾 | 查詢述詞套用與 `RedistributionFilter` 相同的揭露規則 | [07 §7.9](07-domain-intel.md#79-再散布政策法遵強制)、[13 §13.7](13-platform-ops.md) |
| 3 | `InMemoryRateLimiter` 的 bucket map 永不逐出（鍵含 client IP） | 超過 10,000 個且 10 分鐘節流後，只逐出「已回滿且閒置逾一日」者——**不放寬任何配額** | [10 §10.7](10-identity-plans.md#107-限流) |
| 4 | STIX `name` 截斷到 255 char 可能切斷 surrogate pair，產生無效 UTF-16 | 最後保留的 char 是高代理即退一格 | [07 §7.8.2](07-domain-intel.md) |
| 5 | filter 逸出的例外落到 Boot 預設 `/error`，**沒有 `code` 與 `traceId`**——§9.4 的統一錯誤契約在這條路徑上是破的 | `TraceIdFilter`（最外層）加錯誤網，以既有 `FilterErrorWriter` 寫出；回應已 committed 則原樣往上拋 | [09 §9.4](09-api.md) |
| 6 | 版本表沒有列三項**實作已在使用**的相依（規則 17 已回報四次） | 6.2.2 補列 JWT(Nimbus)、Flyway Maven Plugin、networknt json-schema-validator；**皆不新增版本 property**，不改變任何 pin | [06 §6.2.2](06-tech-stack.md#62-版本表) |

> ⚠️ **刻意仍不修的八項**（理由見 ADR 0015 末節）：`changePassword` 撤銷 family（M3 才有呼叫端，
> 現在做是推測性行為）、註冊的 email 枚舉（無寄信基礎設施）、租戶停權語意（§10 未定義，
> 猜一個實作下去比不做更糟）、自助註冊即得 `TENANT_ADMIN`（**方案配額才是正確的閘門**）、
> IDNA2008／ICU4J（需新增 runtime 相依，性質與上面補記錄不同）、
> `VITE_API_URL`（M2-25，兩種修法架構影響不同）、`MAX_PAGES_PER_RUN` 的 cursor/since 契約、
> FilterBar 的 back/forward 草稿同步。**前六項是規格層決策，需使用者定調。**

---

## 0.18 實作回饋修訂（2026-08-28，Phase 1–13 規格漏補）

盤點 Phase 14–23 阻斷項時，使用者指出「前面 phase 規格如果有漏掉的也要補」。
這一輪處理的是**已完成 phase 留下的缺口**——不是「以後會踩到」，是現在契約就已經不成立。
逐項與驗證見 [ADR 0016](../architecture/decisions/0016-phase1-13-spec-backfill.md)。

| # | 發現 | 處置 | 回寫位置 |
|---|---|---|---|
| 1 | **ArchUnit 規則 1 的 Jackson 防線是空的**——只擋 `com.fasterxml.jackson..`，而 Boot 4 是 `tools.jackson..`。Phase 8 發現後回寫了 §6.3.6，**沒回頭同步 Phase 4 建立的規則** | 規則 1 加上 `tools.jackson..`；以「加依賴 → 測試轉紅」驗證 | `ArchitectureTest` |
| 2 | **11 個環境變數在 compose、四份樣板、§5.4 三處皆未宣告**（Phase 6/8/9）。compose 的 backend 環境變數是明列白名單，漏列者寫進 `.env` **也到不了容器** | 三處補齊 + `SERVER_PORT`；**新增 `ConfigSymmetryTest` 自動檢查**（人工比對已守不住兩次） | [05 §5.4](05-environment.md#54-環境變數清單) |
| 3 | **`15 §15.5` 明文「P-02 的可自動化部分必須實作」從未實作**，且無任何 DoD 項目檢查它 | 新增 **ArchUnit 規則 10**：禁止 §2.1 詞彙表「常見誤用」欄的類別名 | [15 §15.5](15-dod-gates.md)、`ArchitectureTest` |
| 4 | `.github/workflows/` 只有 2 支；§13.8 標 **M1** 的 4 支 + 標 M2 的 2 支全部逾期，Phase 1–12 執行單皆未列，`dod.sh` 也不檢查 | 註記為逾期件，Phase 23 一次補齊 + 增設「11 支皆存在」的 DoD 檢查。內容已由本機判準涵蓋，是**自動化缺口而非品質缺口** | [13 §13.8](13-platform-ops.md#138-cicd-phase-23--m3基本流程自-m1-就要有)、phase-23 |
| 5 | `09` 宣稱端點數 47，實列 **43** | 改為 43 | [09](09-api.md) |
| 6 | §13.1 演進圖寫「M1–M2 程序內 listener」，但全庫 `@EventListener` **零命中** | 加註：發佈端已就位、消費端尚無需求是刻意的；Phase 20 是第一個消費者 | [13 §13.1](13-platform-ops.md) |
| 7 | `01` 的 `search/SearchService` 實際是 `indicator/IndicatorQueryService` | 加註實況；獨立 `search/` 待 Phase 19 | [01](01-architecture.md) |
| 8 | §6.1.2 規定 image 用 major 浮動 tag，但 compose 把 Kafka/ES/Prometheus/Grafana 釘死 patch | 明確化：浮動只適用資料面元件；那四個**維持精確 pin**（minor/patch 會改 API 與 dashboard schema） | [06 §6.1.2](06-tech-stack.md) |
| 9 | §15.3 自稱「25 項全部可執行」，但 M3-17 需要 gitignore 的 `.env.prod`、M3-19 需要未安裝的 `gh` | 註明兩項前置 | [15 §15.3](15-dod-gates.md) |
| 10 | `sample_data.sql` 無方案／訂閱樣本，而 §14.7 要求 | 加註原因並**寫進 phase-14 交付物**（Phase 16 的 `SyncEndToEndTest` 依賴它）；順修 phase-14 的「15 個配額維度」→ **14** | [14 §14.7](14-testing.md)、phase-14 |

> **為什麼現在才發現**:前十三個 phase 的收尾回寫，範圍一律是「本 phase 做了什麼、偏離了什麼」。
> 第 1、2、3 項都是**跨 phase 的**——Phase 8 發現 Jackson 3 卻沒回頭看 Phase 4 的規則；
> Phase 6/8/9 各加幾個變數卻沒人重驗對稱性；Phase 1 就該做的 ArchUnit 擴充寫在 `15` 而不在任何執行單裡。
> 逐 phase 回寫抓不到這種缺口，故第 2、3 項已改為**自動檢查**。

---

*主綱結束。規格版本 v2.0（含 2026-08-21、2026-08-25、2026-08-26、2026-08-27、2026-08-28 實作回饋修訂，見 §0.7–§0.18）。*
