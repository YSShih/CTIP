# 15 — Definition of Done Gates（可執行）

> **規範等級：強制。** 未通過該里程碑的 Gate，不得開始下一個里程碑。
>
> **本版的核心改動**：v1.1 的六十條 DoD 幾乎都是人類散文（「hot reload 實測」、「STIX 匯出格式符合 2.1」、「Swagger UI 可開啟，所有端點有 schema 與範例」）。**一個 AI 對著散文 checklist 自我評分，會通過。** 本檔的每一條都對應一個回傳 0/1 的指令；確實無法自動化的集中列於 15.5，並明確標為「需人工確認」。

---

## 15.0 執行方式

```bash
./environment/scripts/dod.sh mvp        # 執行 DoD-MVP 全部檢查
./environment/scripts/dod.sh phase2
./environment/scripts/dod.sh full
./environment/scripts/dod.sh mvp M1-14  # 只執行單一項目
```

`dod.sh` 契約：

| 規則 |
|---|
| 逐項執行，每項印出 `[PASS] <id> <描述>` 或 `[FAIL] <id> <描述>` 與失敗輸出 |
| 全部通過則 exit 0；任一失敗則 exit 1 並在結尾列出失敗項清單 |
| 支援 `--only <id>` 與 `--skip <id>` |
| **不得**因為某項失敗就中止後續檢查（要一次看到全部問題） |
| 結尾必須印出「需人工確認」清單，並提示這些項目未被自動驗證 |

