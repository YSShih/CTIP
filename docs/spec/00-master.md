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
| 1 | 表 8（M1 的 V7）定義 `fk_so_threat` 引用 M2 才建立的 `threats` | V7 保留 `threat_id` 欄位、FK 由 V25 以 `ALTER TABLE` 補上 | [04 表 8、§4.7](04-data-dictionary.md) |
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

*主綱結束。規格版本 v2.0（含 2026-08-21、2026-08-25、2026-08-26 實作回饋修訂，見 §0.7–§0.9）。*
