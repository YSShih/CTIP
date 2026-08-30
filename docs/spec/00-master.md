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
| 分層依賴方向 + 11 條 ArchUnit 規則 | [01-architecture.md](01-architecture.md#19-archunit-規則強制共-11-條) |
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

## 0.19 實作回饋修訂（2026-08-28，Phase 14–23 前置清障）

使用者要求把後續 phase 會遇到的問題全部盤出來修掉。三組平行盤點 `phases/phase-14..23.md`
與其治理規格、對照已實作程式碼，共約 **90 項**落差。分七批處理，
逐項見 [ADR 0017](../architecture/decisions/0017-gate-credibility.md)–[0022](../architecture/decisions/0022-orphan-deliverables.md)。

| 批 | 主題 | 關鍵發現 |
|---|---|---|
| 1 | **閘門可信度** | `dod.sh` 對**不存在的測試類回報 PASS**——實測 Phase 14/15/16 一行程式都沒有時 `dod.sh phase2` 回報 27/27 全綠。另修 23 份執行單的 `./mvnw` 路徑（repo 根沒有這支）、過濾式判準改 `test`（`verify` 會綁 JaCoCo）、`MigrationIntegrationTest` 的兩個會在 Phase 14/15/18/20/21 各紅一次的封閉清單、RBAC 矩陣的**第三份來源（規格表）納入自動比對** |
| 2 | **版本表** | Phase 15–23 需要的相依**一項都沒列**（規則 6 會擋住每一個 phase 的第一天）。補 14 列 + `spring-boot-<tech>` autoconfig 座標表 + GitHub Action 版本政策 |
| 3 | **環境可啟動** | prometheus 掛空目錄→**容器啟動即退出**（實測），`up.sh staging` 必死→M2-25→M3-01 連鎖；grafana provisioning 空→M3-13 FAIL；kafka 無 healthcheck；ES 開了 security 卻沒傳密碼 |
| 4 | **Phase 14–16 定調** | 配額超限**三處三種答案**、`ioc:publish` 照字面實作**不產生任何公開效果**、誤判回報在資料模型上**必撞唯一約束**、匯入 job **無表可存**、Bloom `hashFunctionCount` 範例與公式**不一致**且雙雜湊**溢位語意未定義**、`CTIP_PLAN_*` **到不了容器也綁不上屬性** |
| 5 | **Phase 17–19 定調** | 限流維度歸屬三處各說各話、`endpointClass` 配額值從未定義、H4 的唯一約束在 `NULL` 時**完全不生效**、H6 是跨聚合不變量與 §2.2 互斥、M2 五種 STIX SDO **無任何欄位對照**、ES index mapping 漏欄位會**整套繞過可見度過濾** |
| 6 | **Phase 20–23 定調 + 稽核角色模型**（唯一含實作） | webhook 簽章對象兩句互斥、W3 事件名 02 自身矛盾、**secret 只存雜湊卻要算 HMAC（數學上不可能）**、WebSocket/SSE **完全沒有端點定義**卻要用 Playwright 測、保留任務六個只定義四個、prod 沒暴露 `prometheus`。**實作**：應用改以非特權角色 `ctip_app` 連線 |
| 7 | **無主交付物歸位** | Playwright、六個前端頁、改密碼端點、`docs/api/events/`、`dod.sh` 的 workflow 存在性檢查——規格要求、DoD 會檢查，卻**沒有任何 phase 負責交付** |

> **批 6 的實作為什麼非做不可**:`POSTGRES_USER` 是 postgres image 的**初始 superuser**
> (實測 `rolsuper = t`),而 superuser **繞過所有 GRANT/REVOKE**——實測 `REVOKE` 之後
> `DELETE` 照樣成功。Phase 21 的 `REVOKE UPDATE, DELETE ON audit_logs` 因此完全無效,
> **M3-09「必須由 DB 拒絕」永遠不可能通過**。現在改只影響一個測試基底類;
> 拖到 Phase 21,全部整合測試都已建立在 superuser 連線上。
>
> 切換後 `IngestionEndToEndTest` 立刻以 `permission denied for schema public` 失敗
> ——那是權限模型真的生效的證據。**選擇改測試而不是放寬權限**,否則就失去
> 「測試與正式環境同權限」的意義。

---

## 0.20 實作回饋修訂（2026-08-28，Phase 14）

方案／配額與 IOC 寫入端點的實作回饋，逐項見
[ADR 0023](../architecture/decisions/0023-phase14-plans-and-write-endpoints.md)。

| # | 發現 | 處置 | 影響檔案 |
|---|---|---|---|
| 1 | 配額值 `0`（停用）若照 §9.7 回 `429 + Retry-After`，等於告訴 client「等一下再試就會過」，而那永遠不會發生 | 同一欄位依值分流：`0` → **403 PLAN_LIMIT_EXCEEDED**、正整數在視窗內用罄 → **429** | [09 §9.7](09-api.md#97-寫入端點細節-m2) |
| 2 | 只做 [ADR 0019](../architecture/decisions/0019-phase14-16-spec-resolutions.md) 的擁有權轉移，**發布出去的 IOC 仍然沒有任何人看得到**（I14：全來源 `INTERNAL_ONLY` 不得對非擁有租戶輸出，而擁有租戶豁免刻意不適用於 public tenant） | 發布時該筆 MANUAL 來源記錄記為 `PUBLIC_REDISTRIBUTABLE`——「發布」本身就是租戶對再散布的授權 | [09 §9.7](09-api.md#97-寫入端點細節-m2) |
| 3 | 匯入若不計入每日提交配額，每日上限可被「改用匯入端點」完全繞過 | 匯入的每一筆都扣減 `max_manual_submissions_per_day`，越界者逐筆 `QUOTA_EXCEEDED` | [10 §10.6](10-identity-plans.md#106-方案) |
| 4 | ENTERPRISE 的 `requests_per_day` 為 `null`，而 §10.7 要求標頭出現在所有回應 | `X-RateLimit-Limit`／`-Remaining` 以字面值 `unlimited` 表達無上限 | [10 §10.7](10-identity-plans.md#107-限流) |
| 5 | 「放寬三處配額型別」的那三處在改讀 `plans` 表後**完全沒有呼叫端**，留著就是第二個真相來源 | 直接移除，連同五個環境變數（compose、五份樣板、§5.4 同步）；`API_DEFAULT_PAGE_SIZE` 保留（不是配額） | [05 §5.4](05-environment.md) |
| 6 | `indicator_sources.raw_payload` 自 M1 就存在、有 GC 索引，卻**從未被寫入**；而 §9.7 的 `note`／`reason` 沒有其他欄位可放 | 改為真的寫入（只寫不讀；新快照無內容時不覆寫既有值） | [04 表 5](04-data-dictionary.md) |
| 7 | §8.3 要求 adapter 從 `FetchContext.config` 取批次，但 STIX bundle 需要 JSON 解析器，而 `ctip-adapters`／`ctip-core` 都沒有 JSON 相依 | 新增 port `ImportPayloadParserPort`：CSV 走 adapter（§8.3 字面實作）、bundle 在 ctip-app 解；兩者之後走**同一條 pipeline** | [08 §8.3](08-ingestion-sdk.md) |

---

## 0.21 實作回饋修訂（2026-08-28，Phase 15）

兩層 Bloom（snapshot / delta）的實作回饋，逐項見
[ADR 0024](../architecture/decisions/0024-phase15-bloom-decisions.md)。

| # | 發現 | 處置 | 影響檔案 |
|---|---|---|---|
| 1 | `Indicator.eligibleForBloom()` 內含再散布條件，而 §11.2 的 tenant 成員條件**沒有**——沿用會使 tenant bloom **恆為空**（手動提交固定 `INTERNAL_ONLY`）。[ADR 0019](../architecture/decisions/0019-phase14-16-spec-resolutions.md)「沒有動的」那一項在此定調 | 兩個述詞收在 `domain/bloom/BloomMembership`；資料庫端另有等價 SQL，`BloomCoverageTest` 逐筆比對防漂移 | [11 §11.2](11-sync-bloom.md#112-兩層架構)、[02](02-ddd-model.md#bloomversion) |
| 2 | `plans.tenant_bloom_capacity` 的 `NULL` 在 §11.2 是「**無** tenant Bloom」，與 `QuotaLimit` 通用語意的「無限制」相反 | 以 §11.2 為準、fail-closed：只有正整數才產生 tenant bloom | [11 §11.2](11-sync-bloom.md#112-兩層架構) |
| 3 | 容量若照字面只用方案值，`BLOOM_TENANT_DEFAULT_CAPACITY` 會變成沒有呼叫端的死設定（規則 16），且 ENTERPRISE 的小租戶每小時產生 18MB 陣列 | 方案值＝權利上限、環境變數＝實際尺寸預設：`min(上限, max(預設, 成員數))` | [11 §11.2](11-sync-bloom.md#112-兩層架構) |
| 4 | 04 表 23 與 §11.5 對 delta 的 `checksum` 說法相反（位元陣列 vs addedBits payload） | 定調為「未壓縮 **artifact payload**」的 SHA-256；因此 varint 差分編碼屬 Phase 15、base64url 屬 Phase 16 | [04 表 23](04-data-dictionary.md)、[11 §11.5](11-sync-bloom.md#115-metadata-與-api) |
| 5 | 「保留最近 N 份」照字面會先刪掉 full snapshot（同 dataset 內它的 `bloomVersion` 最小＝最舊），使其 delta 鏈永遠無法重建 | 刪除前排除「該 dataset 仍有存活版本」的 full snapshot | [11 §11.3](11-sync-bloom.md#113-bloom-無法刪除元素) |
| 6 | `requiresFullSnapshot` 的兩個參數表達不了可設定的 `BLOOM_MAX_DELTA_CHAIN`，而 domain 不得讀設定 | 多接一個 `BloomChainPolicy` 值物件；只允許對 full snapshot 呼叫 | [02](02-ddd-model.md#bloomversion) |
| 7 | `ConfigSymmetryTest` 是單向檢查，抓不到「compose 與 §5.4 有、`application.yml` 沒綁」的反向缺漏——`BLOOM_STORAGE_DIR` / `BLOOM_COMPRESSION` 即為此 | 補上綁定，並在 §5.4 註明兩個方向都要確認 | [05 §5.4](05-environment.md#54-環境變數清單) |

> 其餘實作決策（位元運算放 domain 而非 infrastructure、`BloomUpdateStage` 只作為
> 「哪個 scope 變了」的訊號而非成員來源、delta 以 `last_seen` 為水位並往回退一分鐘、
> 成員掃描另立 `BloomMemberPort` 以投影 + keyset 分頁進行）屬規格自由度內的選擇，
> 僅記錄於 ADR 0024。

---

## 0.22 實作回饋修訂（2026-08-28，Phase 16）

增量同步 API 與 client 契約的實作回饋，逐項見
[ADR 0025](../architecture/decisions/0025-phase16-sync-api-decisions.md)。

| # | 發現 | 處置 | 影響檔案 |
|---|---|---|---|
| 1 | `min_sync_interval_seconds` 的值(86400/21600/300/60)在 `RateLimitKey.Window` 表達不了,且沒有任何欄位記錄「上次同步時間」([ADR 0019](../architecture/decisions/0019-phase14-16-spec-resolutions.md) 已列此缺口) | 新增 `SyncThrottlePort`(M2 記憶體、Phase 17 起 Redis),記帳對象是**呼叫者身分**而非 tenant——匿名綁 public tenant,以 tenant 記帳會讓全體匿名 client 共用一個額度 | [11 §11.6](11-sync-bloom.md#116-client-同步流程) |
| 2 | manifest 的 `checksum` 若取「最新版本 artifact 的 checksum」,最新版本是 delta 時算的是 varint payload 的雜湊,client 拿它驗自己的陣列**永遠不會相符** | 定調為「完全同步後陣列應有的 SHA-256」(`BloomVersion.arrayChecksum()`);`sizeBytes` 取未壓縮陣列長度(與 §11.5 範例的 17,971,985 一致) | [11 §11.5](11-sync-bloom.md#115-metadata-與-api) |
| 3 | §11.6 第 4 步「更新版本」沒說更新成哪個數字。照 manifest 記,client 的陣列會少掉 delta 的位元卻自認最新——**Bloom 的 false negative 不可接受** | `/sync/bloom` 回應必帶 `X-Bloom-*` 七個標頭(含這份 artifact 自己的版號與 checksum),client 一律據此更新;CORS 的 `exposedHeaders` 一併補上 | [11 §11.5](11-sync-bloom.md#115-metadata-與-api)、[09 §9.1](09-api.md#91-端點清單) |
| 4 | `409 SNAPSHOT_REQUIRED` 若也消耗同步間隔,client 依 §11.6 轉去下載 full 時會立刻撞 `429`,**復原路徑永遠走不完** | 節流只在「已確定回 200」之後才記帳;`/sync/manifest` 完全不節流(它是流程第 1 步) | [11 §11.5](11-sync-bloom.md#115-metadata-與-api)、[11 §11.6](11-sync-bloom.md#116-client-同步流程) |
| 5 | 「302 至簽章下載 URL」沒有簽章金鑰的環境變數(ADR 0019 已列) | 採直接串流;不新增設定項(規則 18) | [11 §11.5](11-sync-bloom.md#115-metadata-與-api) |
| 6 | 匿名對 `scope=TENANT` 的語意未定義(ADR 0019 已列) | 回 `403 PLAN_LIMIT_EXCEEDED`;判定點 `BloomScopePlanner.hasTenantBloom` **與生成端共用**,避免「manifest 說有、下載回 403」 | [11 §11.5](11-sync-bloom.md#115-metadata-與-api) |
| 7 | **M2-15 是假綠**:判準跑 `BloomDeltaTest`(生成端),而 `409` 的 HTTP 行為在 Phase 15 不存在 | 判準改指向 `SyncEndToEndTest`(真的產生 25 段 delta);`dod.sh` 同步 | [15 §15.2](15-dod-gates.md) |
| 8 | Playwright 只出現在版本表與 DoD,**位置與執行方式沒有規格**([ADR 0022](../architecture/decisions/0022-orphan-deliverables.md) 只指定了「歸 Phase 16」) | 補 `frontend/e2e/` + `playwright.config.ts` 的契約、API 邊界(`page.route`)、`E2E_BASE_URL` 的整套環境模式;M2-26 的四個情境全數交付 | [12 §12.8](12-frontend.md#128-前端測試)、[14 §14.6](14-testing.md#146-前端測試) |
| 9 | `OpenApiCompletenessTest` 的 2xx 檢查寫死 `application/json`,會逼 `GET /sync/bloom`(octet-stream)假裝自己回 JSON | 改為「任一 2xx 有非空 `content`」——§9.6 要求的是記載回應內容,不是 JSON | (測試) |
| 10 | `bloom_artifacts.download_count`(04 表 23)自 Phase 15 就存在卻**從未被寫入**,而下載端點是它唯一可能的呼叫端(規則 16 的永不可達欄位) | 下載成功後以**定向 UPDATE** +1(整列覆寫會與排程生成互相沖掉);連帶發現 `download()` 不得宣告 `readOnly` 交易 | [04 表 23](04-data-dictionary.md) |

> 其餘實作決策(`ClientIp` 抽到 `infrastructure/web` 讓限流與同步節流共用同一份 IPv6 `/64` 收斂、
> delta 區間以併集後重新編碼、`SyncService` 只讀不觸發生成)屬規格自由度內的選擇,僅記於 ADR 0025。

---

## 0.23 實作回饋修訂（2026-08-29，Phase 17）

Redis(快取 + 分散式限流)的實作回饋,逐項見
[ADR 0026](../architecture/decisions/0026-phase17-redis-cache-and-distributed-rate-limit.md)。

| # | 發現 | 處置 | 影響檔案 |
|---|---|---|---|
| 1 | **維度 5 的鍵沒有主體**:§10.7 寫的是 `ratelimit:{scope}:{endpointClass}:{window}`,照字面實作是**全平台共用一個桶**——任一租戶(或任一匿名 IP)打滿它,所有人都會被 429 | 鍵改為 `ratelimit:{scope}:{subject}:{endpointClass}:{window}`;主體沿用當下最 specific 的維度。ADR 0020「分類上限恆低於總上限」本來就只有 per-subject 才成立 | [10 §10.7](10-identity-plans.md#107-限流) |
| 2 | **維度 4 把已認證的呼叫者綁死在匿名配額**:限流必須排在認證之前(ADR 0012 決策 16),但那時還不知道會不會認證成功,於是 ENTERPRISE 的 client 也只有 60/min——方案分級形同虛設 | `RateLimiterPort` 新增 `refund`;認證成功後歸還維度 4 的 token 並重寫 `X-RateLimit-*`。對已認證流量,維度 4 變成「同一 IP 同時進行中的請求數」上限;認證失敗者沒有歸還機會,暴力破解仍被擋 | [10 §10.7](10-identity-plans.md#107-限流) |
| 3 | 「單一 filter」與「維度 4 必須在認證前、維度 1–3 必須在認證後」看似互斥 | 兩個檢查點,共用同一個 `RateLimitResponder` 與同一份豁免規則——不是兩份各自演化的邏輯 | [10 §10.7](10-identity-plans.md#107-限流) |
| 4 | `POST /iocs/search`／`/lookup` 照 HTTP 方法字面屬 write,會把前端唯一的搜尋路徑壓到總配額的 20% | §10.7 的 read 明文含「查詢」;以 POST 表達的查詢歸 read | [10 §10.7](10-identity-plans.md#107-限流) |
| 5 | bucket4j 把桶的設定一併存進 Redis,建立後不隨呼叫端的限額更新——**方案降級時 fail-open** | Redis 鍵多帶一段容量(限額改變即換桶),與記憶體實作同語意;僅影響 Redis 內部 | [10 §10.7](10-identity-plans.md#107-限流) |
| 6 | Redis 不可用時該怎麼辦,規格沒說 | 限流 **fail-fast**(連不上即啟動失敗,不得降級記憶體);快取 **fail-soft**(記 WARN 並重新載入) | [10 §10.7](10-identity-plans.md#107-限流)、`docs/deployment/rate-limiting.md` |
| 7 | `server.forward-headers-strategy=framework` 的 Boot 內建 filter **無條件採信** `X-Forwarded-*`,只要應用有一條路徑能被直連,IP 維度即可被偽造繞過 | 以同型別 bean 取代,只信任 `TRUSTED_PROXIES`(新增環境變數)之內的對端;預設空 = fail-closed;非 mvp 環境為空時 WARN | [10 §10.7](10-identity-plans.md#107-限流)、[05 §5.4](05-environment.md#54-環境變數清單) |
| 8 | `CachePort` 若沒有真實呼叫端就是規則 16 禁止的裝飾品 | 消費者是兩個**既有的行程內快取**(`PlanRepositoryAdapter`、`RolePermissionRepositoryAdapter`,後者註解明寫「分散式快取為 Phase 17」)。行程內的 map 無法跨實例失效,那正是要修的缺陷。訂閱仍不快取(降級必須立即生效) | — |
| 9 | ArchUnit 規則 1 只擋 domain,而 **port 定義在 application 層**——真正會洩漏 Lettuce 型別的地方是那裡 | 新增規則 11;順帶補回規則 10(ADR 0016 加了實作卻沒回寫 §1.9 的表與計數) | [01 §1.9](01-architecture.md#19-archunit-規則強制共-11-條)、[15 §15.1](15-dod-gates.md) |
| 10 | `spring-boot-data-redis` 一在 classpath 上,actuator 就加 redis 健康檢查——而 mvp 的 compose 不啟動 redis,`/actuator/health` 會永遠 DOWN、容器永遠 unhealthy | 只在 `application-mvp.yml` 關閉該健康檢查 | `application-mvp.yml` |
| 11 | `TestRestTemplate` 在 Boot 4 被拆到版本表未列的 `spring-boot-restclient-test`(同 MockMvc 的前例) | 測試改用 JDK `HttpClient`,不新增相依;地雷清單補第 9、10 條 | [06 §6.3.6](06-tech-stack.md#636-spring-boot-4-模組化與-testcontainers-2x編譯地雷) |
| 12 | `docs/deployment/` 的「真實 client IP 限制」被 [ADR 0022](../architecture/decisions/0022-orphan-deliverables.md) 判為無 phase 承接而排到 Phase 23,但 `phase-17.md` 的「不得做的事」本來就明文要求 | 本 phase 交付 `docs/deployment/rate-limiting.md`;phase-23 的清單同步 | [phase-23](phases/phase-23.md) |
| 13 | **判準自己量錯對象**:`SpringApplicationBuilder.properties(...)` 是優先序最低的 defaultProperties,`server.port=0` 被 `application.yml` 的 `${SERVER_PORT:8080}` 蓋掉——第二個實例綁固定 8080,而 `dod.sh mvp` 的 M1-38 正好在 mvp 容器佔用 8080 時執行,請求打到的是容器裡的另一個 app | 改用命令列參數(優先序高於 yml)+ 兩道啟動守衛(埠不為 0 且與實例 1 不同、後端真的是 redis) | (測試) |

> **值得記住的行為變更**:維度 5 讓**匿名的 write 上限變成 12/min**(60 的 20%)。
> `AuthHardeningTest` 因此在第 13 個請求上回 429——那是正確行為,測試改為每個方法一個 client IP,
> 不是放寬配額。

---

## 0.24 實作回饋修訂（2026-08-29，Phase 18）

Threat 實體與關聯 + M2 的 STIX 物件,逐項見
[ADR 0027](../architecture/decisions/0027-phase18-threat-and-m2-stix.md)。

| # | 發現 | 處置 | 影響檔案 |
|---|---|---|---|
| 1 | **平台沒有任何建立 Threat 的管道**:§9.1 只有三個 `GET`、ingestion 不產生 Threat、Phase 19–23 也沒有——三張表、聚合的四個行為、三種 STIX 投影、`threat:read` 全部永遠不可達(規則 16 的 placeholder,整個 phase 規模) | 補五個寫入端點與 `threat:manage` 權限(第 23 個,`ADMIN_UP`);歸屬與 TLP 完全沿用 §9.7 手動提交的規則(預設 AMBER;CLEAR/GREEN 需 `ioc:publish` 且轉為 public tenant)。與 v2.0 為 IOC 補寫入端點同源 | [09 §9.1](09-api.md#91-端點清單)、[10 §10.3](10-identity-plans.md) |
| 2 | 只做 `retire()` 會讓 `ThreatStatus.DORMANT` **永遠不可達** | 端點為 `PUT /{id}/status`,domain 補 `changeStatus`;`retire()` 委派到終態。`RETIRED` 為終態,設定成它已經是的狀態回 409 | [02 §2.3](02-ddd-model.md#threat)、[09 §9.1](09-api.md#91-端點清單) |
| 3 | **`/threats` 三端點的可見度述詞未定義**(執行單明列) | 定調為 §7.7 的通則;與 Indicator 的差異只有「沒有軟刪除」與「沒有再散布維度」。`GET /{id}/indicators` 必須對每個關聯 IOC 再走一次 Indicator 的可見度——關聯不是可見度的旁路 | [07 §7.7](07-domain-intel.md#threats-的可見度) |
| 4 | ADR 0020 要求的 `IndicatorTlpTightened` 事件在 §2.4 沒有位置 | 補進事件清單(欄位 indicatorId／tenantId／previousTlp／currentTlp),由 `Indicator.recompute()` 在 TLP 真的變嚴格時發佈 | [02 §2.4](02-ddd-model.md#24-domain-event-清單) |
| 5 | **AFTER_COMMIT 的消費端用預設傳播行為寫資料庫,寫入不落庫也不報錯**(實測:malware 與 relationship 一列都沒有,連例外都沒有)——回呼仍在已提交交易的 synchronization 範圍內 | 消費端一律 `REQUIRES_NEW`;規則寫進 §2.4(對 M3 的 Kafka listener 同樣成立) | [02 §2.4](02-ddd-model.md#24-domain-event-清單) |
| 6 | M2 的五種 STIX 物件沒有欄位對照(ADR 0020 第 7 節指定由本 phase 補) | 新增 §7.8.7:`malware`／`attack-pattern`／`observed-data`／`identity`／`relationship` 五張對照表;§7.8.6 的引用同步為「7.8.2–7.8.4、7.8.7」 | [07 §7.8.7](07-domain-intel.md#787-m2-的四種-sdo-與-relationship強制對照表) |
| 7 | `stix_objects` **沒有 `threat_id` 索引**,而 `fk_so_threat` 帶 `ON DELETE CASCADE`(執行單明列) | `V31` 一併建立 `ix_so_threat`;表 8 的索引清單同步 | [04 表 8](04-data-dictionary.md) |
| 8 | 權限種子若另開 `V34`,Phase 20 的 `V32` 在既有資料庫上就成 out-of-order(ADR 0014 修過的坑) | `threat:manage` 的冪等種子寫在 `V31` 內;§4.7 註明理由 | [04 §4.7](04-data-dictionary.md#47-flyway-migration-對應) |

> **值得記住的行為**:H6 是單向的——把私有 IOC 關聯到公開威脅會把該威脅收緊到公開範圍之外,
> 解除關聯也不會放寬回去。這是 H6 的必然結果,寫入端點的文件必須明說。

---

## 0.25 實作回饋修訂（2026-08-29，Phase 19）

Elasticsearch 搜尋、降級與 reconciliation,逐項見
[ADR 0028](../architecture/decisions/0028-phase19-elasticsearch-search.md)。

| # | 發現 | 處置 | 影響檔案 |
|---|---|---|---|
| 1 | **`X-Search-Backend` 沒有傳遞通道**:`SearchPort` 回 `CursorPage<Indicator>`,而 §13.7 又禁止在 controller 判斷降級(ADR 0020 §8) | `SearchPort.search(SearchQuery) → SearchResult(page, backend)`;輸入一併包成 record(多一個 `fuzzy` 會使簽章超過 checkstyle 的 `ParameterNumber ≤ 5`) | [13 §13.7](13-platform-ops.md#137-搜尋-phase-12--m1postgresqlphase-19--m2elasticsearch) |
| 2 | ⚠️ **§13.7 的搜尋欄位清單不含 `ownerTenantId`、`deletedAt`、來源的再散布政策**——照字面實作,ES 路徑會整套繞過可見度與側信道防護(ADR 0015、ADR 0020 §8) | 索引另帶 `ownerTenantId`／`redistributable`／`disclosableSourceIds`,軟刪除不進索引;**且回傳的 Indicator 一律以 `findVisibleByIds` 從 PostgreSQL 取回**(兩層防護,各以測試反向驗證) | [13 §13.7](13-platform-ops.md#137-搜尋-phase-12--m1postgresqlphase-19--m2elasticsearch) |
| 3 | 索引名、mapping、查詢形狀、模糊查詢的 API 契約、對帳演算法皆未定義 | 全部定案並寫回 §13.7:`ctip-indicators`／`dynamic: strict`／`wildcard` 子字串(語意等同 M1 的 `LIKE`)／`fuzzy` 旗標／歸併對帳只在共同涵蓋區間內判定 | [13 §13.7](13-platform-ops.md#137-搜尋-phase-12--m1postgresqlphase-19--m2elasticsearch)、[09 §9.1](09-api.md#91-端點清單) |
| 4 | §13.7「一致性」表格的三條規則因引用區塊插在表頭與內容列之間而**沒有 render 成表格** | 修訂移到表格之後;三條規則內容未變,一律為強制 | [13 §13.7](13-platform-ops.md#137-搜尋-phase-12--m1postgresqlphase-19--m2elasticsearch) |
| 5 | reconciliation 排程沒有環境變數(執行單明列);`08 §8.7` 其實已命名 `ES_RECONCILE_CRON`,但 §5.4.5、compose 與樣板都沒有 | 補 `ES_RECONCILE_CRON` 與 `SEARCH_BACKEND`(compose + §5.4 + 五份樣板;`ConfigSymmetryTest` 強制對稱) | [05 §5.4](05-environment.md#54-環境變數清單)、[05 §5.6](05-environment.md#56-compose-骨架) |
| 6 | **`spring-boot-elasticsearch` 一在 classpath 上就會加 actuator 的 ES 健康檢查**——ES 只屬 `full` profile,mvp 與 dev 都沒有它,不關掉容器永遠 unhealthy、`dod.sh mvp` 整批紅(同 Phase 17 的 Redis,但 Redis 屬 `standard,full` 故當時只需關 mvp) | `application-mvp.yml` 與 `application-dev.yml` 皆關;`06 §6.3.6` 補第 11 條(含 Testcontainers ES 座標與 image 對應) | [06 §6.3.6](06-tech-stack.md#636-spring-boot-4-模組化與-testcontainers-2x編譯地雷) |
| 7 | `ELASTICSEARCH_URL` 為空時,ES 路徑會變成「每次查詢先逾時再降級」的靜默錯誤——降級會把它蓋成看起來正常的 200,沒有人會發現 ES 從來沒被用過 | 守衛放在**設定層**:`ConfigSymmetryTest` 斷言 compose 對「有 autoconfig 綁在上面的變數」不得給空字串預設值。放 `StartupValidator` 是錯的——autoconfig 在 context refresh 期間就先失敗(見第 14 項),bean 形式的檢查永遠不會觸發,那會是一段不可達的程式碼(規則 16) | [13 §13.7](13-platform-ops.md#137-搜尋-phase-12--m1postgresqlphase-19--m2elasticsearch)、[05 §5.6](05-environment.md#56-compose-骨架) |
| 8 | phase-19 的「ES 型別不得洩漏到 application 層」與規則 11(Phase 17 為 Redis 建立)是同一條規則 | **擴充規則 11 的套件清單而非新增規則 12**,維持 §0.3 的「11 條」契約;一併擋 Resilience4j | [01 §1.9](01-architecture.md#19-archunit-規則強制共-11-條) |
| 9 | **`15 §15.2` 的 M2-22 是空轉通過的**:它是 DoD 全表唯一用 `verify` 的過濾式判準,違反 §15.0 自訂的規則,並因此繞過 `dod.sh` 的 `mvn_test` 存在性守衛(ADR 0017)——測試類不存在時 surefire 跑 0 個測試、build 成功、該項 `[PASS]` | 與 M2-23／M2-24 統一為 `mvn_test`;規格與 `dod.sh` 同步 | [15 §15.2](15-dod-gates.md#152-dod-phase2phase-1319) |
| 10 | `06 §6.5` 要求的 `docs/deployment/licensing.md` 從 M1 起就不存在(M3-23 的 12 份文件之一) | 本 phase 補上(§6.5 是 phase-19 的治理規格之一,ES → OpenSearch 的替換步驟正屬此處) | [06 §6.5](06-tech-stack.md#65-授權注意事項) |
| 11 | **compose 的 `backend`／`frontend` 沒有 `image:` 鍵,兩個 build target 共用同一個 image 名稱**——`docker compose up` 只在 image 不存在時才建置,先跑過 mvp(development)再跑 staging(production)會沿用 development 的 image,兩個容器 crash-loop | 兩個服務加 `image: ${PROJECT_NAME:-ctip}-<service>:${*_BUILD_TARGET:-production}`;補列於 §5.8.2。M2-25 是 DoD 中唯一會切換 build target 的項目,先前 phase 只跑 `--only` 子集故未浮現 | [05 §5.6](05-environment.md#56-compose-骨架)、[05 §5.8.2](05-environment.md#582-image-tag-與-build-target) |
| 12 | **frontend 的 `HEALTHCHECK` 用 `http://localhost/`**——容器內 `localhost` 只解析到 `::1`,而 nginx 的 `listen 80;` 只綁 IPv4,busybox 的 `wget` 不回退 → production 的 frontend 永遠 `unhealthy`(用 `curl` 手動驗證看不出來,它會回退) | 改為 `http://127.0.0.1/`;補列於 §5.8.2。同樣只有 M2-25 會實際執行 production stage | [05 §5.3](05-environment.md#53-dockerfile-契約)、[05 §5.8.2](05-environment.md#582-image-tag-與-build-target) |

| 13 | **全新的 ES 叢集在 05:00 的對帳之前索引是空的,而搜尋照樣回 200 並宣稱 `X-Search-Backend: elasticsearch`**——比降級更糟,降級至少會說出來(實跑 staging 才發現) | `SearchIndexBootstrap`:啟動後檢查「索引空而資料庫非空」,成立才在背景補建一次;正常重啟不付出代價 | [13 §13.7](13-platform-ops.md#137-搜尋-phase-12--m1postgresqlphase-19--m2elasticsearch) |

| 14 | **compose 對 `ELASTICSEARCH_URL` 用空字串預設值**,而 Boot 的 ES autoconfig 對空 `uris` 直接丟 `hosts must not be null nor empty`——加入 `spring-boot-elasticsearch` 之後,mvp/dev 的 backend **完全無法啟動**(即使 `SEARCH_BACKEND=postgres`、根本用不到那個 client) | 預設值改為 `http://elasticsearch:9200`;補列於 §5.8.2。與 §6.3.6 第 1 條互為對照:該條是「autoconfig 不在 classpath 上,屬性靜默失效」,這裡是「autoconfig 在 classpath 上,空屬性直接讓應用死掉」 | [05 §5.6](05-environment.md#56-compose-骨架)、[05 §5.8.2](05-environment.md#582-image-tag-與-build-target) |

| 15 | **`up.sh` 切換環境時不收掉上一個 profile 的服務**——四個環境共用同一個 compose 專案名、服務差異只靠 profile,先 staging(`full`)再 mvp 會留下五個容器,`M1-14`「只有三個容器」因此不可能通過:**`dod.sh phase2` 跑完一次就再也重跑不了**(M2-25 把環境留在 staging)。⚠️ `--remove-orphans` **解決不了**(compose 刻意不把 profile 停用的服務當 orphan) | `up.sh` 第 6 步以 `ps --services` 減 `config --services` 算差集並 `rm -sfv`;§5.10 與 §5.8.2 同步 | [05 §5.10](05-environment.md#510-腳本契約)、[05 §5.8.2](05-environment.md#582-image-tag-與-build-target) |

| 16 | **兩個 gate 並行執行會產生「看似有結論」的假分數**:共用 `backend/*/target`,一邊 `clean` 就抽掉另一邊的 classes(症狀是 `cannot find symbol`,完全不像併發問題),容器又互搶記憶體(Testcontainers `Timed out waiting for log output`)。本 phase 因此白丟一輪 26/27 | `dod.sh` 加互斥鎖(以 pid 記錄、殘鎖自動接手;巢狀呼叫以環境變數傳遞持有權);規則寫進 §15.0 | [15 §15.0](15-dod-gates.md#150-執行方式)、[05 §5.10](05-environment.md#510-腳本契約) |
| 17 | **以 log 內容判斷 gate 是否結束必然誤判**:`M2-01` 巢狀執行整個 `dod.sh mvp`,它的 `=== 結果` 行會先出現在同一份 log 裡——外層還在跑就被當成結束(這就是第 16 項的起因) | 結果行改帶 gate 名稱 `=== 結果(<gate>):N/M 通過 ===`;§15.0 明訂**判斷完成一律用行程結束/退出碼** | [15 §15.0](15-dod-gates.md#150-執行方式) |
| 18 | **`up.sh` 逾時只說「未就緒」**,而 crash-loop 的容器在 `ps` 裡看起來只是「一直在 restart」——本 phase 連續三次靠人工 `docker logs` 才找到原因(資料庫憑證、空的 ES uris、image 用錯 target) | 失敗時印出未就緒服務的日誌尾段,並以 `diagnose_startup_failure` 把三種症狀翻譯成可執行的修法;§5.10 第 7 步同步 | [05 §5.10](05-environment.md#510-腳本契約) |
| 19 | 四個環境共用同一個 compose 專案與具名 volume 的兩個後果(`POSTGRES_*` 必須一致、切換環境要收掉上一個 profile 的服務)散落在各處,沒有寫在最該看到的地方 | 收進 §5.5 的開頭 | [05 §5.5](05-environment.md#55-四種-profile-差異表) |

> **未實作並回報(規則 17)**:§13.7 修訂 3 的「自由排序留待 M2 與 Elasticsearch 一併設計」**不在 Phase 19 交付**
> ——每種排序鍵需要一套 cursor 編碼,而降級可以發生在翻頁的任何一頁、兩邊的 cursor 必須可以互換,
> 兩者直接衝突。排序維持固定 `lastSeen DESC, id DESC`。Threat 的搜尋同樣不在執行單交付物內。

---

## 0.26 實作回饋修訂（2026-08-29，Phase 20）

Kafka、事件 schema、站內通知、WebSocket／SSE 與 webhook 送達,逐項見
[ADR 0029](../architecture/decisions/0029-phase20-kafka-and-notifications.md)。

| # | 發現 | 處置 | 影響檔案 |
|---|---|---|---|
| 1 | ⚠️ **`WebhookFilter` 要的四個過濾維度不在 domain event 上**:§3.2.9 寫 `matches(DomainEvent)`,但 §2.4 的事件沒有 severity / tags / sourceIds(它們是合併之後才定的),而 §13.1 明文「不修改任何發佈端」——照字面實作,過濾條件永遠比對到空集合 | 新增值物件 `NotificationEvent`(domain event 的通知形狀投影),由 application 層在送出前從聚合補齊;`matches` / `accepts` 改收它。不變量 W5 完全保留 | [02 §2.3](02-ddd-model.md#webhook)、[03 §3.2.9](03-diagrams.md#329-webhook-聚合-m3) |
| 2 | **七種通知型別容不下三個事件**:§2.4 給 `SourceRecovered`、`IngestionFailed`、`IndicatorMerged` 的消費者都含 Notification(M3),而 §13.2 的型別清單是封閉的七項 | 前兩者映射到 `SOURCE_FAILURE`(來源健康頻道,severity 各異)、後者到 `NEW_IOC`;不新增第八種型別。對照表寫入 `docs/api/events/README.md`,並由 `KafkaTopicsTest` 與 §2.4 三方綁定 | [13 §13.2](13-platform-ops.md#132-通知-phase-20--m3) |
| 3 | **`@TransactionalEventListener(AFTER_COMMIT)` 是多餘的**:`SpringEventPublisherAdapter` 自 Phase 6 起就已在 `afterCommit` 回呼裡才發佈信封 | 轉發 listener 一律 `@EventListener`(同 Phase 18 的 `ThreatConsistencyListener`);§13.1 的演進圖加註 | [13 §13.1](13-platform-ops.md#131-事件與-kafka-phase-20--m3) |
| 3b | **02 §2.4 的「REQUIRED 會使寫入不落庫也不報錯」在本專案沒有重現**(實測:改回 `REQUIRED`,`EventIdempotencyTest` 仍通過——連線歸還時會一併提交) | 規則照留(`REQUIRES_NEW` 讓寫入有明確的提交邊界),但敘述改為不宣稱那個症狀。**反過來確有一件事必須在交易內做**:聚合發出的 `WebhookDisabled` 若在交易外發佈,會掛到已走完 afterCommit 的交易上,永遠不觸發 | [02 §2.4](02-ddd-model.md#24-domain-event-清單) |
| 4 | ⚠️ **§13.1 規則 7 照字面實作仍會癱瘓業務路徑**:`KafkaTemplate.send()` 取不到 metadata 時同步阻塞到 `max.block.ms`(預設 60 秒),broker 掛掉時每個事件都讓請求多等一分鐘——回 200 但等一分鐘與失敗沒有差別 | 轉發移出業務執行緒(單執行緒 + 有界佇列,滿了丟棄並記錄);producer 的 `max.block.ms` 收到 5 秒。規則 7 補上「不得阻塞業務執行緒」 | [13 §13.1](13-platform-ops.md#131-事件與-kafka-phase-20--m3) |
| 5 | **`KafkaAdmin` 看不見 `List<NewTopic>` 型別的 bean**——topic 只能靠 broker auto-create 產生(分割數變成預設值),關閉 auto-create 的正式環境則直接沒有 topic | 改用 `KafkaAdmin.NewTopics`;`06 §6.3.6` 補第 12 條。`KafkaEventTest` 一併斷言**分割數**——只驗「topic 存在」會被 auto-create 蒙混過去,這個缺陷就是被那條斷言抓到的 | [06 §6.3.6](06-tech-stack.md#636-spring-boot-4-模組化與-testcontainers-2x編譯地雷) |
| 6 | **SSE fallback 沒有方案閘門**:§9.1 只在 WebSocket 那一列寫 `websocket_enabled`,任何 client 改連 `/events` 就繞過方案限制 | 兩者共用同一個閘門(它們是同一個能力的兩種傳輸);§9.1 的表格同步 | [09 §9.1](09-api.md#91-端點清單) |
| 6b | 握手的子協定只定義了 client 送什麼,沒說伺服器回什麼——原樣回送等於把 token 寫進反向代理與瀏覽器的 log | client 同時提供 `ctip.auth` 與 `ctip.auth.<jwt>`,伺服器選前者;§9.1 補列 | [09 §9.1](09-api.md#91-端點清單) |
| 7 | **表 25 沒有 payload 欄位,而重試在數分鐘後才發生**——各自重新組裝會讓 body 漂移,而 body 是簽章的一部分,接收端第二次驗簽必失敗 | 送達 body 定義為 `notifications` 那一列的**純函數**(欄位順序寫死);§13.2 補列 | [13 §13.2](13-platform-ops.md#132-通知-phase-20--m3) |
| 7b | **W3 與 W4 的計數對象未定義**:`consecutiveFailures` 若計「嘗試」,一個用盡五次嘗試的事件就會立刻觸發 W3,W3 便完全等同於 W4 | 定調計「事件」——連續五個事件用盡重試才停用;§2.3 的 W3 補註 | [02 §2.3](02-ddd-model.md#webhook) |
| 8 | **`04` 的權限清單漏掉 `threat:manage`**:Phase 18 寫進了 §10.3 與 `V31` 種子,但沒有同步 `04`,兩份清單差一項 | 補回,並一併新增 `notification:read`(ADR 0021 第 5 節指派給本 phase);合計 24 項,矩陣 120 格 | [04 §4.3](04-data-dictionary.md)、[10 §10.3](10-identity-plans.md#103-使用者與-rbac-phase-13--m2) |
| 9 | `12 §12.5` 的頁面表沒有 webhook 管理頁,而 `09` 有三個 `/webhooks` 端點與 `webhook:manage`(ADR 0022 的孤兒交付物) | 補 `/settings/webhooks`(需登入 + `webhook:manage`) | [12 §12.5](12-frontend.md#125-頁面) |
| 10 | `13 §13.2` 要求 timestamp 偏差規則「必須寫入 `docs/api/`」,ADR 0022 把它排到 Phase 23——但本 phase 就已經有接收端需要它 | 提前交付 `docs/api/webhooks.md`(五個標頭、驗簽三步驟、重試與停用、過濾語意) | `docs/api/webhooks.md` |
| 11 | **測試 context 的連線池會撞上 `max_connections`**:context 是快取的,每個都有自己的 10 條池;Phase 20 新增五個 context 之後整批爆掉。症狀 `FATAL: remaining connection slots are reserved` 出現在**後面**才載入的 context 上,看起來完全像那個測試自己的問題 | `AbstractPostgresIntegrationTest` 把池上限固定為 4;`DistributedRateLimitTest` 的第二個實例同步 | [14 §14.1](14-testing.md) |
| 12 | `KAFKA_BOOTSTRAP_SERVERS` 的 compose 預設值是空字串——與 Phase 19 的 `ELASTICSEARCH_URL` 同一個型態(Boot 的 Kafka autoconfig 對空 bootstrap-servers 丟 `ConfigException`) | 預設值改為 `kafka:9092`,並把它納入 `ConfigSymmetryTest` 的「不得空字串預設值」清單——不必等它再壞一次 mvp | [05 §5.4](05-environment.md#54-環境變數清單) |
| 13 | ⚠️ **`up.sh` 從來不重建 image**:Phase 19 給兩個 build target 不同的 `image:` tag 之後,`docker compose up` 只在 image **不存在**時才建置——tag 一旦存在,之後每次 `up` 都沿用它。staging 因此跑的是上一次建置的 jar,而八個服務全 healthy、log 沒有任何異常。本 phase 第一次實跑時六個 topic 一個都沒建立,查了環境變數與條件裝配才在 `/app/app.jar` 的時間戳上找到原因 | `up.sh` 第 6 步改為 `up -d --build --remove-orphans`;原始碼沒變時 layer cache 幾乎不花時間 | [05 §5.10](05-environment.md#510-腳本契約)、[05 §5.8.3](05-environment.md#583-image-重建與-sse-標頭) |
| 14 | **`SseEmitter` 的回應標頭要等到第一次寫入才 flush**——client 與中間的反向代理在第一則通知抵達之前不知道連線已建立;`curl -N /api/v1/events` 會一直等到逾時,看起來像端點壞掉 | 建立連線後立刻送一行 `:keepalive` 註解 | [09 §9.1](09-api.md#91-端點清單)、[05 §5.8.3](05-environment.md#583-image-重建與-sse-標頭) |

> **未實作並回報(規則 17)**:
> ① §13.2 提到的 **FCM / APNs adapter** 不在本 phase 交付;擴充點是 `RealtimePushPort`。
> ② 即時推送的連線登記簿在**記憶體**,前提與 08 §8.7 的排程相同(M1–M3 單一實例);
> 多實例需要共用的 pub/sub。
> ③ `ctip.notification.events.v1` 以外的五個 topic **目前沒有消費端**——它們是對外的事件串流契約
> (schema 已定版),Phase 21 的稽核消費端會讀 `ctip.audit.events.v1`。

---

## 0.27 實作回饋修訂（2026-08-29，Phase 1–20 總複查）

跨 phase 的複查(邏輯、規格與實作一致性、資安、程式弱點),逐項見
[ADR 0030](../architecture/decisions/0030-phase1-20-review-security-fixes.md)。
五項全部已修復並附迴歸測試。

| # | 發現 | 處置 | 影響檔案 |
|---|---|---|---|
| 1 | ⚠️ **CORS `allowedMethods` 漏了 PUT 與 PATCH**:清單停在 `GET/POST/DELETE`,但 Phase 18 加了兩支 `PUT`(威脅關聯、威脅狀態)、Phase 20 加了 `PATCH /notifications/{id}/read`。前端是獨立來源的 SPA(nginx 不 proxy `/api`),這三支端點的 preflight 一律 403,**在瀏覽器端完全打不通**;`MockMvc` 不走 preflight,所以測試全綠 | 清單補為 `GET, POST, PUT, PATCH, DELETE`;`CorsPreflightTest` 改為對 `PATCH` 端點實際發 preflight。§5.7 加註「新增端點時同步這份清單是硬性步驟」 | [05 §5.7](05-environment.md#57-spring-設定對應本版新增) |
| 2 | ⚠️ **Webhook 送達是未設防的 SSRF 入口**:W1 只要求 `https://`,而送達是「伺服器主動對租戶指定的 URL 發 POST」。任何持 `webhook:manage` 的租戶都能存進 `https://169.254.169.254/…`(雲端 metadata)或 `https://10.0.0.5:8080/admin`;本文雖被丟棄,`webhook_deliveries` 仍記下狀態碼與延遲 | 兩道防線:建立時對**字串**判定(`WebhookTarget`,domain 純運算)、每次送達前對**解析後位址**判定(`WebhookTargetGuard`,擋主機名指向內網與 DNS rebinding)。範圍共用同一組判定。`reconstitute` 刻意只驗 scheme——一列舊資料不得讓整個租戶的扇出停擺 | [02 §2.3](02-ddd-model.md#webhook)、[13 §13.2](13-platform-ops.md#132-通知-phase-20--m3) |
| 3 | ⚠️ **登入鎖定期滿後 `failed_login_count` 不歸零 → 帳號可被永久鎖定**:計數一旦到 10 就永遠是 10,鎖定一過期,任何一次失敗都立刻再鎖 15 分鐘。攻擊者每 15 分鐘一個錯密碼即可讓受害帳號永久登不進來。U7 說的是「**連續**失敗 10 次」,原實作是「一生失敗 10 次」 | 記錄本次失敗之前先檢查上一段鎖定是否已過期,過期即歸零重新起算;§10.4 補明 | [10 §10.4](10-identity-plans.md#104-jwt-phase-13--m2) |
| 4 | **匯入端點的請求本文沒有容器層上限**:`@RequestBody byte[]` 先把整包讀進記憶體,64 MB 檢查在那之後才跑;Tomcat 對非表單本文沒有預設上限。持 `ioc:import` 的帳號送數 GB 本文即可耗盡堆積 | 新增排在 security chain 之前的 `RequestBodySizeLimitFilter`:有 `Content-Length` 的看標頭回 413,**chunked 的由包裝過的 input stream 在讀滿上限時中止**(只檢查標頭等於沒擋)。端點層檢查保留為兜底,兩處共用同一常數 | [09 §9.7](09-api.md#97-寫入端點細節-m2) |
| 5 | **限流的端點分類可被路徑編碼繞過**:分類拿 `getRequestURI()` 原文比對,而 routing 拿的是解碼後、去路徑參數的段落。`/api/v1/iocs/%69mport` 照樣打到 import handler,上限卻從 `heavy` 的 5% 變成 `write` 的 20% | 比對前正規化(逐段去路徑參數、逐段百分比解碼、去尾斜線);解碼出來的 `/` 不得成為段落分隔符,壞掉的百分比序列不得拋例外(那條路徑未認證即可觸發) | [10 §10.7](10-identity-plans.md#107-限流) |

> **檢查過但未發現問題**(記下來,免得下一輪重查):JWT 簽發與驗證(無 alg confusion)、
> API key 常數時間比對、refresh token 輪替與重用偵測、RBAC 矩陣與端點授權宣告
> (24 個 authority 全有種子、無端點漏標 `@PreAuthorize`)、TLP 與再散布可見度的兩份實作、
> SQL 全部參數化、`LIKE` 與 ES wildcard 跳脫、Bloom 位元序與 delta 編碼、cursor 精度、
> Kafka 轉發的非阻塞與有界佇列、前端(無 `dangerouslySetInnerHTML`,token 只在記憶體)。
>
> **一個排除掉的疑似缺陷**:`UserTest` 的外層 `@Test` 在 surefire 報表顯示 `Tests run: 0`。
> 實測(故意讓其中一個失敗)證明它們**有**執行,只是被歸到第一個 `@Nested` 類別的報表裡。
> 那是報表歸屬問題,不是覆蓋率缺口。

---

## 0.28 實作回饋修訂（2026-08-30，Phase 21 實測後回寫）

Phase 21（Audit Log + 資料保留）的修訂**已全數註記進對應主題檔**;
完整決策記錄見 [ADR 0031](../architecture/decisions/0031-phase21-audit-and-retention.md)（12 節）。

### 照字面實作會失敗或永不可達（4 項）

| # | 項目 | 解決 | 修訂處 |
|---|---|---|---|
| 1 | §13.5 規則 2「清理角色無 SELECT 業務表之權限」——**PostgreSQL 對 `DELETE/UPDATE … WHERE` 仍要求 WHERE 欄位的 SELECT 權限**,六項清理全部會 `permission denied` | 改以**欄位層級**授權(只給主鍵與時間欄位);批次用 `id IN (SELECT … LIMIT n)` 而非 `ctid`(系統欄位不在欄位授權範圍) | [13 §13.5](13-platform-ops.md#135-稽核-phase-21--m3) |
| 2 | `SUBSCRIPTION_CHANGED` 是 §13.5 強制的 26 種行為之一,但 09 §9.1 **沒有任何端點**會呼叫 `Subscription.changePlan`／`cancel`——該行為與那兩個聚合方法都永不可達(執行規則 16) | 補 `PATCH /api/v1/admin/tenants/{id}/subscription`(`system:admin`;`planCode=CANCEL` 為取消) | [09 §9.1](09-api.md#91-端點清單) |
| 3 | §13.4 要求「M3 提供資料主體查詢與刪除的管理端點」,但 09 全文未定義路徑/方法/權限;而刪除權與 §13.5 規則 1 的 append-only 直接衝突 | 補 `GET`／`DELETE /api/v1/admin/data-subjects/{userId}`;**稽核軌跡不在刪除範圍內**,以 180 天保留期收斂,法律基礎寫入 `docs/deployment/privacy.md` | [09 §9.1](09-api.md#91-端點清單)、[13 §13.4](13-platform-ops.md#134-隱私與資料保留) |
| 4 | `POST /auth/change-password` 在 09 全文不存在,而 ADR 0015 把「改密碼撤銷全部 token family」指定為 M3 責任(ADR 0022 指派本 phase) | 補端點與全撤;撤銷原因沿用 `ADMIN`(表 15 的列舉固定五值,不為此改 schema) | [09 §9.1](09-api.md#91-端點清單) |

### 規格缺口補齊（3 項）

| # | 項目 | 解決 | 修訂處 |
|---|---|---|---|
| 5 | 表 27 沒有 `action` 的 CHECK,而 §4.0 明文「列舉以 VARCHAR + CHECK 對應」——拼錯的 action 會靜靜寫進一張永不更新的表 | V33 補 26 值的 `ck_al_action` | [04 表 27](04-data-dictionary.md#27-auditlogs-phase-21--m3) |
| 6 | `AUDIT_SAMPLE_READ_RATE` 與四個保留 cron 只出現在內文,05 §5.4 與 compose 都沒宣告;`POSTGRES_RETENTION_*` 沒傳給 backend 服務 | 三處補齊(`ConfigSymmetryTest` 現在會擋) | [05 §5.4](05-environment.md#54-環境變數清單) |
| 7 | 12 §12.2 的目錄樹有 `audit/` 但沒有 `admin/`,而 §12.5 有 Admin Panel;兩頁也沒標所需權限 | 補 `admin/` 與兩頁的權限(`audit:read`／`system:admin`) | [12 §12.2、§12.5](12-frontend.md#125-頁面) |

### 實作決策（不改規格正文，僅記於 ADR 0031）

稽核以兩個橫切消費端實作(filter + event listener),業務服務一行不改;
Bloom artifact 清理沿用 Phase 15 的 `BloomRetentionService`(以應用角色執行,使用者裁示);
`RetentionConnection` 刻意不是 `DataSource` 型別的 bean(否則主資料源不會建立);
`OpenApiCompletenessTest` 的「POST 必有 request schema」改為「宣告了 requestBody 才要求」
(§9.1 有兩支無本文的動作端點)。

> **本 phase 未做而回報的三件事**(見 ADR 0031 末段):`TOKEN_CLEANUP_CRON`(08 §8.7,標 M2)
> 至今無實作;12 §12.5 的 Settings 頁(`/settings`,M2)不存在,改密碼端點因此還沒有前端入口。
> 兩者都屬 M2 的遺漏,建議指派給 Phase 23。

---

## 0.29 實作回饋修訂（2026-08-30，Phase 22 實測後回寫）

Phase 22（監控、日誌、追蹤）的修訂**已全數註記進對應主題檔**;
完整決策記錄見 [ADR 0032](../architecture/decisions/0032-phase22-observability.md)（14 節）。

### 照字面實作會失敗（4 項）

| # | 項目 | 解決 | 修訂處 |
|---|---|---|---|
| 1 | ⚠️ **關掉 `management.tracing.export.enabled` 會連「接收 `traceparent`」一起關掉**——Boot 的 `TextMapPropagator` bean 也掛在 `@ConditionalOnEnabledTracingExport` 上。沒有 OTLP collector 時照直覺關掉全域 export，傳入的 traceparent 就被忽略、server span 變成新 trace，§13.6 要的關聯線索等於不存在 | 全域開關維持 `true`，改用 `management.tracing.export.otlp.enabled`（`TRACING_EXPORT_ENABLED`）控制是否送出 span | [13 §13.6](13-platform-ops.md#136-監控日誌追蹤-phase-22--m3) |
| 2 | 追蹤切面若以**整個套件**當切入點，`final` 類別（`IndicatorSearchIndex`、`KafkaTopics`）會使 CGLIB 建不出代理，**整個 context 起不來** | 切入點只點名 `*Adapter` 與具名類別 | [13 §13.6](13-platform-ops.md#136-監控日誌追蹤-phase-22--m3)、[06 §6.3.6](06-tech-stack.md#636-spring-boot-4-模組化與-testcontainers-2x編譯地雷) |
| 3 | ⚠️ **Prometheus 的 exemplar 與 Lettuce 指標在啟動時死鎖**:exemplar 取樣器會在記錄指標的執行緒(netty event loop)上向 bean factory 要 `Tracer`,而主執行緒正握著 singleton 建立鎖等 Redis 連線——那條連線只能由同一個 event loop 完成。`RATE_LIMIT_BACKEND=redis` 的環境(dev/staging/prod)**卡在啟動且無任何錯誤訊息** | `management.tracing.exemplars.include: none`(exemplar 非 §13.6 要求項),並以測試鎖住 | [13 §13.6](13-platform-ops.md#136-監控日誌追蹤-phase-22--m3)、[06 §6.3.6](06-tech-stack.md#636-spring-boot-4-模組化與-testcontainers-2x編譯地雷) |
| 3b | `logback-spring.xml` 若以 `ctip.environment` 取環境名，其值是 `${ENVIRONMENT}` 這個必填佔位符；日誌系統在 environment-prepared 階段就初始化，測試的 `DynamicPropertySource` 還沒進來，**佔位符解不開會讓整個 context 起不來** | 直接讀 `ENVIRONMENT` 環境變數並給預設值 | [13 §13.6](13-platform-ops.md#136-監控日誌追蹤-phase-22--m3) |

### 規格自身衝突（1 項）

| # | 項目 | 解決 | 修訂處 |
|---|---|---|---|
| 4 | phase-22 的判準是 `up.sh staging` + `curl /actuator/prometheus`，而 05 §5.5 的差異表把 staging 列為 `health,info`——照字面設定判準必然 404 | staging 改為 `health,info,prometheus`；同時補列 `PROMETHEUS_ALLOWED_IPS` 與兩個追蹤變數、日誌格式一列 | [05 §5.5](05-environment.md#55-四種-profile-差異表)、[05 §5.4](05-environment.md#54-環境變數清單) |

### 規格缺口補齊與擴充（3 項）

| # | 項目 | 解決 | 修訂處 |
|---|---|---|---|
| 5 | §13.6 只寫「`prometheus` 需限制來源 IP」，而 `SecurityConfig` 是 `anyRequest().permitAll()`，actuator 端點沒有任何方法層宣告可掛（ADR 0021 已點名「該限制沒有實作位置」） | 以 `PrometheusAccessFilter` 落實；`PROMETHEUS_ALLOWED_IPS` **空清單 = 拒絕所有來源**；另補 prod 的 actuator 暴露白名單啟動守衛（`env`／`beans`／`configprops`／`heapdump` 一律拒絕啟動） | [13 §13.6](13-platform-ops.md#136-監控日誌追蹤-phase-22--m3) |
| 6 | 指標語意未定義：`ctip.source.sync.lag` 是耗時還是落後、`ctip.ratelimit.rejected` 的 `dimension` 是什麼、`kafka.consumer.lag` 與 Micrometer 的原生名稱不同 | 三者的語意與取值方式寫入 §13.6 的引用區塊 | [13 §13.6](13-platform-ops.md#136-監控日誌追蹤-phase-22--m3) |
| 7 | `ctip-core` 的 application 層需要 Micrometer 當指標門面，而 ArchUnit 規則 1 的禁止清單沒有它——domain 層等於沒有防線 | 規則 1 的清單加入 `io.micrometer..`（規則數維持 11 條，§0.3 的契約不變） | [01 §1.9](01-architecture.md#19-archunit-規則強制共-11-條) |

### 實作決策（不改規格正文，僅記於 ADR 0032）

指標一律在啟動時註冊(序列不存在 ≠ 值為 0);日誌格式由 profile 決定而非環境變數
(logback 的 `<if>` 需要版本表沒有的 Janino);遮罩刻意不動十六進位摘要(指紋與 traceId 是主線索);
`BloomSnapshotService.generateAll()` 因 per-scope 計時而移除(執行規則 16)。

---

## 0.30 實作回饋修訂（2026-08-30，Phase 23 實測後回寫）

Phase 23（CI/CD 完整化、安全掃描、文件）的修訂**已註記進對應主題檔**；
完整決策記錄見 `docs/architecture/decisions/0041-phase23-cicd-security-docs.md`。
本 phase 另補齊八則跨 phase 的架構 ADR（0033–0040）。

### 規格缺口補齊（4 項）

| # | 項目 | 解決 | 修訂處 |
|---|---|---|---|
| 1 | 「相依弱點」二擇一選 Dependabot alerts，但 alerts 是 repo 面板、**不會擋 PR**——照字面實作，CI 上等於沒有這一道 | 選 Dependabot（`.github/dependabot.yml`），另以 Trivy 檔案系統掃描（`exit-code: 1`、`ignore-unfixed`）提供會失敗的 CI 訊號 | [13 §13.8](13-platform-ops.md#138-cicd-phase-23--m3基本流程自-m1-就要有) |
| 2 | **M3-20 的 SBOM 沒有產生路徑**：`bom.json` / `sbom.json` 從未被任何建置步驟產出，DoD 只檢查存在性 | CycloneDX plugin 綁 `package`（`makeAggregateBom`、`includeTestScope=false`）；frontend 新增 `npm run sbom`。**兩者皆為建置產物不進版控**——過期的 SBOM 比沒有 SBOM 更危險 | [15 §15.3](15-dod-gates.md#153-dod-fullphase-2023)、[13 §13.8](13-platform-ops.md#138-cicd-phase-23--m3基本流程自-m1-就要有) |
| 3 | `deploy-prod` 的「人工核准」有一半**版控檔案表達不了**：required reviewers 存在 GitHub repo 設定裡，綁了 `production` environment 但規則是空的，workflow 照跑 | 檔案端做到 `workflow_dispatch` + 確認字串 + `environment: production`，M3-19 驗 environment 綁定；reviewer 設定列入需人工確認 **P-07**（清單由 6 項增為 7 項） | [15 §15.5](15-dod-gates.md#155-需人工確認未被自動驗證)、[13 §13.8](13-platform-ops.md#138-cicd-phase-23--m3基本流程自-m1-就要有) |
| 4 | STIX Viewer 要求「關聯」，但 `GET /api/v1/stix/{stixId}` 只回單一物件，**平台沒有反查端點**——從 indicator 出發看不到指向它的 relationship | 圖只順著物件自身的參照往外長（SRO 畫成邊）；限制寫入規格與 UI 文案，不以假資料掩蓋 | [12 §12.6](12-frontend.md#stix-viewer-m3) |

### 判準本身的修訂（1 項）

| # | 項目 | 解決 | 修訂處 |
|---|---|---|---|
| 5 | phase-23 要求 `dod.sh` 增設「11 支 workflow 檔案皆存在」的檢查，但新增一個 ID 會讓 [15 §15.3](15-dod-gates.md#153-dod-fullphase-2023) 的「25 項」失真 | **M3-19 就地擴充**為「11 支檔案存在 → `deploy-prod` 綁定 environment → CI 全綠」；那個檢查在語意上本來就是 M3-19 的前置（「只有兩支且都綠」正是六支 workflow 逾期十個 phase 的原因） | [15 §15.3](15-dod-gates.md#153-dod-fullphase-2023) |

### 實作決策（不改規格正文，僅記於 ADR 0041）

`ExampleThreatSourceAdapter` 放 `ctip-sdk` 的**測試**原始碼並沿用既有 `SourceType` 成員
（新增 `EXAMPLE` 成員會留下永不可達的列舉值，違反規則 16）；
安全掃描 action 釘 commit SHA（gitleaks v3.0.0、trivy-action v0.36.0，tag 寫在註解裡）；
STIX Viewer 是唯一 code-split 的路由（Cytoscape.js 約 370 kB）。

### 兩項 M2 遺漏的補件（Phase 23 補件；[ADR 0042](../architecture/decisions/0042-m2-gaps-token-cleanup-and-settings.md)）

連續回報三次、且**不在任何 phase 執行單交付物清單裡**的兩項標 `[M2]` 內容，於本 phase 補齊：

| # | 項目 | 解決 | 修訂處 |
|---|---|---|---|
| 6 | **`TOKEN_CLEANUP_CRON`**（[08 §8.7](08-ingestion-sdk.md#排程)）自 Phase 13 起就在排程表，但從未實作——表 15 的 `EXPIRED_CLEANUP` 與名為 `ix_rt_gc` 的索引因此一直沒有寫入者 | `ExpiredTokenCleanupService` + `IdentitySchedulers`：**標記為 `EXPIRED_CLEANUP`，不刪除列**（刪列等於偷偷新增第七項保留政策）；語意定調寫入 §8.7 | [08 §8.7](08-ingestion-sdk.md#排程)、[05 §5.4](05-environment.md) |
| 7 | **[12 §12.5](12-frontend.md#125-頁面) 的 Settings 頁（`/settings`）不存在**，而 `POST /auth/change-password`（Phase 21 交付）因此**沒有任何前端入口** | 交付 `/settings`（只掛 `RequireAuth`）：帳號資訊、外觀、變更密碼、其他設定頁入口。變更密碼成功後**就地清掉本地 session**——後端會撤銷含呼叫端自己在內的全部 token family | [12 §12.5](12-frontend.md#125-頁面) |

### 依規則 17 回報（1 項，未完成）

**M3-19 在本機無法通過**：`gh` 未安裝、repo 從未推上 GitHub，CI 從未跑過
（[15 §15.3](15-dod-gates.md#153-dod-fullphase-2023) 註明的操作者前置，非專案交付物）。

---

*主綱結束。規格版本 v2.0（含 2026-08-21、2026-08-25、2026-08-26、2026-08-27、2026-08-28、2026-08-29、2026-08-30 實作回饋修訂，見 §0.7–§0.30）。*