> 判準中的 `./backend/mvnw … -Dtest=<類名>` 在多 module reactor 下依賴 parent pom 的 surefire 設定
> `failIfNoSpecifiedTests=false`（[06-tech-stack.md §6.3.6](06-tech-stack.md#636-spring-boot-4-模組化與-testcontainers-2x編譯地雷) 第 4 點）；缺少該設定時，沒有該測試類的 module 會使整個指令失敗。

> **實作回饋修訂（2026-08-28；[ADR 0017](../architecture/decisions/0017-gate-credibility.md)）**——三項與判準指令有關的修正：
>
> 1. **`./mvnw` 不存在**：wrapper 在 `backend/mvnw`，repo 根沒有。23 份執行單原本都寫 `./mvnw -f backend/pom.xml`，
>    逐字執行必然 `no such file`（Phase 1–13 是由人轉譯後執行才沒踩到）。全部改為 `./backend/mvnw -f backend/pom.xml`。
> 2. **過濾式判準一律用 `test`，不用 `verify`**：`verify` 會綁上 JaCoCo `check`，而只跑幾個測試類
>    不可能達到 `ctip-core` 的 PACKAGE 門檻（domain 0.85 / application 0.75）。`dod.sh` 一直用的就是 `test`
>    ——兩處原本不一致。**每個 phase 收尾另跑一次無過濾的 `clean verify -Ptest-integration`**（此為既有慣例）。
> 3. **`failIfNoSpecifiedTests=false` 的反面代價**：測試類**不存在**時 surefire 跑 0 個測試、build 仍成功，
>    於是尚未實作的 phase 的 DoD 項目會一路 `[PASS]`。實測 Phase 14/15/16 一行程式都沒有時，
>    `dod.sh phase2` 仍回報 27/27 全綠。`dod.sh` 現在會**先確認測試類檔案存在**再交給 Maven。

---

## 15.1 DoD-MVP（Phase 1–12）

| ID | 檢查 | 指令 |
|---|---|---|
| M1-01 | 四個 module 皆編譯，L1–L3 測試通過 | `./mvnw -f backend/pom.xml verify -Ptest-integration` |
| M1-02 | 覆蓋率門檻達標（domain ≥ 85%） | 同上（JaCoCo `check` 綁在 `verify`） |
| M1-03 | ArchUnit 11 條規則通過 ¹ | `./mvnw -f backend/pom.xml test -Dtest=ArchitectureTest` |
| M1-04 | Spotless 格式一致 | `./mvnw -f backend/pom.xml spotless:check` |
| M1-05 | Checkstyle 五條可讀性規則通過 | `./mvnw -f backend/pom.xml checkstyle:check` |
| M1-06 | 前端 type check 通過 | `cd frontend && npx tsc --noEmit` |
| M1-07 | 前端 lint 通過（含 feature 依賴規則） | `cd frontend && npx eslint . --max-warnings 0` |
| M1-08 | 前端 build 通過 | `cd frontend && npm run build` |
| M1-09 | 前端測試通過且覆蓋率達標 | `cd frontend && npm run test -- --coverage` |
| M1-10 | 前端型別與 OpenAPI 一致（非手寫） | `cd frontend && npm run api:check` |
| M1-11 | 四種 env 的 compose config 皆有效 | `./environment/scripts/dod.sh mvp M1-11`（迴圈四個 `.env.*.example`） |
| M1-12 | prod 設定不含原始碼掛載 | `docker compose --env-file environment/.env.prod.example -f environment/docker-compose.yml config \| grep -qE '\.\./(backend\|frontend)' && exit 1 \|\| exit 0` |
| M1-13 | prod 設定不含 JDWP debug agent | 同上，`grep -qi jdwp` |
| M1-14 | `up.sh mvp` 成功，且**只有** frontend/backend/postgres 三個容器 | `./environment/scripts/up.sh mvp && test "$(docker compose ... ps --services \| wc -l)" -eq 3` |
| M1-15 | 三個容器皆 healthy | `docker compose ... ps --format json \| jq -e 'all(.Health == "healthy" or .Health == "")'` |
| M1-16 | Flyway 從空資料庫執行至最新版本成功 | `./mvnw -f backend/pom.xml test -Dtest=MigrationIntegrationTest` |
| M1-17 | public system tenant 存在且不可刪除 | `test -Dtest=PublicTenantIntegrationTest` |
| M1-18 | 樣本資料寫入成功（≥1000 IOC，涵蓋所有型別與四種 TLP） | `test -Dtest=SampleDataIntegrationTest` |
| M1-19 | 資料庫中無 `TLP:RED` 資料 | `test -Dtest=TlpRedAbsenceTest` |
| M1-20 | 所有必要索引存在 | `test -Dtest=RequiredIndexTest`（比對 [04](04-data-dictionary.md) 的索引清單） |
| M1-21 | `MockOpenPhishAdapter` 端對端：抓取→驗證→正規化→去重→合併→落庫 | `test -Dtest=IngestionEndToEndTest` |
| M1-22 | 髒資料被拒絕並記入 `ingestion_rejections`，八種 reason 皆覆蓋 | `test -Dtest=RejectionRuleTest` |
| M1-23 | 多來源重疊 IOC 依 `IndicatorMergePolicy` 正確合併 | `test -Dtest=IndicatorMergePolicyTest` |
| M1-24 | 三步 `valid_until` 計算正確（含來源未明示與 `FILE_HASH` 分支） | `test -Dtest=ValidityPeriodTest` |
| M1-25 | 正規化規則七種型別全數正確 | `test -Dtest=NormalizationTest` |
| M1-26 | `GET /api/v1/iocs` 回傳正確結果，cursor 分頁可連續翻至最後一頁 | `test -Dtest=CursorPaginationIntegrationTest` |
| M1-27 | `POST /api/v1/iocs/search` 篩選正確 | `test -Dtest=IocSearchIntegrationTest` |
| M1-28 | 安全測試 1、3、7、9 通過（匿名 TLP、跨租戶 404、限流 429、再散布作用域） | `test -Dtest=SecurityTest` |
| M1-29 | STIX 匯出以 STIX 2.1 JSON Schema 驗證通過 | `test -Dtest=StixSchemaValidationTest` |
| M1-30 | 五個 TLP marking UUID 與 OASIS 定義完全相符 | `test -Dtest=StixTlpMarkingsTest` |
| M1-31 | 六種 IocType 的 pattern 模板與四種 hash 演算法對應正確 | `test -Dtest=StixPatternTest` |
| M1-32 | 錯誤回應符合統一結構，含 `traceId` | `test -Dtest=ErrorResponseTest` |
| M1-33 | Swagger UI 可開啟 | `curl -fsS http://localhost:8080/swagger-ui/index.html` |
| M1-34 | 所有端點皆有 summary、response schema 與至少一個範例 | `test -Dtest=OpenApiCompletenessTest`（解析 `/v3/api-docs` 逐端點檢查） |
| M1-35 | 前端 IOC 搜尋頁能查到後端資料，四種狀態皆呈現 | `cd frontend && npm run test -- IocSearchPage` |
| M1-36 | 前端 HMR 生效（修改 tsx 後 5 秒內更新） | `./environment/scripts/dod.sh mvp M1-36`（寫入標記字串、輪詢頁面內容） |
| M1-37 | 後端 reload 生效（`reload.sh` 後 10 秒內新行為生效） | `./environment/scripts/dod.sh mvp M1-37` |
| M1-38 | README 的啟動步驟可直接複製執行 | `./environment/scripts/dod.sh mvp M1-38`（擷取 README 的 bash 區塊並在乾淨環境執行） |

**38 項，全部可執行。**

> ¹ **規則數 9 → 11（2026-08-29）**：規則 10（詞彙表禁用命名）由 [ADR 0016](../architecture/decisions/0016-phase1-13-spec-backfill.md)
> 依 §15.5 的 P-02 加入卻未回寫計數；規則 11（application 不得依賴 Redis／Bucket4j 型別）為 Phase 17 新增。
> 判準指令不變——它跑的是整個 `ArchitectureTest`，規則增減自動涵蓋。

> M1-37 取代 v1.1 的「修改 backend Java 檔**自動**生效」。該條在 v1.1 的容器設計下**必定不通過**——dev stage 跑 `spring-boot:run`，DevTools 監看 `target/classes`，而容器內沒有任何程序會編譯 `.java`。改為腳本觸發後，這一條變成可執行且必定準確。詳見 [05-environment.md](05-environment.md#511-hot-reload-契約本版修正)。

---

## 15.2 DoD-Phase2（Phase 13–19）

| ID | 檢查 | 指令 |
|---|---|---|
| M2-01 | DoD-MVP 全部仍通過（回歸） | `./environment/scripts/dod.sh mvp` |
| M2-02 | 註冊／登入／refresh／登出全流程 | `test -Dtest=AuthFlowIntegrationTest` |
| M2-03 | Refresh token 輪替與重用偵測（重用觸發 family 全撤） | `test -Dtest=RefreshTokenRotationTest` |
| M2-04 | 五種角色的權限矩陣正確，`@PreAuthorize` 生效 | `test -Dtest=RbacMatrixTest`（參數化，涵蓋 [10](10-identity-plans.md) 矩陣每一格） |
| M2-05 | API Key 建立（僅回傳一次）、撤銷、scope 檢查、不可提權 | `test -Dtest=ApiKeyTest` |
| M2-06 | 跨租戶測試：**每一個** tenant-scoped 端點皆回 404 | `test -Dtest=CrossTenantIsolationTest` |
| M2-07 | 安全測試 1–9 全數通過 | `test -Dtest=SecurityTest` |
| M2-08 | 方案配額生效，超限回 429 且帶 `X-RateLimit-*` | `test -Dtest=QuotaEnforcementTest` |
| M2-09 | Redis 限流在**兩個 app 實例**下正確 | `test -Dtest=DistributedRateLimitTest`（Testcontainers 起兩個 app） |
| M2-10 | Public bloom 與 tenant bloom 皆可生成 | `test -Dtest=BloomGenerationTest` |
| M2-11 | Bloom 位元序與雙雜湊索引符合 11.4 規格 | `test -Dtest=BloomBitLayoutTest` |
| M2-12 | Bloom checksum 驗證通過 | 同 M2-10 |
| M2-13 | `TLP:GREEN` 不進入 public bloom | `test -Dtest=BloomCoverageTest` |
| M2-14 | Delta 生成與套用正確，`resultingChecksum` 相符 | `test -Dtest=BloomDeltaTest` |
| M2-15 | Delta 鏈超過上限時回 `409 SNAPSHOT_REQUIRED` | `test -Dtest=SyncEndToEndTest` ¹ |
| M2-16 | 完整同步流程端對端（manifest → delta → 套用 → 更新版本） | `test -Dtest=SyncEndToEndTest` |
| M2-17 | manifest 含 `coverage` 與 `notCovered` 欄位 | 同 M2-16 |
| M2-18 | 手動提交 IOC 走完整 pipeline，預設 `TLP:AMBER` | `test -Dtest=ManualSubmissionTest` |
| M2-19 | 匯入超出方案上限回 `413` | 同 M2-18 |
| M2-20 | 誤判回報後 status 由合併規則決定（非呼叫端指定） | `test -Dtest=FalsePositiveReportTest` |
| M2-21 | Threat 實體與 `threat_indicators`、`threat_external_references` 可用 | `test -Dtest=ThreatIntegrationTest` |
| M2-22 | Elasticsearch 索引建立、搜尋正確 | `test -Ptest-all -Dtest=ElasticsearchSearchTest` ³ |
| M2-23 | ES 掛掉時 API 降級為 PostgreSQL（回 200 + `X-Search-Backend: postgres`） | `test -Dtest=SearchFallbackTest` |
| M2-24 | Reconciliation 能偵測並修正 DB 與 ES 差異 | `test -Dtest=SearchReconciliationTest` |
| M2-25 | `up.sh staging` 成功且未掛載原始碼 | `./environment/scripts/dod.sh phase2 M2-25` |
| M2-26 | Playwright E2E：匿名搜尋、登入、建立 API key、提交 IOC | `cd frontend && npx playwright test` ² |
| M2-27 | L1–L3 全通過，覆蓋率門檻達標 | `./mvnw -f backend/pom.xml verify -Ptest-integration` |

> ¹ **判準改指向 `SyncEndToEndTest`（2026-08-28，Phase 16；[ADR 0025](../architecture/decisions/0025-phase16-sync-api-decisions.md)）**：
> 原本寫「同 M2-14」，而 `BloomDeltaTest` 驗的是**生成端**「鏈太長 → 改生 full」——
> `409 SNAPSHOT_REQUIRED` 這個 HTTP 行為在 Phase 15 根本還不存在，該項因此是**假綠**。
> `SyncEndToEndTest` 真的產生 25 段 delta 讓 `chainLength > BLOOM_MAX_DELTA_CHAIN` 在資料庫裡成立，
> 再斷言 `409` + `code = SNAPSHOT_REQUIRED`，並確認 409 之後仍可下載 full（409 不消耗同步間隔）。
>
> ² **執行前置（2026-08-28，Phase 16）**：需 `cd frontend && npm ci` 與
> `npx playwright install chromium`（瀏覽器本體屬本機／CI 前置，非專案交付物，同 `gh` CLI 的處置）。
> `webServer` 會自己跑 `npm run build && npm run preview`，因此不需要另外起 dev server。
> 四個情境以 `page.route` 攔截 API 邊界執行（[12 §12.8](12-frontend.md#128-前端測試)）；
> 整套環境的驗證由 M2-25 與 M3-05 負責。
>
> ³ **改用 `test` 並走 `dod.sh` 的存在性守衛（2026-08-29，Phase 19；[ADR 0028](../architecture/decisions/0028-phase19-elasticsearch-search.md)）**：
> 原指令是本節唯一用 `verify` 的過濾式判準，違反 [§15.0](#150-執行方式) 自訂的
> 「過濾式判準一律用 `test`」（`verify` 綁 JaCoCo `check`，單一測試類不可能滿足門檻）；
> 更嚴重的是它因此**繞過 `dod.sh` 的 `mvn_test` 存在性守衛**（[ADR 0017](../architecture/decisions/0017-gate-credibility.md)），
> 在 `ElasticsearchSearchTest` 尚未存在時是 0 個測試的**空轉通過**。已與 M2-23／M2-24 統一。

**27 項，全部可執行。**

---

## 15.3 DoD-Full（Phase 20–23）

| ID | 檢查 | 指令 |
|---|---|---|
| M3-01 | DoD-MVP 與 DoD-Phase2 全部仍通過 | `./environment/scripts/dod.sh mvp && ./environment/scripts/dod.sh phase2` |
| M3-02 | Kafka（KRaft）啟動，事件正確發佈與消費 | `./mvnw verify -Ptest-all -Dtest=KafkaEventTest` |
| M3-03 | 消費端冪等（重複 `eventId` 不產生重複副作用） | `test -Dtest=EventIdempotencyTest` |
| M3-04 | Kafka 不可用時業務操作不失敗 | `test -Dtest=KafkaUnavailableTest` |
| M3-05 | WebSocket 通知端對端，斷線自動重連 | `cd frontend && npx playwright test websocket` |
| M3-06 | Webhook 送達、HMAC 簽章正確（含 timestamp 防重放） | `test -Dtest=WebhookDeliveryTest` |
| M3-07 | Webhook 失敗重試與連續 5 次後停用 | 同 M3-06 |
| M3-08 | 訂閱過濾在伺服器端執行 | `test -Dtest=WebhookFilterTest` |
| M3-09 | Audit log append-only：應用角色的 UPDATE/DELETE 被 DB 拒絕 | `test -Dtest=AuditAppendOnlyTest` |
| M3-10 | 稽核寫入失敗不影響主要業務操作 | `test -Dtest=AuditFailureIsolationTest` |
| M3-11 | 六項資料保留任務正確清理 | `test -Dtest=RetentionTaskTest` |
| M3-11b | 26 種稽核行為皆有實際寫入路徑（無永不可達行為） | `test -Dtest=AuditCompletenessTest` |
| M3-12 | Prometheus 指標齊全（含每個 ingestion stage 的耗時） | `test -Dtest=MetricsCompletenessTest`（比對 [13](13-platform-ops.md) 指標清單） |
| M3-13 | Grafana dashboard 可載入 | `./environment/scripts/dod.sh full M3-13`（驗證 provisioning JSON 有效） |
| M3-14 | OpenTelemetry trace 從 API 串到 DB / Kafka / ES | `test -Dtest=TracePropagationTest` |
| M3-15 | 日誌不含敏感欄位 | `test -Dtest=SensitiveLogTest` |
| M3-16 | `traceId` 同時出現在錯誤回應與日誌 | 同 M3-14 |
| M3-17 | prod 設定驗證：不掛載原始碼、無明文 secret、CORS 非 `*`、Swagger 關閉 | `./environment/scripts/dod.sh full M3-17` |
| M3-18 | prod 啟動守衛生效（樣板 JWT_SECRET 與 `CORS=*` 皆拒絕啟動） | `test -Dtest=StartupValidatorTest` |
| M3-19 | CI 全綠：測試、lint、build、compose 驗證、弱點掃描、secret 掃描、映像掃描 | `gh run list --limit 1 --json conclusion -q '.[0].conclusion == "success"'` |
| M3-20 | SBOM 產出（backend CycloneDX + frontend npm sbom） | `test -f target/bom.json && test -f frontend/sbom.json` |
| M3-21 | `ctip-sdk` 可獨立打包 | `./mvnw -f backend/pom.xml -pl ctip-sdk package` |
| M3-22 | `ExampleThreatSourceAdapter` 可編譯並通過測試 | `./mvnw -f backend/pom.xml -pl ctip-sdk test -Dtest=ExampleAdapterTest` |
| M3-23 | 文件齊備（12 份必要文件皆存在且非空） | `./environment/scripts/dod.sh full M3-23` |
| M3-24 | 所有規格內部交叉引用皆指向存在的目標 | `./environment/scripts/dod.sh full M3-24`（掃描 `docs/spec/**` 的相對連結與 anchor） |

**25 項，全部可執行。**

> **實作回饋修訂（2026-08-28；[ADR 0016](../architecture/decisions/0016-phase1-13-spec-backfill.md)）**：
> 「全部可執行」有兩項前置未在此註明——**M3-17** 需要 `environment/.env.prod`（真實檔，
> 依 `.gitignore` 不進版控，須由操作者先從樣板建立）；**M3-19** 需要本機安裝 `gh` 並已推上 GitHub 跑過 CI。
> 兩者都不是腳本能自備的，執行 `dod.sh full` 前必須先備妥。

M3-23 檢查的 12 份文件：`README.md`、`SECURITY.md`、`CONTRIBUTING.md`、`LICENSE`、`docs/architecture/overview.md`、`docs/architecture/security.md`、`docs/deployment/licensing.md`、`docs/deployment/privacy.md`、`docs/development/getting-started.md`、`docs/development/plugin-sdk.md`、`docs/development/version-audit.md`、`docs/api/openapi.json`。

---

## 15.4 每個 Phase 的內部循環

```text
讀該 Phase 執行單 → 產生程式碼 → 編譯 → 測試 → 修正 → 執行該 Phase 的完成判準 → commit → 下一個 Phase
```

**絕不在未編譯驗證的情況下產生數百個檔案。**

每個 Phase 執行單（`phases/phase-NN.md`）都有自己的完成判準，是 DoD Gate 的子集。Phase 完成 ≠ 里程碑完成；里程碑完成需跑完整個 Gate。

---

## 15.5 需人工確認（**未被自動驗證**）

以下項目工具無法判斷，`dod.sh` 會在結尾列出並提示。**這份清單刻意保持很短**——每一項都是「自動化的成本高於價值」的判斷，不是「懶得寫」。

| # | 項目 | 為什麼無法自動化 |
|---|---|---|
| P-01 | 聚合圖（[03](03-diagrams.md#32-聚合圖)）與實際 domain 類別的方法一致 | ArchUnit 能驗證依賴方向，不能驗證「`Indicator` 有 `markExpired()`」。可寫反射測試但會在每次合理重構時誤報 |
| P-02 | Ubiquitous Language 詞彙表被遵守（未使用「常見誤用」欄的命名） | 需語意判斷。**部分可自動化**：可寫一條測試禁止類別名為 `IocEntity`、`SourceRecord`、`AbstractXxx` 等已知誤用 |
| P-03 | 程式碼「人類易讀」 | Checkstyle 只能量測行數與巢狀，無法量測命名與結構品質 |
| P-04 | Grafana dashboard 的圖表確實有意義 | M3-13 只驗證 JSON 有效 |
| P-05 | `docs/architecture/decisions/` 的 ADR 內容正確 | 只能驗證檔案存在 |
| P-06 | 版本表的「推估」支援終止日 | 需上網查證，見 [06-tech-stack.md](06-tech-stack.md#64-版本複查程序強制) |

**P-02 的可自動化部分必須實作**（列為 ArchUnit 規則的擴充），剩餘部分才算人工項。

---

*檔案結束。可執行項：90。需人工確認：6。上次校對：2026-08-21。*
