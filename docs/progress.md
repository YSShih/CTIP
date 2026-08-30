# CTIP 實作進度(跨 session 交接檔)

> append-only。每個 phase 完成後由該 session 更新。新 session 開場先讀這裡。
> Phase 順序與內容見 `docs/spec/00-master.md` §0.5。

## 總覽

| Milestone | Phase | 狀態 |
|---|---|---|
| M1 — MVP | 1–12 | **完成(dod.sh mvp 38/38)** |
| M2 — Platform | 13–19 | **完成(dod.sh phase2 27/27)** |
| M3 — Production | 20–23 | **四個 phase 全部完成**(含 Phase 23 補件的兩項 M2 遺漏);`dod.sh full`(25 項)待獨立 session 執行 |

---

## Phase 1 — Repository Skeleton

- **狀態**:done(2026-08-21)
- **執行單**:`docs/spec/phases/phase-01.md`
- **Commit**:(見 git log,message `Phase 1: repository skeleton`)
- **完成判準結果**:全綠 —
  - `./backend/mvnw -f backend/pom.xml -DskipTests package` ✅ 四個 module 皆產生 jar
  - `mvn verify`(含 Spotless、Checkstyle、JaCoCo 綁定)✅
  - `spotless:check` ✅、`checkstyle:check` ✅
  - `npm ci && npx tsc --noEmit && npx eslint . --max-warnings 0` ✅、`prettier --check .` ✅
  - `environment/.noop/.gitkeep` ✅;`.gitignore` 對 `.env*` / `.env*.example` 行為 ✅
- **偏離事項 / ADR**:兩項版本相容性處置,皆記錄於 `docs/development/version-audit.md`:
  1. **TypeScript 7.0.2 → 5.9.3 降版**:typescript-eslint 8.67.0 peer 上限 `<6.1.0`。這是 06 §6.3.2 明文允許的唯一降版。
  2. **eslint-plugin-import 2.32.0 peer 不含 ESLint 10**:規格版本表自身的矛盾(兩者皆標「已查證」)。以 package.json `overrides` 解決,規則實測正常。**已依規則 6/17 回報**。
- **給下一 session 的注意事項**:
  - Maven 指令一律用 `./backend/mvnw -f backend/pom.xml ...`(wrapper 已進版控,Maven 3.9.15)
  - 測試分層 profile 已就緒:預設只跑 `unit` tag;`-Ptest-slice` / `-Ptest-integration` / `-Ptest-all` 控制 excludedGroups
  - JaCoCo:各 module 以 `ctip.coverage.line-minimum` property 控制 BUNDLE 門檻;ctip-core 已依套件設兩條 PACKAGE 規則(domain 0.85 / application 0.75)
  - Checkstyle 設定在 `backend/config/checkstyle/ctip-checks.xml`,經 `${maven.multiModuleProjectDirectory}` 引用
  - frontend 的 feature 依賴規則 F1/F2/F4 已在 `eslint.config.js` 以 zones 實作(9 個 feature 名已預先列出);F3 由未來的 `api:check` script 負責(Phase 10)
  - Phase 2 是 Environment + Docker(compose、Dockerfile、四份 .env、scripts),治理規格 05-environment.md

---

## Phase 2 — Environment + Docker

- **狀態**:done(2026-08-21)
- **執行單**:`docs/spec/phases/phase-02.md`
- **Commit**:(見 git log,message `Phase 2: environment + docker`)
- **完成判準結果**:全綠 —
  - 四種 env 的 `docker compose config -q` ✅
  - prod 設定無原始碼掛載 ✅、無 JDWP ✅
  - staging/prod 樣板無 `*_MOUNT_*` 變數 ✅
  - `docker compose --env-file .env.mvp.example build` ✅(backend/frontend development stage 皆建成)
  - 全部 `.sh` 過 `bash -n`;`dod.sh` 的 `--only`/`--skip`/單項/FAIL 輸出/exit code/人工清單行為煙霧測試 ✅
- **交付物**:compose(§5.6 逐字)、backend/frontend Dockerfile(§5.3 逐字)、五份 `.env.*.example`、
  `config/nginx/default.conf`(安全標頭 + SPA fallback)、`config/{postgres,redis,kafka,elasticsearch,monitoring/*}`
  結構目錄、八支 scripts、`environment/README.md`、`.github/workflows/compose-validate.yml`
- **偏離事項 / ADR**:無版本或契約偏離。兩個實作層決定(皆在規格自由度內,未另立 ADR):
  1. nginx 靜態資產快取用 `expires` 而非 `add_header Cache-Control`——location 內出現 `add_header`
     會整組遮蔽 server 層安全標頭(nginx 繼承規則),安全優先。
  2. `dod.sh` 的單測項統一以 `-Ptest-all -Dsurefire.failIfNoSpecifiedTests=false` 執行,
     避免預設 profile 的 unit tag 過濾使 `-Dtest=<IntegrationTest>` 空跑。
- **給下一 session 的注意事項**(Phase 3 = Spring Boot 啟動 + PostgreSQL + Flyway 9 張表 + 種子資料,治理規格 04、05):
  - compose 對 backend 的 `ENVIRONMENT`/`POSTGRES_*`/`JWT_SECRET`/`CORS_ALLOWED_ORIGINS` 用 `:?` 強制;
    Spring 設定對應契約在 05 §5.7(`application.yml` + 四個 profile yml、`@ConfigurationProperties` record、
    `StartupValidator` 守衛、`ddl-auto: validate`)——這些是 Phase 3 的範圍
  - `migrate.sh` 呼叫 `mvnw -pl ctip-app flyway:migrate`,需要 Phase 3 在 ctip-app 加 Flyway maven plugin 才可用
  - JWT_SECRET 樣板值的 canonical 字串是 `CHANGE_ME_MIN_32_BYTES`(`_common.sh` 與未來 StartupValidator 應一致)
    — ⚠️ **已於 Phase 13 更正為 `CHANGE_ME_MIN_32_BYTES_REPLACE_THIS`**:原值本身只有 22 bytes,
    HS256 上線後會使照 README 快速開始的全新環境啟動失敗(見 Phase 13 段落)
  - `dod.sh` 中依賴執行中 stack 的項目(M1-14/15/33/36/37、M2-25、M3-17)使用**真實** `.env.<env>`
    (由 up.sh 引導從 .example 複製);其餘 compose 檢查用 `.example`
  - `dod.sh` M1-15 需要 `jq`、M3-24 需要 `python3`(缺少時該項會 FAIL 並說明)
  - 本機已驗證 Docker 29.4.0 / Compose v5.1.2;mvp 的兩個 development image 已建置並留在本機快取

---

## Phase 3 — Spring Boot + PostgreSQL + Flyway + 種子資料

- **狀態**:done(2026-08-21)
- **執行單**:`docs/spec/phases/phase-03.md`
- **Commit**:(見 git log,message `Phase 3: spring boot + postgresql + flyway + seed data`)
- **完成判準結果**:全綠 —
  - `verify -Ptest-integration -Dtest='Migration…,PublicTenant…,SampleData…,RequiredIndex…,TlpRedAbsence…'`(逐字)✅ 22/22
  - 另跑無過濾 `verify -Ptest-integration` ✅ 29/29(含 StartupValidatorTest 7 項 unit)、Spotless/Checkstyle/JaCoCo 全過
  - `./environment/scripts/up.sh mvp` ✅ 三容器 healthy;`curl /actuator/health | jq -e '.status == "UP"'` ✅
  - compose stack 內實測:indicators=1020、flyway 7 版全 success、sources=4
- **交付物**:`CtipApplication`、`CtipProperties`(單一 @ConfigurationProperties record,@Validated)、
  `StartupValidator`(五條守衛)、`SeedDataConfig`、`application.yml` + 四 profile yml、
  `V1`–`V7` migrations、`db/seed/sample_data.sql`(1,020 IOC,冪等)、五個整合測試 + 1 個單元測試
- **偏離事項 / ADR**:五項規格衝突處置,全部記錄於 `docs/architecture/decisions/0001-phase3-spec-conflict-resolutions.md`:
  1. **public tenant 加 DB 觸發器**(T2 深度防禦,V2)
  2. **V7 的 `fk_so_threat` 延後至 V25**(threats 是 M2 表,04 自身衝突;Phase 18 須以 ALTER TABLE 補 FK)
  3. **JDWP 從 BACKEND_JAVA_OPTS 移到 spring-boot:run 的 jvmArguments**(JAVA_TOOL_OPTIONS 會使 Maven 與 app 兩個 JVM 搶 5005)
  4. **postgres volume 掛載點改 `/var/lib/postgresql`**(postgres:18 拒絕掛 …/data;規格第五項建置阻斷缺陷)
  5. **frontend node_modules 以 named volume 遮罩 + up.sh `npm ci` 預熱**(macOS 原生 binding 進 Linux 容器必失敗)
  - 另兩項建置層修正(未列 ADR):Boot 4 模組化需明確加 `spring-boot-flyway` 依賴,否則 Flyway autoconfig 不存在、
    migration 靜默不執行;parent surefire 設 `failIfNoSpecifiedTests=false`,否則規格判準的 `-Dtest=` 指令在無測試的 module 必炸
- **給下一 session 的注意事項**(Phase 4 = Domain + 最小安全層,治理規格 02、07、10、01):
  - Boot 4(4.1.0)已模組化:testcontainers 座標帶 `testcontainers-` 前綴(2.x,`PostgreSQLContainer` 在
    `org.testcontainers.postgresql`,非泛型);per-tech autoconfig 在獨立 module(如 spring-boot-flyway)
  - 整合測試基底 `AbstractPostgresIntegrationTest`:單例 postgres:18-alpine 容器 + mvp profile +
    DynamicPropertySource 走真實 application.yml 對應;新整合測試直接繼承即可共用 context
  - seed 載入順序:Boot 預設 script initializer 在 Flyway **之前**;`SeedDataConfig` 以
    `@DependsOn("flywayInitializer")` 修正——若改動 seed 機制,不要退回 Boot 預設行為
  - `spring-boot.run.skip` parent=true / ctip-app=false:dev 容器 CMD 的 `-pl ctip-app -am spring-boot:run`
    會對每個 reactor module 執行 run goal,勿移除
  - Phase 4 的 domain 類別須依 T2 在 domain 層再次強制 public tenant 不變量(DB 觸發器只是最後防線)
  - mock 來源 V4 種子的 metadata(default_tlp/redistribution/reputation)是我依 08 §8.3 的合理選值;
    Phase 5 實作 adapter 的 `SourceMetadata` 時應與 V4 對齊或回頭調整 V4(migration 不可改,必要時新增 migration)
  - `environment/.env.mvp`(真實檔)已在本機由 example 複製,不進版控

---

## 規格修訂 — Phase 2–3 衝突回寫(2026-08-21,Phase 3 之後)

- **狀態**:done(使用者指示:修復規格衝突並將摘要回寫進 md)
- **Commit**:(見 git log,message `Spec: write Phase 2-3 conflict resolutions back into spec (§0.7)`)
- **內容**:Phase 2–3 發現的規格衝突已全數修入 `docs/spec/`,規格恢復 single source of truth;
  總摘要新增於 `00-master.md` **§0.7**,逐項如下:
  - `04`:表 8 的 `fk_so_threat` 改為 V25 以 ALTER TABLE 補上(§4.7 的 V7/V25 列同步更新);
    表 1 補記 V2 的 `trg_tenants_protect_public` 觸發器(T2 深度防禦)
  - `05`:§5.5 mvp/dev 的 `BACKEND_JAVA_OPTS` 改為空(JDWP 移至 spring-boot:run,註 ¹)、
    新增 `NODE_MODULES_*` 變數與差異表列;§5.6 骨架修 postgres 掛載點(`/var/lib/postgresql`)、
    加 node-modules 遮罩掛載與頂層 volume、改寫 debug port 註記;新增 **§5.8.1**(四項實作回饋缺陷表);
    §5.10 up.sh 契約補預熱步驟(第 5 步)與「exited 即失敗」
  - `06`:新增 **§6.3.6**(Boot 4 模組化缺 `spring-boot-flyway` 則 migration 靜默不執行、
    Testcontainers 2.x 座標/套件改名、sql.init 先於 Flyway、`failIfNoSpecifiedTests=false`)
  - `15`:§15.0 補註 `-Dtest` 判準依賴 parent surefire 設定
  - `dod.sh` 的 M3-24 交叉引用檢查器修正:承認 `<a id="…">` HTML anchor(修正前對既有規格全是誤報);
    修正後 M3-24 全綠,含本次新增的全部連結
- **驗證**:M3-24 ✅;四環境 compose config ✅;M1-11/12/13 ✅(程式碼與環境檔在 Phase 3 已改好,
  本次僅規格文字與檢查器,無行為變更)
- **給下一 session 的注意事項**:讀規格時 §0.7 是 Phase 2–3 修訂的索引;05 §5.8.1 與 06 §6.3.6
  是照字面實作會踩的坑清單,Phase 4 之後新增基礎設施(Redis/Kafka/ES)時記得對應的
  `spring-boot-<tech>` autoconfig 模組

---

## Phase 4 — Domain(Indicator / Tenant / Source / TLP)+ 最小安全層

- **狀態**:done(2026-08-24)
- **執行單**:`docs/spec/phases/phase-04.md`
- **Commit**:(見 git log,message `Phase 4: domain aggregates + minimal security layer`)
- **完成判準結果**:全綠 —
  - `verify -Ptest-integration` ✅ BUILD SUCCESS(sdk 13 + core 44 + app 45 = 102 tests;
    JaCoCo 門檻全過:domain 各套件 ≥ 0.85、sdk ≥ 0.70、app ≥ 0.60)
  - `test -Dtest=ArchitectureTest` ✅ 9/9;`test -Ptest-integration -Dtest=SecurityTest` ✅ 4/4(條號 1、2、3、9)
- **交付物**:
  - sdk:8 個 Shared Kernel 型別(Tlp.strictest/Severity.max/Confidence 驗證)+ 列舉成員契約測試
  - core/domain:Indicator(I1–I14)、Tenant(T1–T4)、Source(S1–S5)、IndicatorMergePolicy(7.5 全公式)、
    值物件(IocValue/ValidityPeriod/Fingerprint/Reputation/TenantSlug/Cursor/CursorPage/Visibility)、
    Sha256FingerprintStrategy、DomainEvent + 11 個 M1 事件(PendingEvents 收集、信封欄位由發佈端補)
  - core/application/port:Indicator/Source/Tenant Repository、SearchPort、EventPublisherPort、
    RateLimiterPort、ClockPort、IdGeneratorPort
  - app/infrastructure:9 張表 JPA entity(package-private 欄位)+ 3 個 RepositoryAdapter +
    package-private JpaRepository + MapStruct mapper;security:TenantContext(@RequestScope)、
    AnonymousTenantFilter、TlpSpecifications(唯一一套過濾)、AuthState;config/PortsConfig
- **偏離事項 / ADR**(`docs/architecture/decisions/0002-…`,五項):
  1. **TLP 過濾採複合條件**——§1.11 的單一 maxVisibleTlp 無法表達 §7.7 可見度表(自家全部 + public 上限),安全優先
  2. 事件信封欄位(eventId/occurredAt/traceId)由發佈端補齊(規則 9 禁 domain 取時間/亂數)
  3. 第 11 個事件 = IndicatorFalsePositiveReported(2.4 標 M2 但行為屬 Phase 4)
  4. ArchUnit 規則 5 切片粒度 = 頂層模組(規格結構在子套件粒度必然成環)
  5. entity 用 package-private 欄位(300 行限制 + 無 Lombok)
- **給下一 session 的注意事項**(Phase 5 = SDK + Mock Adapter + Resilience + Source Health,治理規格 08):
  - SDK 還缺 Phase 5 的型別:ThreatSourceAdapter、SourceMetadata、FetchContext、FetchResult、RawThreatRecord(簽章在 08 §8.1)
  - 三個 mock adapter 的 SourceMetadata 應與 V4 種子值對齊(default_tlp/redistribution/reputation 見 V4__seed_sources.sql)
  - S6(config 不存憑證)在 adapter 設定層落實;S5 的 CredentialMasker 已在 domain/source(package-private)
  - IndicatorMergePolicy 已完整實作(非骨架):7.5 的加權/狀態判定含測試;Phase 7 接 pipeline 時
    **必須把所有涉及來源的 Reputation 傳入**(重建後缺席的 reputation 以中性值 50 計)
  - IndicatorSource.tags 為合併輸入、不隨 indicator_sources 持久化(聯集物化在 indicators.tags);
    per-source tags 如需保留是 schema 變更,屆時新增 migration
  - Indicator 建立走 NewIndicatorCommand + FingerprintStrategy(I2 由建構保證);id 由 IdGeneratorPort 產生
  - repository findVisible* 已內建 tenant+TLP+再散布過濾(TlpSpecifications);Phase 9 controller
    只需把「查無」映射 404,不得再自行過濾
  - AnonymousTenantFilter 目前對所有請求綁匿名;Phase 13 在其中加憑證解析後改綁 bindAuthenticated
  - 分頁 nextCursor 是 Cursor.encode() 的內部格式("epochMilli:uuid");Phase 9 的 CursorCodec 負責對外不透明包裝

---

## Phase 5 — SDK + Mock Adapter + 韌性 + 來源健康

- **狀態**:done(2026-08-25)
- **執行單**:`docs/spec/phases/phase-05.md`
- **Commit**:(見 git log,message `Phase 5: sdk + mock adapters + resilience + source health`)
- **完成判準結果**:全綠 —
  - `verify -Ptest-integration -Dtest='MockAdapterDeterminismTest,ResilienceTest,SourceHealthTest'`(逐字)✅
  - `test -Dtest=ArchitectureTest` ✅ 9/9
  - 另跑 `clean verify -Ptest-integration` 無過濾 ✅(sdk 13 + core 50 + adapters 24 + app 47;
    Spotless/Checkstyle/JaCoCo 全過)
- **交付物**:
  - sdk:ThreatSourceAdapter、SourceMetadata、FetchContext、FetchResult、RawThreatRecord(08 §8.1 逐字)
  - adapters/mock:三個確定性 mock(固定手寫資料集、零亂數、髒資料覆蓋 §7.3 七種 reason、
    SharedIocs 11 個跨來源重疊、AlienVault 以 STIX revoked=true 標撤回)+ MockFeed 分頁 helper
  - adapters/http:ResiliencePolicy(§8.5 預設值)、FetchResilience(retry+jitter/CB/bulkhead,
    per-sourceType 隔離)、ResilientThreatSourceAdapter、HttpFeedClients(timeout 契約)
  - core:AdapterRegistryPort、SourceHealthService(交易+事件發佈)、SourceSyncService
    (逐一處理、單一失敗不影響其他、分頁迴圈上限 1000);Source 聚合補 nextCursor/totalRecordsIngested
  - app:AdapterRegistry(§8.1 逐字 + implements port)、AdaptersConfig(bean + 韌性裝飾)、
    SpringEventPublisherAdapter(AFTER_COMMIT 信封發佈,提前自 Phase 6)
- **偏離事項 / ADR**:八項決策見 `docs/architecture/decisions/0003-phase5-sdk-adapter-decisions.md`
  (AdapterRegistryPort、adapters 零 Spring、固定資料集、STIX revoked、Source 聚合擴充、
  EventPublisherPort 提前、retry=4 attempts、config 空 Map)
- **給下一 session 的注意事項**(Phase 6 = Ingestion pipeline + 資料品質 + 排程 + 記憶體限流):
  - 判準的過濾式 `verify -Dtest=...` 之所以能過 JaCoCo,是因為 jacoco.exec 為 append 模式且
    無測試執行的 module 會跳過 check;**驗證真實狀態一律用 `clean verify -Ptest-integration`**
  - SourceSyncService 目前只抓取+記健康(records 只計數);Phase 6 改為餵進 IngestionPipeline
    並寫 source_sync(RUNNING→SUCCESS/PARTIAL/FAILURE)與 IngestionStarted/Completed 事件
  - mock 撤回記錄的映射約定:rawPayload["revoked"]==true → 該來源記錄 RETRACTED(ADR 0003 決策 4)
  - RateLimiterPort 目前是 Phase 4 的簡化簽章(tryAcquire);Phase 6 須依 10 §10.7 改為
    tryConsume(RateLimitKey, tokens) → RateLimitResult(含 X-RateLimit-* 標頭資料)
  - 整合測試 base 未關排程;Phase 6 加 @Scheduled 後記得在 AbstractPostgresIntegrationTest
    設 SCHEDULER_ENABLED=false,避免排程干擾測試
  - anonymous 限流數值:10 §10.6(60/min、1000/day);M1 存 properties 預設值,M2 移入 plans 表

---

## Phase 6 — Ingestion Pipeline + 資料品質 + 排程 + 記憶體限流

- **狀態**:done(2026-08-25)
- **執行單**:`docs/spec/phases/phase-06.md`
- **Commit**:(見 git log,message `Phase 6: ingestion pipeline + data quality + scheduling + in-memory rate limit`)
- **完成判準結果**:全綠 —
  - `verify -Ptest-integration -Dtest='NormalizationTest,RejectionRuleTest,IngestionEndToEndTest,RateLimitTest'`(逐字)✅
    (Normalization 21、RejectionRule 9(八種 reason 各 ≥1)、E2E 4、RateLimit 2)
  - 另跑 `clean verify -Ptest-integration` 無過濾 ✅(sdk 13 + core 112 + adapters 24 + app 54;
    含 ArchitectureTest 9/9、SecurityTest 5/5(新增條號 7);Spotless/Checkstyle/JaCoCo 全過)
- **交付物**:
  - core/domain:`normalization/`(IocNormalizer × 6 型別、IocNormalizers 清理/分派/推斷、
    ReservedIpRanges)、ThreatScorer + RuleBasedThreatScorer(§7.6 四權重)、
    Indicator.applyScore / mergeFrom(known reputations overload)
  - core/application/ingestion:IngestionStage + 9 個 stage(Parse→Validate→Normalize→Fingerprint→
    Deduplicate→Merge→Score→Persist→PublishEvent)、IngestionPipeline、IngestionBatchProcessor
    (一批一交易、單筆失敗記錄後 continue)、RejectionReason(8)、BatchState、SourceContext
  - core/application:SourceSyncService(fetch 交易外、滿批進 pipeline)+ SourceSyncRecorder
    (source_sync RUNNING→SUCCESS/PARTIAL/FAILURE、健康、Ingestion 事件)、IndicatorExpiryService;
    port:RejectionLogPort、SourceSyncLogPort、RateLimiterPort(§10.7 定形)+ RateLimitKey/Result
  - app:RejectionLogAdapter、SourceSyncLogAdapter(REQUIRES_NEW)、IndicatorRepository.findExpirable、
    IngestionPipelineConfig(顯式 List.of)、IngestionSchedulers(來源同步/IOC 過期 03:00/重試 15 分,
    SCHEDULER_ENABLED 總開關)、InMemoryRateLimiter(Bucket4j)+ RateLimitFilter(全端點、
    X-RateLimit-* 全回應、429+Retry-After、IPv6 /64)、CtipProperties/application.yml 擴充
    (scheduler crons、normalization.strip-www、data-quality.domain-allowlist、匿名限流配額)
- **偏離事項 / ADR**(`docs/architecture/decisions/0004-…`,十項):StixProjectionStage 留 Phase 8
  (pipeline 現為 9 stage)、pipeline bean 內聯建構(規格範例 10 參數違反自身 checkstyle)、
  需 canonical 值的拒絕規則移 NormalizeStage、QUOTA_EXCEEDED 以 BatchState 配額測試覆蓋、
  IDNA2003 代 IDNA2008(版本表無 ICU4J,回報)、redis 後端 M1 fallback 記憶體 + WARN、
  匿名限流 60/1000 property 預設(M2 移 plans 表)、source_sync start/finish 一次回寫、
  合併補 known reputations、RateLimitFilter 由 config 建立(ArchUnit 規則 5)
- **給下一 session 的注意事項**(Phase 7 = 去重/合併/指紋/評分,治理規格 07):
  - Phase 7 的大部分交付物已先行完成:IndicatorMergePolicy(Phase 4)、三步 valid_until 與
    indicator_sources UPSERT + report_count(Phase 4 domain、Phase 6 接線)、Sha256FingerprintStrategy
    (Phase 4)、ThreatScorer/RuleBasedThreatScorer(Phase 6)。Phase 7 的主要工作是其判準測試:
    IndicatorMergePolicyTest(已有,需補三來源+RETRACTED rep≥80 案例)、ValidityPeriodTest、
    ThreatScorerTest(需含「confidence 與 score 來源數定義一致」案例)、FingerprintTest;
    以及 hash_records 寫入(目前 pipeline 未寫 hash_records——FILE_HASH 的 HashRecord 物化待補)
  - 修 bug 的教訓:重建後 Indicator.reputations 為空,合併「必須」帶 known reputations
    (MergeStage.knownReputations);任何新的合併路徑(Phase 14 手動提交)都要走同一 overload
  - Boot 4 模組化:MockMvc 在 spring-boot-webmvc-test,@AutoConfigureMockMvc 套件
    org.springframework.boot.webmvc.test.autoconfigure(建議回寫 06 §6.3.6)
  - E2E 測試自行清理(snapshot 表 + sources 還原),新增整合測試若動 seed 資料請比照
  - python/sed 批量改碼注意:spotless 會折行,字串替換要 assert 有命中(本 phase 曾因此
    靜默漏改 call site)

---

## 規格修訂 — Phase 5–6 衝突回寫(2026-08-25,Phase 6 之後)

- **狀態**:done(使用者指示:把與原規格不一致之處註記回規格)
- **Commit**:(見 git log,message `Spec: write Phase 5-6 implementation feedback back into spec (§0.8)`)
- **內容**:ADR 0003/0004 的偏離與釐清已全數以「實作回饋修訂」引用區塊註記進對應主題檔,
  規格維持 single source of truth;總索引新增於 `00-master.md` **§0.8**(10 項 + 釐清段):
  - `08`:§8.1 AdapterRegistryPort 與 adapters 零 Spring;§8.2 StixProjectionStage 留 Phase 8、
    bean 範例參數數衝突、拒絕規則判定點;§8.3 固定資料集/revoked 約定/QUOTA_EXCEEDED 覆蓋方式;
    §8.5 retry 次數釐清(3 次重試 = maxAttempts 4)+ 裝配位置;§8.7 SOURCE_SYNC_CRON 掃描節奏
  - `07`:§7.2 IDNA2003 代 IDNA2008(版本表無 ICU4J);§7.3 QUOTA_EXCEEDED 擴充點、
    非預期錯誤映射 MALFORMED_VALUE、判定點;§7.5 重建後必須補入全部來源信譽(⚠️)
  - `10`:§10.7 redis 後端 M1 fallback + WARN、port 簽章定形、M1 維度範圍/匿名數值承載/actuator 排除
  - `04`:表 3 source_sync「append-only」精確語意(RUNNING 建立 → 終態回寫一次)
  - `06`:§6.3.6 第 5 條(spring-boot-webmvc-test / @AutoConfigureMockMvc 新套件)
  - `phases/phase-06.md`:交付物加註 ¹(9+1 stage 的 phase 歸屬)
- **驗證**:`dod.sh full M3-24` ✅(交叉引用全部指向存在的目標);純文件變更,無程式碼行為變更
- **給下一 session 的注意事項**:讀規格時 §0.7(Phase 2–3)與 §0.8(Phase 5–6)是實作回饋
  修訂的索引;07 §7.5 的「重建後補信譽」⚠️ 是 Phase 14 手動提交路徑必讀

---

## Phase 7 — 去重 · 合併 · 指紋 · 評分

- **狀態**:done(2026-08-26)
- **執行單**:`docs/spec/phases/phase-07.md`
- **Commit**:(見 git log,message `Phase 7: dedup + merge + fingerprint + scoring tests, hash_records materialization`)
- **完成判準結果**:全綠 —
  - `verify -Ptest-integration -Dtest='IndicatorMergePolicyTest,ValidityPeriodTest,ThreatScorerTest,FingerprintTest'`(逐字)✅ 25/25
    (MergePolicy 8 含三來源+RETRACTED rep≥80 → REVOKED;ValidityPeriod 7 含「未明示 → 型別預設 TTL」與
    「FILE_HASH → null」兩關鍵分支;ThreatScorer 4 含 confidence/score 來源數定義一致案例;Fingerprint 6 含 I2)
  - 另跑 `clean verify -Ptest-integration` 無過濾 ✅(sdk 13 + core 103 + adapters 24 + app 54;
    Spotless/Checkstyle/JaCoCo 全過)
- **交付物**(多數已於 Phase 4/6 先行完成,本 phase 補缺口與判準測試):
  - `Indicator.create` 物化平台去重指紋 `HashRecord`(SHA256,sourceId=null)→ 經既有 mapper
    reconcile 落 `hash_records`(缺口:Phase 6 前 pipeline 從未寫入此表)
  - 新測試:`ValidityPeriodTest`(§4.6 三步計算 + 值物件)、`ThreatScorerTest`(§7.6 四權重、
    上限 5 來源、30 天半衰期)、`FingerprintTest`(吸收原 Sha256FingerprintStrategyTest,加 I2 與
    HashRecord 物化案例);`IndicatorMergePolicyTest` 補信譽加權與可信任撤回案例
  - `IngestionEndToEndTest` 補兩條斷言:每筆新 indicator 恰一列平台 SHA256 hash_record 且
    digest == indicators.fingerprint;重同步不重複物化
- **偏離事項 / ADR**:無新 ADR。既有實作(IndicatorMergePolicy、三步 valid_until、UPSERT+report_count、
  Sha256FingerprintStrategy、RuleBasedThreatScorer)經判準測試驗證即符合規格,未改行為
- **給下一 session 的注意事項**(Phase 8 = STIX 正規化與匯出,治理規格 07 §7.8、04 表 8/9):
  - 一次完整 verify 曾見 `RateLimitTest.actuatorProbesAreNotRateLimited` 回 503——是宿主機睡眠造成的
    環境性 flake(該次輸出的 elapsed 高達 51596s),重跑即綠;非程式問題
  - 判準的過濾式 `verify -Dtest=...` 要在 full verify 之後**不 clean** 執行才能過 JaCoCo(append 模式)
  - StixProjectionStage 是 pipeline 第 8 stage(Score 與 Persist 之間),只改 IngestionPipelineConfig
    的那一個 `List.of`(08 §8.2 註 1)
  - stage 8 在 Persist **之前**執行:新 indicator 尚無 created_at/updated_at,STIX created/modified
    需以 ClockPort + UPSERT 保留 created 的方式近似,屆時寫 ADR
  - M1 匿名無 `stix:export`(10 §10.6 匿名 bundle 匯出 ✗):bundle 端點 M1 對匿名回 403 較符合
    安全優先,屆時寫 ADR;GET /api/v1/stix/{stixId} 為匿名可存取
  - StixSchemaValidationTest 需離線驗證:vendor OASIS cti-stix2-json-schemas 的 schemas/common/ 全部
    + sdos/indicator.json(draft 2020-12、相對 $ref);版本表無 JSON Schema 驗證器,需加 test-scope
    networknt json-schema-validator 並以 ADR 回報(比照 Phase 6 IDNA 前例)
  - `stix_objects`/`stix_relationships` entity(V7 表)已存在於 persistence 套件(package-private,尚無 repository)

---

## Phase 8 — STIX 2.1 正規化與匯出

- **狀態**:done(2026-08-26)
- **執行單**:`docs/spec/phases/phase-08.md`
- **Commit**:(見 git log,message `Phase 8: stix projection + tlp markings + pattern builder + bundle export`)
- **完成判準結果**:全綠 —
  - `verify -Ptest-integration -Dtest='StixTlpMarkingsTest,StixPatternTest,StixSchemaValidationTest'`(逐字)✅ 14/14
    (五個 marking UUID + extension-definition ID 字面斷言;六模板 + 四 hash 對應 + 跳脫;
    產出以 vendored OASIS STIX 2.1 JSON Schema 離線驗證,含 bundle)
  - 另跑 `clean verify -Ptest-integration` 無過濾 ✅(sdk 13 + core 123 + adapters 24 + app 61;
    含 ArchitectureTest 9/9、E2E 6/6;Spotless/Checkstyle/JaCoCo 全過)
- **交付物**:
  - core/domain/stix:StixTlpMarkings(五常數 + 擴充定義 ID,§7.8.4 原樣輸出)、StixPatternBuilder
    (六模板 + hashing-algorithm-ov 對應)、StixPatternEscaper、StixIndicatorProjector(§7.8.2 對照表,
    手寫 builder 唯一例外)、StixProjection
  - core/application:StixProjectionStage(stage 8,只建構)、IngestionBatchExecutor + StixProjectionWriter
    (交易提交後寫出,單筆失敗隔離)、StixQueryService、StixExportService(+Settings/Bundle/例外)、
    RedistributionFilter(§7.9 規則 2 的單點,規則 3 委派 I14);port:StixObjectPort;
    BatchOutcome/IngestionContext 擴充(承載投影)
  - app:StixObjectAdapter + StixObjectJpaRepository(stix_id UPSERT、content 序列化)、
    interfaces/rest/StixController(GET /{stixId} 匿名、GET /bundle 需 AUTHENTICATED)+ StixBundleWriter、
    StixConfig、CtipProperties.Stix(`STIX_EXPORT_MAX_OBJECTS`,預設 1000)、pipeline List.of 插入 stage 8
  - 測試:判準三類 + StixProjectionStage/Writer/Query/Export 單元測試;E2E 新增投影物化斷言
    (每 indicator 一列、UPSERT 冪等、created ≤ modified)與端點行為(匿名 GREEN → 404、bundle → 403)
- **偏離事項 / ADR**:八項決策見 `docs/architecture/decisions/0005-phase8-stix-projection-decisions.md`
  (投影建構/寫出分離、created/modified 近似、marking 常數供應不落表、bundle 匿名 403、
  匯出上限 property 承載、networknt json-schema-validator 1.5.6 test-scope + vendored schema(規則 6/17 回報)、
  external_references 需附 description、RedistributionFilter 位置)
- **給下一 session 的注意事項**(Phase 9 = REST API + DTO/Mapper + 錯誤處理 + cursor 分頁,治理規格 09):
  - **Boot 4 是 Jackson 3**:套件 `tools.jackson.*`(非 com.fasterxml)、例外 unchecked;
    只有 jackson-annotations 還是 2.x 座標(建議回寫 06 §6.3.6)
  - 背景/新 shell 跑 maven 一律 `./backend/mvnw -f backend/pom.xml ...`(cwd 不保證在 backend;
    本 phase 曾因此拿到空輸出差點誤判)
  - interfaces/rest 已建立(StixController 是第一個 controller);Phase 9 的 @RestControllerAdvice
    (ErrorResponse + traceId,M1-32)接手後,StixController 的 ResponseStatusException 與
    PLAN_LIMIT_EXCEEDED handler 應改走統一錯誤結構
  - RedistributionFilter 目前只有規則 3;Phase 9 的 DTO 映射要在同一類別補規則 4(attribution 欄位)
    與規則 5(DERIVED_ONLY 遮罩),不得散落 controller
  - StixQueryService 對 indicator 的查詢已內建可見度 + 再散布過濾;Phase 9 IOC 端點照同一模式
    (repository findVisible* + RedistributionFilter),「查無」一律 404
  - bundle 匯出上限計法 = marking + indicator 合計;Phase 14 把 StixExportSettings 換成 plans 查表
  - vendored schema 在 ctip-app/src/test/resources/stix-schemas/(BSD-3-Clause,README 記出處);
    升級 STIX schema 時整包替換

---

## 規格修訂 + README 回寫 — Phase 7–8 衝突回寫(2026-08-26,Phase 8 之後)

- **狀態**:done(使用者指示:確認異動回寫 README 與 06 §6.3.6 編譯地雷清單)
- **Commit**:(見 git log,message `Spec+README: write Phase 7-8 feedback into spec (§0.9), update README to Phase 8`)
- **內容**:
  - `06 §6.3.6` 補第 6 條:**Boot 4 的 Jackson 是 3.x**(`tools.jackson.*` 座標與套件、
    unchecked 例外;僅 jackson-annotations 維持 fasterxml 2.x 座標;IDE 自動 import 易誤引)
  - `08 §8.2` 補註 4:StixProjectionStage 只建構、寫出在批次交易提交後(FK 順序 + §7.8.6 失敗隔離)
  - `07 §7.8.2` 補引用區塊:created/modified 的近似計法;external_references 必附 description/url/external_id
    之一(OASIS schema 的 oneOf 會拒絕只有 source_name)
  - `00-master.md` 新增 **§0.9**(Phase 7–8 回寫索引;Phase 7 無規格偏離);結尾版本註記更新
  - `README.md`:開頭進度(en/zh)更新至 Phase 8、現況表(backend Phase 1–8、規格書三輪修訂、
    ADR 0001–0005)、快速開始補 STIX 端點示例、§0.7–§0.9 導覽連結
- **驗證**:`dod.sh full M3-24` ✅(曾抓到一個 anchor 筆誤:檢查器 slug 會丟棄底線,
  `stix_objects` 標題的 anchor 是 `#786-stixobjects-…`);純文件變更,無程式碼行為變更
- **給下一 session 的注意事項**:規格內部連結若指向含底線的 code span 標題,anchor 要去掉底線

---

## Phase 9 — REST API + DTO/Mapper + 錯誤處理 + cursor 分頁

- **狀態**:done(2026-08-26)
- **執行單**:`docs/spec/phases/phase-09.md`
- **Commit**:(見 git log,message `Phase 9: rest api + dto/mapper + error handling + cursor pagination`)
- **完成判準結果**:全綠 —
  - `verify -Ptest-integration -Dtest='CursorPaginationIntegrationTest,IocSearchIntegrationTest,ErrorResponseTest,SecurityTest'`(逐字)✅ 28/28
    (分頁測試連續翻頁至最後一頁、與 DB 同條件 id 集合比對無重複無遺漏;SecurityTest 含條號 1、2、3、7、9,
    條號 3 以參數化涵蓋 M1 全部 tenant-scoped 讀取端點)
  - `test -Dtest=ArchitectureTest`(逐字)✅ 9/9
  - 另跑 `clean verify -Ptest-integration` 無過濾 ✅(sdk 13 + core 135 + adapters 24 + app 84 = 256;
    Spotless/Checkstyle/JaCoCo 全過)
- **交付物**:
  - Controller:System(health/version)、Ioc(list/detail/sources/search/lookup)、Stats(summary/sources)、
    Source(list/{id}/{id}/status);StixController 改走統一錯誤
  - core/application:IndicatorQueryService(含 lookup 正規化)、StatsQueryService、SourceQueryService、
    IndicatorFilter、LookupResult;RedistributionFilter 定形於 application/indicator(規則 3/4/5);
    port:StatsPort、IndicatorRepository 擴充(filter/offset/visibleByIdentity)、SearchPort 擴充、
    SourceRepository.findAll;SourceSnapshot 補 homepageUrl(attribution 規則 4)
  - app:CursorCodec(base64url {"ls","id"},INVALID_CURSOR)、dto/(全 record,依資源分子套件)、
    interfaces/rest/mapper(IocDtoMapper/SourceDtoMapper)、IocResponseAssembler(輸出過濾第 4–5 步)、
    ApiExceptionHandler + ErrorCode(16)+ ErrorResponse(traceId)、TraceIdFilter(W3C traceparent/MDC)、
    SearchAdapter(PostgreSQL LIKE + 跳脫)、StatsAdapter(重用 TlpSpecifications 的 criteria 統計)、
    IndicatorFilterSpecs(status 預設排除 EXPIRED)、RateLimitFilter 429 改統一錯誤結構、
    CtipProperties.Api(匿名配額 property)
- **偏離事項 / ADR**(`docs/architecture/decisions/0006-phase9-rest-api-decisions.md`,八項):
  - ⚠️ **安全性缺陷修正**:§7.9「viewer == owner 免過濾」照字面會使匿名(綁 public tenant)成為
    全部公開情資的「擁有租戶」,再散布過濾對公開輸出完全失效。修正:豁免僅限非 public 租戶,
    domain I14 / TlpSpecifications / RedistributionFilter 三處同一規則。**建議回寫 07 §7.9**
  - 其餘:規則 5 遮罩粒度、attribution homepage(SourceSnapshot 擴充)、匿名配額 property、
    traceId 實作、lookup 未命中語意、offset 實作、IocListParams record 繫結
- **給下一 session 的注意事項**(Phase 10 = OpenAPI/Swagger,治理規格 09 §9.6):
  - springdoc-openapi **3.1.0**(3.x 才相容 Boot 4,不得 2.x);`-parameters` 旗標已在編譯設定?
    請驗證(springdoc 參數名推導需要)
  - 產生的 openapi.json 需 commit 至 docs/api/openapi.json + CI artifact + 破壞性變更比對(§9.6)
  - prod 預設關 Swagger(SWAGGER_ENABLED 可開但須加保護);mvp/dev/staging 開
  - 每個公開 API 要 summary/description/schemas/錯誤回應/認證需求/至少一個範例——
    現有 controller 無 openapi 註解,Phase 10 補
  - MockMvc 測試共用 default context 時注意匿名限流 bucket:各測試類用獨立 client IP
    (setRemoteAddr)隔離;SecurityTest 條號 7 會吃滿 127.0.0.1 的 60/min
  - 背景/新 shell 跑 maven:JAVA_HOME 可能沒設(會撿到系統 JDK 20 → `-proc:full` 炸),
    一律 `export JAVA_HOME=/usr/local/opt/openjdk` 且 cwd 用絕對路徑

---

## 規格修訂 + README 回寫 — Phase 9 衝突回寫(2026-08-26,Phase 9 之後)

- **狀態**:done(使用者指示:§7.9 修正回寫規格 + README 更新;並確立每 phase 收尾必含
  「全部驗證無誤 + 規格回寫 + README 更新」三件事)
- **Commit**:(見 git log,message `Spec+README: write Phase 9 §7.9 security fix into spec (§0.10), update README to Phase 9`)
- **內容**:
  - `07 §7.9`:作用域修正的偽碼加上 `!owner.isPublic()` 條件,並以引用區塊說明安全性缺陷
    (匿名綁 public tenant,原字面會使再散布過濾對公開輸出失效)與三處落實位置
  - `00-master.md` 新增 **§0.10**(Phase 9 回寫索引);結尾版本註記更新為 §0.7–§0.10
  - `README.md`:進度更新至 Phase 9(en/zh)、現況表(backend 256 tests、四輪修訂、ADR 0001–0006)、
    快速開始補 REST API 端點示例列
- **驗證**:`dod.sh full M3-24` ✅;純文件變更,無程式碼行為變更
- **給下一 session 的注意事項**:每個 phase 收尾固定三件事(已存入使用者層記憶):
  ① 判準逐字 + 無過濾 `clean verify -Ptest-integration` 全綠;② 規格衝突回寫
  (主題檔引用區塊 + §0.x 索引 + ADR + M3-24);③ README 進度/現況/示例同步

---

## Phase 10 — OpenAPI / Swagger

- **狀態**:done(2026-08-26)
- **執行單**:`docs/spec/phases/phase-10.md`
- **Commit**:(見 git log,message `Phase 10: openapi/swagger + completeness test + committed openapi.json + ci check`)
- **完成判準結果**:全綠 —
  - `verify -Ptest-integration -Dtest=OpenApiCompletenessTest`(逐字)✅ 3/3
  - `./environment/scripts/up.sh mvp` ✅ 三容器 healthy;
    `curl /swagger-ui/index.html` ✅;`curl /v3/api-docs | jq -e '.paths | length > 0'` ✅ true
  - 另跑 `clean verify -Ptest-integration` 無過濾 ✅(sdk 13 + core 135 + adapters 24 + app 87 = 259;
    Spotless/Checkstyle/JaCoCo 全過);openapi.json 跨執行位元一致(確定性驗證)
- **交付物**:
  - springdoc-openapi-starter-webmvc-ui **3.1.0**(parent property;SWAGGER_ENABLED 開關與四環境
    樣板 Phase 2–3 已就位,prod=false);OpenApiConfig(info + OperationCustomizer 統一掛 429/500)
  - `interfaces/rest/openapi/{System,Ioc,Stats,Source,Stix}Api` 文件介面(summary/description/
    schemas/錯誤/@SecurityRequirements/每端點至少一個範例),controller implements 繼承註解
  - `OpenApiCompletenessTest`:逐端點檢查必要欄位(認證需求以描述含「認證」驗證)+
    產出 canonical `docs/api/openapi.json`(鍵排序,§9.6 不得手改)
  - `.github/workflows/openapi-check.yml`(產生 → git diff 比對 committed → artifact →
    對 base 跑破壞性檢查);`environment/scripts/openapi-breaking-check.py`(移除端點/必填/改型別,
    含煙霧測試驗證)
  - **up.sh 預熱守衛修正**:離線 go-offline 探測取代「volume 為空」檢查(相依漂移自動重預熱;
    已端到端實測:偵測 → 重預熱 → healthy → 判準過)
- **偏離事項 / ADR**(`docs/architecture/decisions/0007-phase10-openapi-decisions.md`,六項):
  openapi.json 由判準測試產生(版本表無 springdoc maven plugin)、破壞性比對自寫 python
  (版本表無 oasdiff)、文件介面模式(300 行限制)、M1 認證需求雙軌表達、429/500 統一掛載、
  up.sh 守衛修正(已回寫 05 §5.10 + §0.11)
- **給下一 session 的注意事項**(Phase 11 = React 前端骨架 + 型別產生 + 版面,治理規格 12):
  - 前端型別由 `docs/api/openapi.json` 產生(12 §12.x;openapi-typescript?依 06 §6.2 版本表);
    F3 依賴規則與 `api:check` script(比對產生型別 drift)在此 phase 落實
  - springdoc 註解掛在 openapi/*Api 介面;新端點記得同步文件介面,否則 OpenApiCompletenessTest 會擋
  - @Content 一律要顯式 `mediaType = "application/json"`,否則 springdoc 產出落在 `*/*`
  - mvp stack 目前在本機執行中(swagger UI 可直接開);down.sh mvp 可停
  - openapi.json 是 committed 產物:改了任何端點/DTO 註解後要重跑 OpenApiCompletenessTest
    再 commit,否則 CI drift check 會 fail

---

## Phase 11 — React 前端骨架 + 型別產生 + 版面

- **狀態**:done(2026-08-26)
- **執行單**:`docs/spec/phases/phase-11.md`
- **Commit**:(見 git log,message `Phase 11: react frontend skeleton + type generation + layout`)
- **完成判準結果**:全綠 —
  - `npx tsc --noEmit` ✅、`npx eslint . --max-warnings 0` ✅(F1/F2/F4 zones 生效範圍內)
  - `npm run api:check` ✅(generated 與 openapi.json 一致、已 tracked)
  - `npm run build` ✅;`npm run test -- --coverage` ✅ 54/54,components 行覆蓋 100%(門檻 70)
  - `npm run format:check` ✅;瀏覽器實測亮/深主題與 mvp 容器 HMR ✅
- **交付物**:
  - deps 依 06 §6.2.3 一次裝齊:react-router 8.3.0(v7 血統單一套件,非 react-router-dom)、
    TanStack Query 5.101.4 / Virtual 3.13、RTK 2.12 + react-redux 9、Tailwind 4.3.3 +
    @tailwindcss/vite(CSS-first,無 tailwind.config.js)、RHF 7 + Zod 4.4.3 + resolvers 5、
    lucide-react 1.34、MSW 2.15、openapi-typescript 7.13、RTL 16 + jsdom + coverage-v8
  - `src/api/generated/schema.d.ts`(api:generate 產生、進版控)+ `client.ts`(手寫 typed
    fetch wrapper:ApiError/token provider/物件 query 攤平)+ `api:generate`/`api:check` scripts
  - stores/ 四 slice(auth/ui/toast/filterDraft)+ makeStore + uiSlice→localStorage(zod 驗證載回)
  - components/:ui 六件(手寫 shadcn 等價)、StateViews 四態、TlpBadge(五級+unknown fallback)、
    VirtualTable(TanStack Virtual)、Toaster;layouts/AppLayout(響應式+主題切換);
    routes/(data router、RequireAuth/RequirePermission 完整行為、RootErrorBoundary);
    app/(providers、queryClient、ThemeApplier);pages/ 三頁殼 + NotFound
  - Tailwind v4 @theme:「訊號台」色系(oklch、TLP 五色 token、座標紙紋理、
    Archivo + IBM Plex Mono 自託管);index.html 防 FOUC inline script
  - MSW(src/test/,handlers 以 satisfies 由 generated 型別驅動)
- **偏離事項 / ADR**:八項決策見 `docs/architecture/decisions/0008-phase11-frontend-skeleton-decisions.md`
  (shadcn 手寫等價不跑 CLI、版本表未列必要配套(規則 6/17 回報)、手寫 client 不加 openapi-fetch、
  springdoc `params` 包裝缺陷+client 攤平因應、co-located 測試慣例、dark class 策略+FOUC script、
  守衛不掛載但行為完整、不引入 @/ alias)
- **給下一 session 的注意事項**(Phase 12 = IOC 三頁 + PostgreSQL 搜尋):
  - **springdoc 缺 @ParameterObject**:openapi 的 GET /iocs query 被包成單一 params 物件,
    Phase 12 後端修正後要重跑 OpenApiCompletenessTest、commit openapi.json、前端重 api:generate
  - **up.sh 前端預熱守衛只驗 vite 存在**,偵測不到相依漂移(同 Phase 10 backend 守衛問題);
    本次已手動 `compose run frontend npm ci` 重預熱。Phase 12 動 package.json(recharts)後同樣要重預熱,
    建議順手把守衛改為 lockfile 比對
  - VirtualTable 在 jsdom 測試需 stub HTMLElement offsetWidth/offsetHeight(TanStack Virtual 以
    offset* 量測,getBoundingClientRect 沒用)
  - PageResponse.items 是 untyped:用 client.ts 的 `PageOf<T>` 窄化 helper
  - mvp stack 執行中,frontend 容器 HMR 服務 host 綁定掛載的原始碼;瀏覽器開 http://localhost:5173

---

## Phase 12 — IOC 頁面 + PostgreSQL 搜尋(M1 收官)

- **狀態**:done(2026-08-26);**M1 里程碑閘門 `dod.sh mvp` 38/38 全綠**
- **執行單**:`docs/spec/phases/phase-12.md`
- **Commit**:(見 git log,message `Phase 12: ioc pages + postgres full-field search + dod gate fixes`)
- **完成判準結果**:全綠 —
  - `cd frontend && npm run test -- IocSearchPage IocDetailPage DashboardPage` ✅ 16/16(四種狀態全覆蓋)
  - `./backend/mvnw -f backend/pom.xml clean verify -Ptest-integration` ✅ 全模組 BUILD SUCCESS
    (app 90 tests 含新 StatsIntegrationTest 3、IocSearch 擴充至 9)
  - `./environment/scripts/dod.sh mvp` ✅ **38/38**(含 M1-35 四狀態、M1-36 HMR、M1-37 reload)
  - 前端另跑 coverage ✅ 70 tests、行覆蓋 90.97%(門檻 70);瀏覽器實測三頁 + 深色模式 + 響應式
- **交付物**:
  - 後端:`PostgresSearchAdapter`(改名自 SearchAdapter);**§13.7 搜尋欄位補齊**(使用者決策)——
    IndicatorFilter + IntRange/TimeRange、IndicatorFilterSpecs(tags `@>` GIN、sourceId EXISTS、
    confidence/score/lastSeen 區間)、IocListParams/SearchRequest 擴充、`PostgresFunctionContributor`
    (text[] cast 的自訂 HQL 函式);**WebCorsConfig**(CORS 屬性從 Phase 3 起一直沒接到 MVC,
    前端首次跨源即發現);openapi 修正(@ParameterObject 攤平、三個 List 端點 @ArraySchema)+
    重產 openapi.json(破壞性檢查 PASS);StatsIntegrationTest(summary 匿名口徑/trend 補 0/UTC 日界/
    sources 併表)、IocSearchIntegrationTest 補 tags/score/時間區間/sourceId/confidence 案例
  - 前端:features/ioc(useIocSearch——條件與 cursor 存 URL、useIocDetail/useIocSources、
    IocFilterBar(草稿走 filterDraftSlice)、IocTable(VirtualTable)、CursorPager、IocSummaryCard、
    SourceAttributionList)、features/stix(StixJsonViewer + useStixObject)、
    pages 三頁(Dashboard 統計卡 + Recharts 趨勢 + 型別分布 + 來源健康)、共用 hooks/useStats、
    ui/select、MSW handlers 擴充、recharts ~3.10
  - environment:up.sh 前端預熱守衛改 lockfile 戳記;dod.sh 兩處檢查器缺陷修正
    (M1-37 pipefail+grep -q 假失敗;M1-14/33 前自我修復——gate 的 host 重建會撞死共享
    target/classes 的 dev 容器 DevTools)
- **偏離事項 / ADR**:八項見 `docs/architecture/decisions/0009-phase12-search-pages-decisions.md`;
  規格回寫 §0.12(13 §13.7、05 §5.7/§5.10、09 §9.6),M3-24 ✅
- **給下一 session 的注意事項**(Phase 13 = 認證/RBAC/API Key/租戶隔離,治理規格 10,M2 開始):
  - **M2 前置**:dod.sh mvp 38/38 已過;M2-01 是「DoD-MVP 全部仍通過(回歸)」,改動後端時記得回歸
  - AnonymousTenantFilter 是憑證解析的掛載點(Phase 4 註記);RequireAuth/RequirePermission 前端守衛
    已完整實作待掛載(ADR 0008 §7);authSlice.sessionEstablished 已就緒
  - dev 容器與 host 共享 backend/*/target:host 建置打死容器 app 的問題**已於 ADR 0010 根治**
    (DevTools 改 trigger file 觸發,見文末「環境維護」段);熱替換一律走 reload.sh
  - Hibernate `String[]`→varchar[] 綁定與 text[] 欄位的 `@>` 衝突已由 PostgresFunctionContributor
    解決;之後對 threats.aliases(V25,也是 GIN)做包含查詢時重用同一函式模式
  - openapi.json 為 committed 產物:後端 DTO/參數改動 → OpenApiCompletenessTest 重產 → commit →
    前端 `npm run api:generate` 並 git add(api:check 的 diff 是對 index 比)
  - 前端加 npm 相依後跑 `./environment/scripts/up.sh mvp` 會自動重預熱(lockfile 戳記守衛)

---

## 環境維護 — DevTools trigger file 根治(2026-08-26,M1 閘門之後)

- **狀態**:done(使用者指示:根治「host 建置打死 dev 容器 app」問題)
- **Commit**:(見 git log,message `Env: devtools trigger-file — host builds no longer restart dev container (ADR 0010)`)
- **內容**:DevTools restart 改由 trigger file 觸發(`spring.devtools.restart.trigger-file`)——
  mvp/dev yml 設定、`backend/ctip-app/.devtools/.reloadtrigger`(進版控,經
  `additionalClasspathElements` 掛進 spring-boot:run classpath)、reload.sh 編譯成功後 touch。
  host 端 `mvnw verify`/`clean` 不再引發容器熱重啟,半寫入 classpath 的死亡窗口消失。
- **驗證**:M1-37 ✅(reload <10s);host compile(重寫全部共享 classes)與 host clean
  (刪除全部 classes)皆 **0 重啟**、app 保持 UP、swagger 200
- **地雷**:Boot 4 plugin 將 run mojo 的 `directories` 更名 `additionalClasspathElements`,
  舊名**靜默無效**(已回寫 06 §6.3.6 第 7 條);ADR 0010、05 §5.11 引用區塊、§0.12 第 7 列
- **給下一 session 的注意事項**:reload 契約 = reload.sh(編譯 + touch trigger);
  直接在容器內手動 mvn compile 不會再觸發重啟,要生效請一律走 reload.sh

---

## M1 總複查(2026-08-27,M1 閘門之後、Phase 13 之前)

- **狀態**:done(使用者指示:重新 review Phase 1–12,確認實作與 README)
- **Commit**:(見 git log,message `M1 review: ...` 與 `Spec+README+docs: ...`)
- **方法**:四個獨立視角平行複查(安全/TLP、攝取/合併、STIX/API、前端/環境),
  每項發現由主 session 讀碼驗證後才修;修正與測試同時產生(規則 15)
- **修正**(細節與取捨見 `docs/architecture/decisions/0011-m1-review-fixes.md`、規格索引 §0.13):
  1. **[高] `IndicatorSource.mergeReport` 沖掉撤回**:同來源 UPSERT 無條件設回 ACTIVE,
     重同步會使 RETRACTED 復活、REVOKED 翻回 ACTIVE(違反 §7.5 規則 1)。已定形 UPSERT
     status 規則並回寫 07 §7.5;E2E Order 5 補撤回持續斷言
  2. **[中] §7.7 TLP:RED 攝取拒收守門缺漏**(規格明文要求):補在 ValidateStage + 行為測試
  3. **[中] ADR 0006 query 層規則無整合測試鎖**:SecurityTest 補 public+CLEAR+INTERNAL_ONLY
     fixture(唯一能判別 `ownerOrRedistributable` 的樣本)
  4. **[中] cursor 內部編碼毫秒截斷**:微秒級 last_seen 會使 keyset 翻頁漏列;改 `epochSecond.nano:id`
  5. **[中] up.sh 印 127.0.0.1:5173 但 CORS 白名單只有 localhost**:mvp/dev 樣板與本機 .env.mvp
     補列兩個 origin;`WebCorsConfig` 解析補 trim
  6. **[中] trend 統計時區錯位**:`date_trunc` 依 session TimeZone 切日、Java 端以 UTC 對桶,
     非 UTC 環境(本機 Asia/Taipei)會漏計——CI(UTC)測不到的時刻依賴 flake;改
     `to_char(timezone('UTC', …))` 分組(全量驗證時實際踩中而確認)
  7. 其餘:raw/normalized 超過 VARCHAR(2048) 的 flush 期整批 rollback 守門、限流三項
     hardening(minute 超限不扣 day、429 path 跳脫、/actuator 豁免防 `..`)、空 bundle 省略
     objects、allowlist 項目正規化、前端 homepage scheme 白名單、詳情頁 sources 錯誤呈現、
     StatsIntegrationTest 期望值 SQL 補再散布條件(新 SecurityTest fixture 暴露其缺漏)
- **文件同步**:root README(§0.7–§0.13/七輪、ADR 0001–0011、deployment 文件標注 M3 產出)、
  docs/spec/README(移除「尚未實作」、補 M1 完成狀態、行數表更新)、environment/README
  (hot reload 改 ADR 0010 trigger-file 敘述、補 openapi-breaking-check.py 與 openapi-check.yml)、
  ADR 0001 標題三項→五項、07 §7.8.4 TLP 1.0 誤述修正、本檔 DevTools 過時注意事項消除
- **驗證**:backend `clean verify -Ptest-integration` 全綠、frontend 五項全綠、`dod.sh mvp` 38/38
  (結果見本節 commit 當下輸出)
- **給下一 session 的注意事項(M2 前置,依風險排序)**:
  - **M2-25 前必修**:staging/prod 前端 `VITE_API_URL` 進不了 bundle(Vite 是 build 期變數,
    compose 只在執行期塞給 nginx;§5.6 規格層缺陷)——需規格決策(build args 或 runtime
    env.js + nginx `/api` 反代)並回寫 05;M1 的 mvp/dev 不受影響(Vite dev server 執行期讀 env)
  - **Phase 14 手動提交前**:`/stats/sources` 筆數不經可見度過濾(INTERNAL_ONLY 提交量會即時
    公開);`sourceId` 查詢參數側信道(可探測被遮罩來源與 IOC 的關聯,需釐清 §7.9×§13.7)
  - **M2 真實 adapter 前**:`MAX_PAGES_PER_RUN` 截斷後 cursor/since 語意矛盾(截斷後剩餘記錄
    永久漏收)——需定義 FetchContext 的 cursor/since 優先序契約
  - **Phase 17 Redis 限流時**:`InMemoryRateLimiter` bucket map 無逐出一併處理
  - 低風險已知縫隙(不修的理由見 ADR 0011 延後表):filter 例外回 Boot 預設錯誤結構、
    STIX name 截斷 surrogate pair、FilterBar back/forward 草稿不同步、IDNA2003 ß→ss 合併風險

---

## Phase 13 — 認證 · RBAC · API Key · 租戶隔離強制(M2 開始)

- **狀態**:done(2026-08-27)
- **執行單**:`docs/spec/phases/phase-13.md`
- **Commit**:(見 git log,message `Phase 13: authentication + rbac + api key + tenant isolation`)
- **完成判準結果**:全綠 —
  - `verify -Ptest-integration -Dtest='AuthFlowIntegrationTest,RefreshTokenRotationTest,RbacMatrixTest,ApiKeyTest,CrossTenantIsolationTest,SecurityTest'`(逐字)✅ 138/138
    (RbacMatrix 101 = **95 格矩陣參數化** + 6;SecurityTest 15,條號 1–9 全數具名;
     CrossTenantIsolation 以參數化涵蓋每一個 tenant-scoped 端點,全部 404)
  - 另跑 `clean verify -Ptest-integration` 無過濾 ✅(sdk 13 + core 207 + adapters 24 + app 243 = 487;
    Spotless/Checkstyle/JaCoCo 全過,含 domain.user / domain.identity ≥ 0.85)
  - frontend ✅ `tsc --noEmit`、`eslint --max-warnings 0`、`api:check`、`build`、`test`(97 tests,行覆蓋 90%)、`format:check`
  - `dod.sh mvp` **38/38 回歸**(M2-01)
- **交付物**:
  - migration:`V20`(users/roles/permissions/role_permissions/tenant_users)、`V21`(refresh_tokens/api_keys)、
    `V24`(5 角色 + **19** 權限 + 矩陣展開,冪等)。**版本號依 04 §4.7 區段設計,V8–V19 保留給 M1,跳號是刻意的**
  - core/domain:`user/`(User U1–U7、RefreshToken、EmailAddress/PasswordHash/RawPassword/TokenHash/
    TokenFamilyId、輪替命令與結果)、`identity/`(ApiKey K1–K7、KeyPrefix/KeyHash/ScopeSet/ApiKeyFormat)、
    `shared/Sha256Digest`;事件 `UserRegistered`/`TokenReuseDetected`/`ApiKeyCreated`/`ApiKeyRevoked`/`TenantCreated`
  - core/application:`identity/`(AuthService 門面 + UserRegistrar/LoginAuthenticator/RefreshTokenRotator/
    SessionIssuer/IdentityResolver/TenantProvisioner/RefreshTokenFactory/ApiKeyService/ApiKeyFactory/
    ApiKeyAuthenticator)、`rbac/RoleCode`;port:UserRepository、RefreshTokenRepository、ApiKeyRepository、
    RolePermissionRepository、TenantMembershipRepository、PasswordHasherPort、AccessTokenPort、SecureTokenGeneratorPort
  - app/infrastructure:7 個 entity + adapter + 手寫 MapStruct mapper;security:`AuthenticatedIdentity` 綁定、
    `CtipAuthenticationFilter`(Bearer / X-API-Key / 匿名同一條 chain)、`CtipPermissionEvaluator`、
    `JwtAccessTokenAdapter`(HS256,claims 僅 sub/tid/roles/perms/iat/exp/jti)、`BCryptPasswordHasher`(cost 12)、
    `SecureRandomTokenGenerator`;`web/FilterErrorWriter`(filter 內統一錯誤結構,RateLimitFilter 一併改用)
  - app/interfaces:`AuthController`、`ApiKeyController`(全部 `@PreAuthorize`)、dto、`AuthApi`/`ApiKeyApi` 文件介面;
    `SecurityConfig`(`@EnableMethodSecurity`、stateless、CORS 加 DELETE);openapi.json 重產(破壞性檢查 PASS)
  - frontend:`features/auth`(登入/註冊 + CredentialsForm)、`features/apikey`(建立表單/清單/一次性金鑰提示)、
    `pages/{Login,Register,ApiKeys}Page`、`hooks/useSession`(登出;layouts 不得 import features)、
    路由掛 `RequireAuth`/`RequirePermission`、`client.ts` 401 自動輪替(並行共用單次輪替)+ `apiDelete`
- **偏離事項 / ADR**:14 項見 `docs/architecture/decisions/0012-phase13-auth-rbac-decisions.md`;
  規格回寫 §0.14(10 §10.3/§10.5、04 表 12/16/§4.7、09 §9.1、01 §1.11)
- **本 phase 抓到的實質缺陷(值得記住)**:
  1. **`@Transactional` 內「寫入失敗紀錄 → 丟例外」會被 rollback**——登入失敗計數(U7)與重用偵測的
     family 全撤(U5)因此完全失效。改為失敗以回傳值交出、交易在協作者內提交、例外在交易外丟。
     **Phase 14 的配額扣減若也要在拒絕時留下紀錄,適用同一規則。**
  2. **API key prefix 規格自相矛盾**:`ctip_<env>_` 前 8 碼是常數,唯一約束必撞。改取隨機段前 8 碼。
  3. **`.env.*.example` 的 `JWT_SECRET=CHANGE_ME_MIN_32_BYTES` 自己只有 22 bytes**——M1 沒有任何東西
     消費它所以一直沒事;HS256 上線後,照 README 快速開始複製樣板的全新環境**直接啟動失敗**。
     樣板改為 `CHANGE_ME_MIN_32_BYTES_REPLACE_THIS`(35 bytes,仍含 `CHANGE_ME` 故 prod 守衛不受影響),
     並加 `EnvTemplateSecretTest` 逐檔鎖住(ADR 0012 決策 15)
- **收尾複查抓到的 4 個安全/完整性缺陷(全部已修 + 回歸測試,ADR 0012 決策 16–19)**:
  1. **認證失敗完全繞過限流**(我在本 phase 引入的迴歸):認證 filter 憑證無效時直接寫 401 並中止 chain,
     而 `RateLimitFilter` 排在 security chain 之後(Boot 對 Filter bean 預設 `LOWEST_PRECEDENCE`)。
     實測 75 次無效 token → **零個 429**。改以 `FilterRegistrationBean` 排在
     `SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1`。
     **Phase 14 加維度 1–3 時,IP 維度必須留在認證之前**(已回寫 10 §10.7)
  2. **登入回應時間洩漏帳號是否存在**:查無帳號時略過 BCrypt,實測 7ms vs 440ms(60 倍),
     錯誤訊息一致完全沒用。改為一律比對(帳號不存在時比 `PasswordHasherPort.dummyHash()`),鎖定路徑亦然
  3. **輪替的消耗舊枚與持久化新枚不同交易**:中間失敗會讓舊枚已作廢、新枚不存在 → 無聲登出且 family 斷掉。
     新枚改在 rotator 同一交易內持久化;`SessionIssuer` 拆為 `issueNewSession` / `resume`
  4. **API key 雜湊用 `String.equals`**(會短路)→ 改 `MessageDigest.isEqual`
- **環境維運(實測踩到)**:
  - **新增 Maven 相依後 dev 容器必須重建**:`spring-boot:run` 的 classpath 在啟動時算好,
    DevTools restart 只換 app classes → `NoClassDefFoundError`。用
    `docker compose --env-file environment/.env.mvp -f environment/docker-compose.yml up -d --force-recreate backend`
  - `up.sh` 對「已在執行但 unhealthy」的容器不會重建,只會等 healthcheck 逾時 300s 後報錯
  - host 端 `mvnw clean` 會刪掉與 dev 容器共用的 `target/classes`,執行中的 app 隨即死亡
    (ADR 0010 只擋掉「重啟」,擋不掉 class 被刪);跑完 gate 前後都要確認容器 healthy
- **未處理、但已確認「現在不該處理」的一項(§10.7 強制)**:
  `server.forward-headers-strategy=framework` 全專案沒有設定。查證後結論是**現階段刻意不設**:
  nginx 只服務前端靜態檔、**沒有反代 `/api`**,後端在四個環境都直接對外,因此 `getRemoteAddr()`
  取到的就是真實 client IP,匿名限流是準的;此時若設 framework,前面沒有可信代理,
  **任何人都能偽造 `X-Forwarded-For` 換一個全新配額**,等於自己開洞。
  ⚠️ **但 M2-25 的 VITE_API_URL 修法若採「nginx 反代 `/api`」,就會在後端前面放上代理** ——
  屆時所有請求的 remote addr 都變成 nginx 容器 IP,**整個平台共用同一個限流桶**(單一使用者即可
  耗盡全部配額)。那時必須同時設 `forward-headers-strategy=framework` **並**以
  `server.tomcat.remoteip.internal-proxies` 限定信任來源,兩者缺一都是洞。
  §10.7 另要求「若無法確定真實 client IP 須在 `docs/deployment/` 記載」——該目錄是 M3 交付物,目前為空。
- **給下一 session 的注意事項(Phase 14 = Plan/Subscription/配額 + IOC 寫入端點)**:
  - 配額值一律讀 `plans` 表;現有四處硬綁 `CtipProperties` 要改:`IocController.clampLimit()`、
    `api.maxBatchLookup()`、`StixExportSettings`、`RateLimitConfig` 的匿名 60/1000
  - `ApiKeyService.countActive(tenantId)` 已備妥,供 `plans.max_api_keys` 檢查
  - `AuthenticatedIdentity` 帶 `apiKeyId`,Phase 14 的限流維度 1–3 直接取用;`RateLimitKey.scope` 是自由字串
  - `@PreAuthorize("hasAuthority('ioc:submit')")` 即可;**不得在 controller 寫 role 判斷**
  - 匿名是「具 ANONYMOUS 角色權限的正當身分」,權限不足回 **403**(非 401);過期 token 才是 401 TOKEN_EXPIRED
  - ArchUnit 規則 9 實際禁止 domain 出現**任何名為 `now` 的方法**(不只 Instant.now);命名避開
  - openapi operationId 由方法名決定,重名會產生 `list_1`/`list_2` 之類的不穩定編號——新端點方法名取唯一名
  - 整合測試身分建立用 `com.ctip.support.TestIdentities`(走真實 register/login);
    fixture 用 `IndicatorFixtures` / `SecurityFixtures`;`SecurityTest` 已達 300 行上限,新條號請先瘦身
  - 前端加新 feature 前確認 `eslint.config.js` 的 feature 名單已含該名稱(auth/apikey/subscription 皆已列)

---

## Phase 13 收尾稽核 — 逐端點對照 §10.3 + 架構 / 資安複查

- **狀態**:done(2026-08-28)。使用者指示:逐端點對照 §10.3 矩陣稽核、以資深架構師與資安專家
  視角複查、找到問題修復並回寫 README。**不是新 phase**,是 Phase 13 的收尾。
- **Commit**:`8a62e31`(`Phase 13 audit: endpoint-level authorization + credential revocation fixes`)
- **完成判準結果**:全綠 —
  - Phase 13 判準逐字 ✅ **151/151**(原 138;RbacMatrix 95 格 → **105 格** + CrossTenantIsolation 加認證方式軸 4 → 8)
  - `clean verify -Ptest-integration` 無過濾 ✅ **532 tests**(sdk 13 + core 214 + adapters 24 + app 281;
    Spotless / Checkstyle / JaCoCo 全過)
  - frontend ✅ `tsc --noEmit`、`eslint --max-warnings 0`、`api:check`、`build`、`test`(97)、`format:check`
  - `openapi.json` 重產(password `maxLength` 256 → 72),破壞性檢查 **PASS**
  - mvp stack 實測:V27 已套用(permissions 21、role_permissions 68)、匿名 `/sources`、`/stats` 仍 200、
    `Authorization: bearer …`(小寫)與 `Basic …` 皆 401
  - `dod.sh mvp` **38/38 回歸**(M2-01)

### 稽核方法與最重要的發現

判準寫「`RbacMatrixTest` 必須涵蓋 §10.3 矩陣**每一格**」,實作的 95 格是 **19 權限 × 5 角色**
的種子一致性檢查 —— 字面達成,但 **「端點 → 需要哪個權限」這條軸完全沒有守門**
(21 個 handler 只有 3 個路徑在該測試裡)。逐端點比對後抓到 5 個端點沒有任何授權宣告。

三份矩陣來源(規格表 / `V24` / 測試常數 `RbacMatrix`)逐格比對**無差異** ✅。

### 修正(12 項,細節與取捨見 `docs/architecture/decisions/0013-phase13-audit-fixes.md`,規格索引 §0.15)

1. **[高] `/sources` ×3、`/stats` ×2 完全沒有 `@PreAuthorize`**。`SecurityConfig` 是
   `anyRequest().permitAll()` + 純方法層授權 → **沒有標註等於完全開放**。一把 scope 只有
   `["apikey:create"]` 的 API key 讀不到 `/iocs`(403)卻讀得到這五個端點,`/stats/summary`
   還帶 `tenantContext.visibility()` 連該租戶私有 IOC 的統計都給 —— §14.4 條號 6 在此失效。
   → 新增 `source:read` / `stats:read`(權限 **19 → 21**、矩陣 **95 → 105 格**、`V27` 種子),
   五個角色全持有故匿名行為不變;`EndpointAuthorizationTest` 逐 handler 守門(**已實測**:
   拿掉一個標註即 FAIL 並指名該端點)
2. **[高] 停權 / 移除成員資格對既有憑證完全無效**。登入會擋 `UserStatus`,refresh 輪替與
   API key 驗證**完全不看**;成員資格查無時兩處都 `orElse(RoleCode.USER)` → 靜默降級而非撤銷。
   → `AccountAccessPolicy` 為單一判定點,規則統一為 fail-closed(登出不受此限)
3. **[中] refresh token family 無絕對存活上限** → 竊得一枚後每 30 天輪替一次即可永久維持存取,
   重用偵測只在「兩邊都用同一枚」時才觸發。→ 90 天上限,逾期整組撤銷
4. **[中] 登入鎖定訊息洩漏帳號是否存在**(`Account temporarily locked` vs `Invalid credentials`),
   抵銷了 ADR 0012 決策 17 才修掉的時間側信道 → 訊息統一,鎖定只記伺服器端
5. **[中] 宣告的密碼政策 12–256 字元在 BCrypt 下不可實現**:Spring Security 7 的 BCrypt 對
   > 72 bytes **丟例外**而非截斷 → 80 字元的密碼在註冊時變成沒有欄位說明的 400。
   → 上限改 UTF-8 **72 bytes**(字元數擋不住:25 個中文字 = 75 bytes)
6. **[中] `/api-keys` 沒有任何數量上限**,`countActive` 是死程式 → `ctip.api-key.max-per-tenant`
   (預設 10),Phase 14 移入 plans 表
7. **[中] `last_used_at` 走整列覆寫的 `save`** → 併發時把剛寫入的 `revoked_at` 沖回 null。
   與 ADR 0011 第 1 項的 `mergeReport` 沖掉撤回**同一類缺陷** → port 新增 `markUsed` 定向 UPDATE
8. 其餘:`Authorization` scheme 大小寫不敏感且非 Bearer 回 401(不再靜默降級匿名)、
   註冊的 email/slug TOCTOU 由 500 收斂為 409、`expiresAt` 加 `@FutureOrPresent`、
   `CtipPermissionEvaluator` 的「任何 UUID 都當 tenantId」陷阱寫進 javadoc + 測試、
   04 表 12 的權限清單原寫 19 項但只列 18 個(漏 `ioc:publish`)已補回

### 查證後確認「沒有問題」(避免重工)

JWT algorithm confusion(Nimbus 在 `JWSHeader.parse` 即拒絕 `alg:none`、`MACVerifier` 對非 HMAC
丟例外)—— 無漏洞,但那是相依函式庫行為,已以否定案例測試釘住;refresh 48 base62 ≈ 285 bits /
API key 32 base62 ≈ 190 bits;`KeyHash` 常數時間比對;前端 token 只存記憶體且 401 輪替有去重;
`ApiKeyController` 無 IDOR;`/auth/*` 確實受限流涵蓋;`TenantSlugs` 對各種爛輸入都產生合法 slug;
actuator 四環境皆 `health,info`。

### 給 Phase 14 的注意事項(在既有清單之外新增)

- **⚠️ 方案配額是唯一阻止「免費取得 PREMIUM 能力」的閘門**:自助註冊即得 `TENANT_ADMIN`
  (ADR 0012 決策 5),而該角色持有 `ioc:submit` / `ioc:import` / `webhook:manage`。
  `plans.manual_submissions_per_day` 對 FREE 必須是 0 **且必須真的被檢查**
- 配額改讀 plans 表的清單多一項:`ApiKeySettings.maxPerTenant`(現為 `ctip.api-key.max-per-tenant`)
- ~~**Flyway 版本號地雷**~~ —— **已於 2026-08-28 處理,見下一段(ADR 0014)**:
  版本號改為依實作順序遞增,Phase 14 用 `V28`/`V29`(不再是 `V22`/`V23`)
- `@PreAuthorize("hasPermission(#id, '…')")` 的 `#id` 若不是 tenantId **一定要用 4 參數重載**,
  否則對所有人恆為 false(見 `CtipPermissionEvaluatorTest`)
- 新增 controller 一定要掛 `@PreAuthorize`,否則 `EndpointAuthorizationTest` 會擋;
  白名單只有 `/health`、`/version`、`/auth/*` 六項
- **⚠️ 升級既有環境**:本次修正前建立的 API key 其 scopes 不含 `source:read` / `stats:read`,
  會失去這五個端點的存取權(這正是修正的目的)。需重新建立金鑰
- M3 實作改密碼端點時,`User.changePassword` **必須一併撤銷該使用者全部 token family**
  (現在沒有呼叫端,加上去會是推測性行為,故未實作)
- `Tenant.suspend()` / `TenantStatus.SUSPENDED` 在任何認證路徑都沒被檢查 —— 與使用者停權同一類,
  但租戶停權的語意 §10 未定義,留待 Phase 14 方案/訂閱一併定義

---

## Flyway 版本號 — 廢除區段預留,改依實作順序遞增

- **狀態**:done(2026-08-28)。使用者指示:把 Phase 13 稽核留給 Phase 14 的 Flyway 地雷先處理掉。
- **Commit**:`fa18e24`(`Flyway: monotonic migration versions + working migrate.sh (ADR 0014)`)
- **完成判準結果**:全綠 —
  - `clean verify -Ptest-integration` 無過濾 ✅ **532 tests**(Spotless / Checkstyle / JaCoCo 全過)
  - `dod.sh full M3-24` ✅;全部 `.sh` 過 `bash -n`;四環境 `compose config -q` ✅
  - **實測(這是重點)**:
    - 舊編號的失敗模式 —— 乾淨 DB 套 `V20`/`V24`/`V27` 後加入 `V22` →
      `FlywayValidateException: Validate failed`,**應用啟動失敗**,`V22` 不會被套用
      (`applied versions = [20, 24, 27]`)。不是靜默漏套
    - `outOfOrder(true)` 可套用,但順序變成 `[20, 24, 27, 22]` —— 與全新 DB 的 `[20, 22, 24, 27]` 永久不一致
    - **新編號**:在已套用 `V1`–`V27` 的 DB 上放 `V28`,不開任何 flag →
      `1,2,3,4,5,6,7,20,21,24,27,28` 乾淨套用
    - `./environment/scripts/migrate.sh mvp` ✅(修好之後;對全新 DB 則套用 V1–V27 全部 11 支)

### 根因

`04 §4.7` 依「表的分組」預留版本號區段(`V1–V19`=M1、`V20–V29`=M2、`V30+`=M3),
但 Flyway **依版本號排序套用**,而 phase 的實作順序與表的分組無關。
Phase 13 用掉 `V20`/`V21`/`V24`/`V27` 之後,Phase 14(`V22`)、Phase 15(`V26`)、
Phase 18(`V25`)都低於已套用的最高版本 → 三個 phase 各會炸一次。
問題在 Phase 13 用掉 `V24` 時就已存在,`V27` 只是讓它更明顯。

### 處置(細節與取捨見 `docs/architecture/decisions/0014-flyway-monotonic-versions.md`,規格索引 §0.16)

1. **廢除區段預留**,版本號一律遞增、依實作順序指派。未寫的 migration 重新編號:
   Phase 14 → `V28`/`V29`、Phase 15 bloom → `V30`、Phase 18 threats → `V31`、
   Phase 20 notifications → `V32`、Phase 21 audit_logs → `V33`
2. 順帶修 `04` 內文寫 `V22__seed_plans.sql` 而 §4.7 寫 `V23__seed_plans.sql` 的自相矛盾
3. 順帶修 **`migrate.sh` 從來沒真的能跑**:呼叫 `flyway:migrate` 但專案從未加過
   flyway-maven-plugin(Phase 2 就記了待辦)。plugin 改宣告在 **parent** pom 的
   `<build><plugins>`(無 `<executions>`,不綁任何 lifecycle),`migrate.sh` 改用 `mvnw -N`

### 為什麼不選 `out-of-order=true`

一行就好,而且對目前規劃的 migration 功能上安全(彼此無依賴)。但它會讓全新 DB 與既有 DB 的
**套用順序永久不一致**,而那正是「絕不修改已套用的 migration」想守住的東西;
且會永久關掉一個安全網。重新編號此刻是純文件修改(那些 migration 一個都還沒寫),往後只會更貴。

### 給下一 session 的注意事項

- **⚠️ 已套用的 migration 一律不動**(checksum)。副作用:`V7__create_stix.sql` 的註解仍寫
  `V25__create_threats.sql`、`V20__create_users_and_rbac.sql` 的註解仍寫舊區段規則 ——
  **刻意保持過時**,一律以 `04 §4.7` 為準
- `V8`–`V19`、`V22`、`V23`、`V25`、`V26` 這些號碼**永遠不會有檔案**,是舊設計的殘留
- **Phase 14 的 migration 是 `V28__create_plans.sql` / `V29__seed_plans.sql`**;
  `MigrationIntegrationTest.allM1MigrationsApplyFromEmptyDatabase` 的版本清單要同步加號碼
- 新增 migration 前先看一眼現有最大版本號,新號碼必須更大
- `migrate.sh` 走 `mvnw -N`:plugin 宣告在 parent、`locations` 直指
  `ctip-app/src/main/resources/db/migration`。**不要改成 `-pl ctip-app`**
  (sibling SNAPSHOT 未安裝會解析失敗),也**不要加 `-am`**
  (`flyway:migrate` 會在 reactor 每個 module 上各執行一次,含沒有設定的 parent)
- 依規則 17 回報:`06 §6.2` 版本表沒有列 `flyway-maven-plugin`(本次未新增版本 property,
  沿用 Boot 納管的 `${flyway.version}` / `${postgresql.version}`),建議版本表補列

---

## 先行清理 — 後續 phase 會踩到的已知缺口

- **狀態**:done(2026-08-28)。使用者指示:把「之後的 phase 會遇到的問題」先修掉。
- **Commit**:`8f1cf30`(`Harden: clear known gaps that later phases would hit (ADR 0015)`)
- **完成判準結果**:全綠 —
  - `clean verify -Ptest-integration` 無過濾 ✅ **537 tests**(原 532;Spotless / Checkstyle / JaCoCo 全過)
  - `dod.sh full M3-24` ✅
  - **每一項修正都以「還原修正 → 確認測試轉紅」驗證過測試真的能判別新舊行為**

### 修了六項(細節與取捨見 ADR 0015,規格索引 §0.17)

1. **[Phase 14 前必修] `/stats/sources` 筆數不經可見度過濾** → `StatsPort.sources(Visibility)`,
   以 IndicatorEntity 為 root 再 join sources,重用同一套 `TlpSpecifications`
2. **[Phase 14 前必修] `sourceId` 查詢參數是還原被遮蔽來源歸屬的 oracle** —— 輸出遮蔽
   `DERIVED_ONLY` 的來源明細,查詢卻能用該來源過濾 → 查詢述詞套用同一條揭露規則
3. **[Phase 17] `InMemoryRateLimiter` bucket map 永不逐出** → 10,000 個 + 10 分鐘節流後,
   只逐出「已回滿且閒置逾一日」者(不放寬配額)
4. **STIX `name` 截斷切斷 surrogate pair** → 最後保留的 char 是高代理即退一格
5. **filter 逸出的例外回 Boot 預設 `/error`(無 `code`/`traceId`)** → `TraceIdFilter` 加錯誤網
6. **版本表補列三項實作已在使用的相依**(JWT/Nimbus、Flyway Maven Plugin、networknt;
   皆不新增版本 property,不改變任何 pin)

### 刻意仍不修的八項 —— 前六項需要你定調

| 項目 | 為什麼不動 |
|---|---|
| `User.changePassword` 不撤銷 token family | M3 才有呼叫端,現在做是推測性行為(規則 16)。**M3 實作改密碼端點時必須一併做** |
| `POST /auth/register` 409 可枚舉 email | 無寄信基礎設施就無法在不破壞註冊流程下消除;受匿名 IP 限流節流 |
| 租戶停權(`Tenant.suspend`)在認證路徑未被檢查 | 語意 §10 完全未定義(既有資料是否仍可讀?已簽發的 token?)——**猜一個實作下去比不做更糟** |
| 自助註冊即得 `TENANT_ADMIN`(含 `ioc:submit`) | ADR 0012 決策 5 的刻意設計;**方案配額才是正確的閘門**,Phase 14 務必確認 FREE 的 `manual_submissions_per_day` 是 0 且真的被檢查 |
| IDNA2008 / ICU4J | 需**新增 runtime 相依**,是版本表的實質變更(與這次補記錄的三項性質不同) |
| `VITE_API_URL` 進不了 staging/prod bundle(M2-25) | 兩種修法架構影響不同;若選 nginx 反代 `/api`,**必須同時設 `forward-headers-strategy=framework` 與 `server.tomcat.remoteip.internal-proxies`**,否則整個平台共用一個限流桶 |
| `MAX_PAGES_PER_RUN` 截斷後 cursor/since 語意矛盾 | 需定義 `FetchContext` 的優先序契約;M2 接真實 adapter 前必須決定,現在猜會綁死錯的語意 |
| FilterBar back/forward 草稿不同步 | 純前端 UX 取捨,非資料正確性問題 |

### 給下一 session 的注意事項

- `StatsPort.sources` 與 `IndicatorFilterSpecs.matches` 都多了 `Visibility` 參數;
  新增呼叫端一律要傳,不得繞過
- `IndicatorFilterSpecs` 的來源比對規則與 `RedistributionFilter.visibleSourceRecords`
  **必須同步**——改一邊沒改另一邊,側信道就回來了
- `TraceIdFilter` 現在是最外層錯誤網;它 catch `Exception` 但**回應已 committed 時原樣往上拋**,
  不要把那個 guard 拿掉

---

## Phase 1–13 規格漏補(批 0)

- **狀態**:done(2026-08-28)。使用者指示:盤點後續 phase 問題時,前面 phase 規格漏掉的也要補。
- **Commit**:`3a53250`(`Backfill: close Phase 1-13 spec gaps (ADR 0016)`)
- **背景**:三組 Explore 平行盤點 Phase 14–23 的阻斷項(約 90 項,見計畫檔),
  過程中發現**已完成的 Phase 1–13 也有缺口**——不是「以後會踩到」,是現在契約就已經不成立。
  本批只處理後者;Phase 14–23 的阻斷項(批 1–7)另行處理。

### 三項有實質防護意義的(都加了自動檢查)

1. **ArchUnit 規則 1 的 Jackson 防線是空的** —— 只擋 `com.fasterxml.jackson..`,
   而 Boot 4 是 `tools.jackson..`。這件事 **Phase 8 就發現並回寫進 06 §6.3.6,
   卻沒回頭同步 Phase 4 建立的規則**。已補,並以「在 domain 加 tools.jackson 依賴 → 測試轉紅」驗證
2. **11 個環境變數在 compose、四份樣板、05 §5.4 三處皆未宣告**(Phase 6/8/9)。
   compose 的 backend 環境變數是**明列白名單**(無 `env_file`),漏列者寫進 `.env` **也到不了容器**
   —— 設定看似可調、實際完全無效。三處補齊 + `SERVER_PORT`,
   並新增 **`ConfigSymmetryTest`** 自動檢查(這條規則規格寫了兩次仍復發,人工比對守不住)
3. **`15 §15.5` 明文「P-02 的可自動化部分必須實作」從未實作**,且無任何 DoD 檢查它。
   新增 **ArchUnit 規則 10**:禁止 `02 §2.1` 詞彙表「常見誤用」欄的類別名出現在 domain/sdk。
   以建立 `domain/source/Feed.java` → 規則 10 轉紅驗證

### 七項文件回補

`.github/workflows/` 只有 2 支(§13.8 標 M1 的 4 支 + 標 M2 的 2 支全部逾期,Phase 1–12
執行單皆未列)、09 端點數 47→**43**、§13.1「M1–M2 程序內 listener」實際零命中、
`01` 的 `SearchService` 命名漂移、§6.1.2 浮動 tag 政策 vs compose 釘死 patch、
§15.3「25 項全部可執行」的兩項前置(`.env.prod`、`gh`)、`sample_data.sql` 缺方案樣本
(已寫進 phase-14 交付物;順修 phase-14 的「15 個配額維度」→ **14**)。

### 為什麼現在才發現(值得記住)

前十三個 phase 的收尾回寫,範圍一律是「**本 phase 做了什麼、偏離了什麼**」。
上面第 1–3 項都是**跨 phase 的**:Phase 8 發現 Jackson 3 卻沒回頭看 Phase 4 的規則;
Phase 6/8/9 各加幾個變數卻沒人重驗對稱性;Phase 1 就該做的 ArchUnit 擴充寫在 `15` 而不在
任何執行單裡。**逐 phase 回寫抓不到這種缺口**——所以第 2、3 項改成了自動檢查。

### 給下一 session 的注意事項

- **新增任何 `ctip.*` 屬性時**,compose + 四份 `.env.*.example` + `05 §5.4` 必須同步,
  否則 `ConfigSymmetryTest` 會擋(它只硬性要求 compose 與規格清單;樣板不強制)
- ArchUnit 現在有 **10 條**規則(判準若寫死 9 需同步)
- 批 1–7(Phase 14–23 的約 90 項阻斷)尚未開始,計畫在
  `~/.claude/plans/task-notification-task-id-a59d3bc425ea2-squishy-sedgewick.md`;
  **批 1(閘門可信度)最優先**——目前 `dod.sh phase2` 會 27/27 假綠,因為
  `failIfNoSpecifiedTests=false` 使不存在的測試類回報 PASS

---

## Phase 14–23 前置清障(批 1–7)

- **狀態**:done(2026-08-28)。使用者指示:把後續 phase 會遇到的問題全部盤出來修掉,
  讓 Phase 14–23 能順順跑完。
- **Commit**:批 1 `4d1f974`、批 2+3 `8c25cff`、批 4–7 `772f5b7`
- **方法**:三組 Explore 平行盤點 `phases/phase-14..23.md` 與其治理規格,逐項對照已實作程式碼,
  共約 **90 項**落差。計畫檔:`~/.claude/plans/task-notification-task-id-a59d3bc425ea2-squishy-sedgewick.md`
- **規格索引**:`00-master.md` §0.19;逐項見 ADR 0017–0022

### 最重要的三件事

1. **閘門本來量不到東西**(批 1、ADR 0017):`failIfNoSpecifiedTests=false` 讓 `dod.sh` 對
   **不存在的測試類回報 PASS**。實測 Phase 14/15/16 一行程式都沒有時,`dod.sh phase2`
   回報 **27/27 全綠**。現在會先確認測試類檔案存在,找不到就 FAIL 並說明所屬 phase
2. **`up.sh staging` 本來必死**(批 3、ADR 0018):prometheus 掛空目錄→容器啟動即 ERROR 退出
   (實測),`up.sh` 對 exited 服務 die → M2-25 → M3-01 連鎖失敗。Phase 19 的第一個硬阻斷
3. **稽核 append-only 本來永遠不可能通過**(批 6、ADR 0021):`POSTGRES_USER` 是 postgres image
   的初始 superuser(實測 `rolsuper = t`),superuser 繞過所有 GRANT/REVOKE
   ——實測 `REVOKE` 之後 `DELETE` 照樣成功。已改為三角色模型

### 給下一 session 的注意事項

- **⚠️ 開發資料庫需重建一次**:`config/postgres/01-app-roles.sh` 是 initdb 腳本,
  只在資料目錄為空時執行。本次已重建 `ctip_postgres-data` volume 並驗證
  (三角色建立、11 migrations、1020 indicators、API 200)。**其他機器第一次拉到這個 commit 時
  也要 `down.sh mvp` + `docker volume rm ctip_postgres-data` + `up.sh mvp`**
- **應用連線角色是 `ctip_app`(非特權)**,Flyway 用 `ctip`(owner)。
  `MigrationIntegrationTest.applicationConnectsAsANonSuperuserRole` 鎖住這件事。
  **新測試若需要建表,會 `permission denied for schema public`——那是刻意的**,
  請改用 Java 端資料結構(見 `IngestionEndToEndTest` 的 `preExistingIds`)
- **新增 `ctip.*` 屬性**:compose + 四份 `.env.*.example` + `05 §5.4` 必須同步(`ConfigSymmetryTest` 會擋)
- **新增權限碼**:規格 §10.3 的清單與矩陣 + seed migration + `RbacMatrix` 常數,
  **三處同步**(`RbacMatrixTest.theSpecificationMatrixMatchesTheSeededMatrix` 逐格比對規格表)
- **Phase 14 開工前必讀** `phase-14.md` 新增的交付物:`subscription:read` 權限、
  放寬三處配額型別(0/null)、`RateLimiterPort` 改簽章、`import_jobs` 表、seed 補方案樣本
- ArchUnit 現在有 **10 條**規則

---

## Phase 14 — Plan · Subscription · 配額 ＋ IOC 寫入端點

- **狀態**:done(2026-08-28)
- **執行單**:`docs/spec/phases/phase-14.md`
- **Commit**:`20932f2`(`Phase 14: plans, quotas and IOC write endpoints`)
- **完成判準結果**:全綠 —
  - `test -Ptest-integration -Dtest='QuotaEnforcementTest,ManualSubmissionTest,FalsePositiveReportTest'`
    (逐字)✅ **24/24**(Quota 12 + ManualSubmission 8 + FalsePositive 4)
  - `clean verify -Ptest-integration` 無過濾 ✅ **644 tests**(sdk 13 + core 263 + adapters 33 + app 335;
    Spotless / Checkstyle / JaCoCo 全過,含 domain/application 的套件覆蓋門檻)
  - frontend ✅ `tsc --noEmit`、`eslint --max-warnings 0`、`build`、`test`、`format:check`、`api:check`
  - `dod.sh mvp` 回歸 ✅ **38/38**(commit 前 M1-10 `api:check` 會因為重新產生的
    `schema.d.ts` 尚未進版控而 FAIL,那是 `git diff --exit-code` 的預期行為;commit 後複跑全綠)
- **交付物**:
  - migration:`V28`(plans / subscriptions / import_jobs + `ingestion_rejections.import_job_id`)、
    `V29__seed_plans_and_permissions.sql`(四個方案 + `subscription:read` 權限,皆冪等)。
    **兩組種子併入 V29**:phase-14 只配到 V28–V29,另開 V30 會推移 Phase 15/18/20/21 已指派的號碼
  - core/domain `plan/`:`Plan`(14 個配額維度的唯讀投影)、`QuotaLimit`(0 = 停用 / null = 無限制)、
    `Subscription` 聚合(B1–B5)、`BillingPeriod`、`PlanCode`/`SubscriptionStatus`/`SubscriptionProvider`;
    事件 `SubscriptionChanged`
  - core/application `plan/`:**`QuotaService` 是配額的單一判定點**,三種超限語意各有例外型別
    (`QuotaExhaustedException` → 429、`PlanLimitExceededException` → 403、
    `RequestSizeLimitExceededException` → 413);port `PlanRepository`/`SubscriptionRepository`/
    `ImportJobRepository`/`ImportPayloadParserPort`
  - core/application `ingestion/`:`ManualSubmissionService`、`ImportService` + `ImportJobRunner`(`@Async`)
    + `ImportJobFactory`、`ImportJob`/`ImportJobStatus`/`ImportFormat`、`IngestionRun`(取代裸 `sourceSyncId`)、
    `RecordOutcome`(單筆路徑);`indicator/FalsePositiveReportService`
  - adapters:`manual/ManualSubmissionAdapter` + `ManualSubmissionCsv`(CSV 解碼,無 JSON 相依)
  - app:`PlanEntity`/`SubscriptionEntity`/`ImportJobEntity` + adapter/mapper(`PlanRepositoryAdapter`
    對 plans 有 60 秒 TTL 快取,**訂閱不快取**——降級要立即生效)、`ImportPayloadParser`(bundle 用 Jackson)、
    `ImportAsyncConfig`(有界執行緒池 + CallerRunsPolicy)、`PlanOverridesInitializer`(`CTIP_PLAN_OVERRIDES`)、
    `IocWriteController`、`SubscriptionController`、`PageSizePolicy`、`RateLimitHeaders`
  - frontend:`/iocs/new`、`/iocs/import`、`/settings/subscription` 三頁 + `features/subscription`、
    `apiPostRaw`(CSV / bundle 原文直送)、header 依權限顯示的次要導覽
- **偏離事項 / ADR**:10 項見 `docs/architecture/decisions/0023-phase14-plans-and-write-endpoints.md`;
  規格回寫 `00 §0.20`(09 §9.7、10 §10.6/§10.7、08 §8.3、04 表 5/12/17/§4.7、05 §5.4)
- **本 phase 抓到的實質缺陷(值得記住)**:
  1. **`ioc:publish` 光做擁有權轉移仍然沒有作用**——ADR 0019 已定調要把 owner 轉成 public tenant,
     但手動提交的來源記錄是 `INTERNAL_ONLY`,而 I14 規定「全來源皆 INTERNAL_ONLY 者不得出現在
     非擁有租戶的回應」+「擁有租戶豁免不適用於 public」,結果是**一筆誰都看不到的公開情資**。
     測試(匿名讀得到)先紅才發現。發布時該筆來源記錄改記 `PUBLIC_REDISTRIBUTABLE`
  2. **匯入若不計入每日提交配額,每日上限可被完全繞過**(改用匯入端點即可)。已改為扣同一個配額;
     副作用是 PREMIUM 一次匯入 10,000 筆當日只會接受 1,000 筆(§10.6 兩個欄位的數值關係使然)
  3. **配額 `0` 回 429 是在騙 client**:`Retry-After` 說「等一下再試」,但停用的配額永遠不會恢復。
     改為 `0` → 403、正整數用罄 → 429
  4. **`indicator_sources.raw_payload` 從 M1 起就沒有任何程式碼寫入**,卻有 GC 索引與保留天數設定。
     Phase 14 的 `note` / 誤判 `reason` 正好需要它,改為真的寫入(只寫不讀,新快照無內容時不覆寫)
  5. **`@Async` + 外層 `@Transactional` 會撞主鍵**:PENDING 列尚未提交,背景執行緒查不到、
     以同一個 id 再 INSERT 一次。`ImportService.submit` 因此刻意不加 `@Transactional`
  6. **配額必須跨批遞減**:`BatchState` 是一批一個,沿用同一個 `IngestionRun` 會讓每批都拿到
     完整餘額,上限形同虛設
  7. **STIX pattern 反解用 `(.*)` 會把複合 pattern 當成單一值**——
     `[a:value = 'x'] AND [b:value = 'y']` 會解出一個假 IOC。改為只吃「非引號或跳脫序列」
- **給下一 session 的注意事項(Phase 15 = Bloom Filter)**:
  - **配額一律經 `QuotaService`**,不得在任何地方寫死數值;`plans.tenant_bloom_capacity` 已可讀
  - **發布的 IOC 其來源記錄是 `PUBLIC_REDISTRIBUTABLE`**,`eligibleForBloom()` 對它成立
    ——ADR 0019「沒有動的」那一項(tenant bloom 恆為空)只剩「租戶私有提交」那一半仍需 Phase 15 定調
  - `RateLimiterPort` 已是 `tryConsume(key, tokens, QuotaLimit)` + `peek`;
    **維度 1–3 仍未做(Phase 17)**,且 `RateLimitFilter` 必須留在認證之前
  - 新增整合測試若要建立 API key,注意**數量上限依方案**(FREE = 1);需要多把就用
    `support/TestPlans` 指派 PREMIUM/ENTERPRISE,測完 `withPlan` 會自動還原
  - `plans` 是**全域參考資料**,整合測試共用同一個 context:改配額一定要還原
    (`TestPlans.withPlan` 走 finally;`RateLimitTest` 用 `@AfterEach`)
  - 新增 migration 前先看現有最大版本號(目前 **V29**);Phase 15 的 bloom 是 `V30`
  - `IngestionBatchExecutor.execute` 的第二個參數已改為 `IngestionRun`(不再是裸 `UUID sourceSyncId`)
  - 前端 `/iocs/new`、`/iocs/import` 是靜態段,靠 react-router 的評分贏過 `/iocs/:id`
    ——`router.test.tsx` 有案例釘住,升版若改了排序規則會先紅

---

## Phase 15 — Bloom Filter(兩層 · snapshot · delta)

- **狀態**:done(2026-08-28)
- **執行單**:`docs/spec/phases/phase-15.md`
- **Commit**:`5879dc7`(`Phase 15: bloom filter (two-tier, snapshot, delta)`)
- **完成判準結果**:全綠 —
  - `test -Ptest-integration -Dtest='BloomGenerationTest,BloomBitLayoutTest,BloomDeltaTest,BloomCoverageTest'`
    (逐字)✅ **22/22**(BitLayout 7 + Generation 6 + Coverage 4 + Delta 5)
  - `clean verify -Ptest-integration` 無過濾 ✅ **705 tests**(sdk 13 + core 301 + adapters 33 + app 358;
    Spotless / Checkstyle / JaCoCo 全過,含 `com.ctip.domain.bloom` 0.85 與 `com.ctip.application.bloom` 0.75 套件門檻)
  - `test -Dtest=ArchitectureTest` ✅ 10/10
  - `dod.sh phase2 --only M2-10..M2-15` ✅ 6/6;`dod.sh full --only M3-24` ✅(規格回寫後複跑)
  - `dod.sh mvp` 回歸 ✅ **38/38**
  - **容器實測**(把 `BLOOM_DELTA_CRON` 暫時改為每分鐘、`up.sh mvp` 後觀察,驗後還原):
    `bloom-data` volume 內實際落檔 `public/…/1/0-full.bin.zst`(15,635 bytes)與
    `tenant/…0001/1/0-full.bin.zst`(14,650 bytes);`bloom_versions` 兩列的
    `hash_function_count = 10`、public 的 `bit_size = 143775880`、
    `uncompressed_size_bytes = 17971985` —— 與 §11.5 manifest 範例逐格相符;
    full 的 `resulting_checksum` 為 NULL(不變量 L6)。
    路徑是 NO_BASELINE → 改生 full snapshot,`BloomGenerationService` 的降級分支在真實環境成立
- **交付物**:
  - migration `V30__create_bloom.sql`(表 22 `bloom_versions` + 表 23 `bloom_artifacts`,含 L1/L2 的
    `ck_bv_public_tenant` / `ck_bv_base` 兩條 DB 層防線)
  - core/domain `bloom/`:`BloomBitArray`(LSB-first、尾端位元為 0)、`BloomIndexer`
    (Kirsch-Mitzenmacher,unsigned 64-bit wraparound)、`BloomDeltaCodec`(升序去重 → 差分 → LEB128 varint)、
    `BloomParameters`(§11.4 公式,m 取整至 8 的倍數、k 由公式導出)、`Checksum`、`BloomVersion` 聚合
    (L1–L8)+ `BloomArtifact`、`BloomMembership`(兩個 scope 的成員述詞)、`BloomChainPolicy`、
    `BloomArtifactLocation`;事件 `BloomSnapshotReady`
  - core/application `bloom/`:`BloomScopePlanner`(誰有 tenant bloom、容量多大)、`BloomSnapshotService`、
    `BloomDeltaService`、`BloomRetentionService`、`BloomArrayLoader`、`BloomGenerationService`(排程編排)、
    `BloomChangeTracker`、`BloomSettings`/`BloomPorts`/`BloomTarget`/`DeltaOutcome`;
    port `BloomStoragePort`/`BloomMemberPort`/`BloomVersionRepository`、`SubscriptionRepository.findActiveTenantIds`
  - core/application `ingestion/BloomUpdateStage`(pipeline stage 10,`PersistStage` 之後)
  - app:`BloomVersionEntity`/`BloomArtifactEntity` + adapter/mapper、`infrastructure/bloom/`
    (`FilesystemBloomStorage`——專案第一個寫檔實作、`BloomMemberAdapter` 投影查詢)、`BloomSchedulers`
    (04:00 full / 每小時 delta,沿用 `ctip.scheduler.enabled` 總開關)、`BloomConfig`、
    `CtipProperties.Bloom` 補 `storageDir`/`compression` + `application.yml` 綁定、`ctip-app` 加 zstd-jni
  - 測試:判準四支 + core 單元測試(`BloomVersionTest`、`BloomScopePlannerTest`、`BloomSnapshotServiceTest`、
    `BloomDeltaServiceTest`、`BloomRetentionServiceTest`、`BloomGenerationServiceTest`、`BloomUpdateStageTest`)、
    `FilesystemBloomStorageTest`;`MigrationIntegrationTest.phase15BloomTablesExist`、`RequiredIndexTest` 補四個索引
- **偏離事項 / ADR**:10 項見 `docs/architecture/decisions/0024-phase15-bloom-decisions.md`;
  規格回寫 `00 §0.21`(11 §11.2/§11.3/§11.5、02 BloomVersion、04 表 23、05 §5.4)
- **本 phase 抓到的實質缺陷(值得記住)**:
  1. **`tenant_bloom_capacity = NULL` 在 §11.2 是「無 tenant Bloom」,而 `QuotaLimit` 的平台慣例是
     「無限制」**——同一個欄位兩種相反讀法。採 §11.2、fail-closed(只有正整數才生成)
  2. **「保留最近 N 份」照字面會破壞 delta 鏈**:同 dataset 內 full snapshot 的 `bloomVersion` 最小
     (= 0)因此最舊,會先被刪掉,而它的 delta 還留著 → 那條鏈永遠無法重建
  3. **04 表 23 與 §11.5 對 delta 的 `checksum` 說法相反**;定調為「未壓縮 artifact payload」的 SHA-256,
     因此 varint 差分編碼必須在 Phase 15 完成(不先產生 payload 就算不出 checksum)
  4. **`BLOOM_STORAGE_DIR` / `BLOOM_COMPRESSION` 在 compose 與 §5.4 都有、`application.yml` 卻沒綁**
     ——`ConfigSymmetryTest` 是單向檢查(yml → compose/spec),抓不到這種反向缺漏
  5. `bloom_artifacts.checksum` 是 `CHAR(64)`,entity 沒加 `@JdbcTypeCode(SqlTypes.CHAR)` 會讓
     `ddl-auto: validate` 以 `bpchar` vs `varchar` **拒絕啟動**
  6. `information_schema.check_constraints` 只列出**當前角色擁有的表**,而應用以非特權的 `ctip_app`
     連線——查約束一律走 `pg_constraint`
  7. **artifact 損壞若不驗會靜默毒化整條鏈**:下一段 delta 的 `resultingChecksum` 會依損壞後的
     陣列算出,於是每個 client 套用後自我驗證失敗、重下 full,而伺服器端毫無徵兆。
     `BloomArrayLoader` 因此走與 client 相同的 §11.6 驗證,不符即回 `FULL_SNAPSHOT_REQUIRED`
     (該檢查以「拿掉檢查 → 測試轉紅」驗證過確實能判別:`expected FULL_SNAPSHOT_REQUIRED but was CREATED`)
- **給下一 session 的注意事項(Phase 16 = 增量同步 API 與 client 契約)**:
  - **`addedBits` 只差第 4 步**:`BloomDeltaCodec` 已產出 varint 差分 payload(artifact 內容),
    Phase 16 只需 base64url(無 padding)包裝;`checksum` / `resultingChecksum` 都已寫入 `bloom_artifacts`
  - **`BloomVersion.requiresFullSnapshot(chainLength, cumulativeDeltaBytes, policy)` 就是 409 的判定點**
    ——生成端已在用(鏈太長改生 full),Phase 16 的 `/sync/delta` 用同一個方法回 `SNAPSHOT_REQUIRED`
  - ⚠️ **M2-15 目前是「假綠」的一半**:它跑的是 `BloomDeltaTest`,而該測試驗的是**生成端**的
    「鏈太長 → 改生 full」;`409 SNAPSHOT_REQUIRED` 的 HTTP 行為還不存在。Phase 16 必須把該分支
    加進 `SyncEndToEndTest`,並考慮把 M2-15 指向它
  - manifest 的 `coverage` / `notCovered` 是必填(§11.5);`BloomScope` 只有 PUBLIC/TENANT,
    `notCovered` 一律含 `TLP:GREEN`
  - `GET /sync/bloom` 若採「302 至簽章 URL」需要新的簽章金鑰設定項(§5.4 沒有);
    直接串流 `BloomStoragePort.read` 則不需要——後者不必新增設定,建議採之
  - 匿名(綁 public tenant)持有 `sync:bloom`,但 `scope=TENANT` 對它的語意未定義;
    `BloomScopePlanner.tenantTarget` 對 public tenant 一律回空,API 層照此回 403/404 較一致
  - **排程預設是關的**(`SCHEDULER_ENABLED=false` 於整合測試);Phase 16 的 `SyncEndToEndTest`
    要自己呼叫 `BloomSnapshotService.generate(...)` 準備資料,不要等排程
  - 整合測試的 bloom artifact 根目錄由 `AbstractPostgresIntegrationTest` 注入臨時目錄,
    且 `BLOOM_PUBLIC_CAPACITY` 縮成 100,000(預設 1,000 萬 → 每份 18MB);新測試沿用即可
  - tenant bloom 需要**真實存在的租戶**(`bloom_versions.tenant_id` 有 FK):用 `support/BloomTenants`
    自建租戶並指派方案,不要動種子的 demo 租戶(其他測試共用)

---

## Phase 16 — 增量同步 API 與 client 契約

- **狀態**:done(2026-08-28)
- **執行單**:`docs/spec/phases/phase-16.md`
- **Commit**:`eab3780`(`Phase 16: incremental sync API and client contract`)
- **完成判準結果**:全綠 —
  - `test -Ptest-integration -Dtest=SyncEndToEndTest`(逐字)✅ **4/4**
    (完整流程、manifest coverage、鏈過長 409、base 不在現行 dataset 409)
  - `cd frontend && npm run test -- SyncPage`(逐字)✅ **6/6**
  - `clean verify -Ptest-integration` 無過濾 ✅ **727 tests**
    (sdk 13 + core 316 + adapters 33 + app 365;Spotless / Checkstyle / JaCoCo 全過)
  - 前端 `npm run test` ✅ 121、`npx tsc --noEmit` ✅、`npm run lint` ✅、`format:check` ✅、
    coverage lines 85.43%(門檻 70%)
  - `npx playwright test` ✅ **3/3**(匿名搜尋、Bloom 說明頁、登入→建 API key→提交 IOC)
  - `dod.sh phase2 --only M2-10/13/14/15/16/17` ✅ 6/6;`--only M2-26` ✅ 1/1;
    `dod.sh full --only M3-24` ✅(規格回寫後複跑)
  - `dod.sh mvp` 回歸 ✅ **37/38 → 38/38**:M1-10(`npm run api:check`)比對的是 **committed** 的
    generated 型別,commit 前必然紅;commit 後單獨複跑 `--only M1-10` ✅
- **交付物**:
  - core/application `sync/`:`SyncService`(manifest / download / delta 的唯一判定點)、
    `SyncManifest`/`SyncDelta`/`BloomDownload`、`SnapshotRequiredException`、`SyncTooFrequentException`;
    port `SyncThrottlePort`;`BloomStoragePort.readStored`(回儲存體原始位元組)
  - core/domain:`BloomCoverage`(coverage / notCovered 的文字與成員條件同一處維護)、
    `BloomVersion.arrayChecksum()`、`BloomDeltaCodec.merge`(區間併集)、
    `BloomScopePlanner.hasTenantBloom`(生成端與下載端共用的成員資格判定)
  - app:`SyncController` + `SyncApi`(OpenAPI,含 `X-Bloom-*` 標頭與三種錯誤的範例)、
    `dto/sync/`(3 個 record)、`SyncDtoMapper`(base64url 在此發生)、
    `InMemorySyncThrottle`、`ClientSubject`、`infrastructure/web/ClientIp`(限流與節流共用 IPv6 /64 收斂)、
    `BloomVersionRepository.recordDownload`(04 表 23 的 `download_count`,定向 UPDATE)、
    `ApiExceptionHandler` 補 409 SNAPSHOT_REQUIRED 與 429 + Retry-After、
    CORS `exposedHeaders` 補七個 `X-Bloom-*`
  - 前端:`features/sync/`(api + hook + `BloomLayerTable` + `BloomSemanticsNotice`)、
    `pages/SyncPage`(路由 `/sync`,匿名可存取)、主導覽加「Bloom 同步」、MSW handler
  - **Playwright 骨架**(ADR 0022 歸位):`@playwright/test` 1.62.1、`playwright.config.ts`
    (webServer 跑 `build && preview`;`E2E_BASE_URL` 可改對整套環境跑)、`e2e/stubs.ts`、
    `e2e/smoke.spec.ts`、`e2e/session.spec.ts`——**M2-26 的四個情境全數覆蓋**
  - 文件:`docs/api/sync-client-contract.md`(§11.7 六條 + 位元格式 + 流程 + 錯誤表)、
    `docs/api/README.md`(索引 + **公開情資誤判申訴流程**——Phase 14 該寫進 `docs/api/` 但漏了)
  - 測試:`SyncEndToEndTest`(判準)、`SyncAuthorizationTest`(匿名/方案/429)、
    `SyncServiceTest`(core 13 條)、`BloomVersionTest` 補 `arrayChecksum` 與 coverage 文字、
    support `SyncTestClient` / `SyncFlowSteps`(把 §11.6 的四個步驟寫成看得懂的流程)
- **偏離事項 / ADR**:11 項見 `docs/architecture/decisions/0025-phase16-sync-api-decisions.md`;
  規格回寫 `00 §0.22`(11 §11.5/§11.6/§11.7、09 §9.1、05 §5.1、12 §12.8、14 §14.6、15 §15.2)
- **本 phase 抓到的實質缺陷(值得記住)**:
  1. **manifest 的 `checksum` 照字面取「最新版本 artifact 的 checksum」會永遠對不上**:
     最新版本是 delta 時那算的是 varint payload 的雜湊。定調為「完全同步後陣列應有的 SHA-256」
     (`arrayChecksum()`),並讓 manifest 與 `/sync/delta` 共用同一個方法
  2. **§11.6 第 4 步「更新版本」沒說更新成哪個數字**——照 manifest 記會產生 Bloom 的
     **false negative**(陣列少了 delta 的位元卻自認最新)。因此下載回應必帶 `X-Bloom-*`;
     自我驗證(空區間也有 `resultingChecksum`)是第二道防線
  3. **409 若也消耗同步間隔,復原路徑永遠走不完**:client 收到 409 必須改下載 full,
     下一步會立刻撞 429。節流因此只在「已確定回 200」之後才記帳
  4. **M2-15 是假綠**:它跑 `BloomDeltaTest`(生成端),而 `409` 的 HTTP 行為在 Phase 15 不存在。
     判準已改指向 `SyncEndToEndTest`(真的產生 25 段 delta),`dod.sh` 同步
  5. **`min_sync_interval_seconds` 的記帳對象不能是 tenant**:匿名一律綁 public tenant,
     以 tenant 記帳等於全體匿名 client 共用一個額度(第一個同步完,其他人整天 429)
  6. `OpenApiCompletenessTest` 的 2xx 檢查寫死 `application/json`,會逼 octet-stream 的下載端點
     假裝自己回 JSON——判準改為「任一 2xx 有非空 content」
  7. **Phase 14 漏交的 `docs/api/` 誤判申訴說明**(09 §9.7 明文要求)本 phase 一併補上
  8. **`bloom_artifacts.download_count` 自 Phase 15 就存在卻從未被寫入**(規則 16 的永不可達欄位)——
     下載端點是它唯一可能的呼叫端,本 phase 補上定向 UPDATE。順帶抓到:`download()` 若宣告
     `@Transactional(readOnly = true)`,那個 UPDATE 會被 PostgreSQL 直接拒絕(整合測試實測),
     而把 18MB 檔案讀取包在交易裡本來就不該做 → 該方法改為不宣告方法層交易
- **給下一 session 的注意事項(Phase 17 = Redis 快取 + 分散式限流)**:
  - **`SyncThrottlePort` 要一併換 Redis 實作**:`SETEX subject <interval> <timestamp>`,
    TTL 讓逐出自動發生;現行 `InMemorySyncThrottle` 僅單一實例正確(與 `InMemoryRateLimiter` 同一定位)
  - 限流維度 1–3(key/user/tenant)本 phase **沒有**實作;`ClientSubject`(API key → user → IP)
    已可直接當那三個維度的鍵來源,IPv6 /64 收斂在 `infrastructure/web/ClientIp`
  - ⚠️ **維度 4(匿名 IP)必須留在認證之前**(ADR 0012 決策 16),不得把它一起搬到認證之後
  - 整合測試的限流器與節流狀態**在記憶體中跨測試類共用**:新測試一律用自己的 client IP
    (本 phase 用 `10.30.0.16/.21/.22`),否則會互相把對方的額度用掉
  - `npx playwright test` 需要 `npx playwright install chromium`(本機已裝);CI 上要記得加這一步
  - 前端 `api:check` 會比對 `docs/api/openapi.json` 產生的型別與 committed 版本——
    改動任何 DTO 後要跑 `OpenApiCompletenessTest` 再 `npm run api:generate` 並一起 commit

## Phase 17 — Redis(快取 + 分散式限流)

- **狀態**:done(2026-08-29)
- **執行單**:`docs/spec/phases/phase-17.md`
- **Commit**:`579c971`(`Phase 17: redis cache and distributed rate limiting`)
- **完成判準結果**:全綠 —
  - `test -Ptest-integration -Dtest='DistributedRateLimitTest,QuotaEnforcementTest'`(逐字)✅ **15/15**
  - `clean verify -Ptest-integration` 無過濾 ✅ **757 tests**
    (sdk 13 + core 323 + adapters 33 + app 388;Spotless / Checkstyle / JaCoCo 全過)
  - `dod.sh phase2 --only M2-08/09/15/16/27` ✅;`dod.sh full --only M3-24` ✅(規格回寫後複跑)
  - `dod.sh mvp` 回歸 ✅ **38/38**
  - **反向驗證**:把 `DistributedRateLimitTest` 的後端切回 `memory`,三個案例全紅
    (共用配額、跨實例遞減、跨實例的方案變更皆失敗)——判準不是假綠
  - **判準在 8080 被佔用時仍正確**:第一次 `dod.sh mvp` 的 M1-38 抓到第二個實例綁固定 8080
    (見下方缺陷 7),修正後在 mvp 容器執行中(8080 被佔)複跑,兩個實例皆取得 ephemeral port
- **交付物**:
  - core:`CachePort`(值為 String,序列化留在 infrastructure)、`RateLimiterPort.refund`、
    `RateLimitKey` 的五個維度工廠 + `inClass`/`inWindow`、`domain/plan/EndpointClass`
    (read 100% / write 20% / heavy 5%,至少 1)
  - app `infrastructure/redis/`:`RedisCacheAdapter`(GET/SET EX/DEL 三個指令)、
    `RedisRateLimiter`(Bucket4j `LettuceBasedProxyManager`,借用 Boot 建好的 Lettuce client)
  - app `infrastructure/cache/InMemoryCache`(memory 後端;帶 TTL 與清掃)
  - app `infrastructure/ratelimit/`:`IdentityRateLimitFilter`(維度 1–3、5 + 維度 4 的歸還)、
    `RateLimitResponder`(標頭與 429 的唯一出口,兩個檢查點共用)、`RateLimitScope`(豁免規則)、
    `EndpointClassifier`、`CacheBackedSyncThrottle`(取代 `InMemorySyncThrottle`,已刪除)
  - app `infrastructure/web/`:`TrustedProxies`、`TrustedProxyForwardedHeaderFilter`
  - config:`RedisConfig`(backend=redis 才裝配)、`RateLimitConfig`(memory + 共用 bean + 兩個 filter)、
    `ForwardedHeadersConfig`、`SecurityConfig` 掛 `addFilterAfter`、`StartupValidator` 補
    TRUSTED_PROXIES 警告、`CtipProperties.Proxy`
  - persistence:`PlanRepositoryAdapter` / `RolePermissionRepositoryAdapter` 的行程內 map
    改走 `CachePort`(+ `PlanCacheCodec`,解不開時當 miss)
  - 設定:`server.forward-headers-strategy=framework`、`ctip.proxy.trusted`、
    `TRUSTED_PROXIES`(compose + 五份樣板 + §5.4)、`application-mvp.yml` 關 redis health indicator
  - 文件:`docs/deployment/rate-limiting.md`(§10.7 明文要求的「真實 client IP 限制」記載 +
    兩個檢查點 + 後端故障行為 + **Valkey 替換步驟** + 排錯表)
  - 測試:`DistributedRateLimitTest`(判準,兩個 Spring context)、`IdentityRateLimitTest`(維度 1/2/3/5)、
    `EndpointClassTest`、`EndpointClassifierTest`、`InMemoryCacheTest`、`CacheBackedSyncThrottleTest`、
    `TrustedProxiesTest`、`RateLimitKeyTest` 補三個維度與維度 5 的鍵、**ArchUnit 規則 11**
- **偏離事項 / ADR**:14 項見 `docs/architecture/decisions/0026-phase17-redis-cache-and-distributed-rate-limit.md`;
  規格回寫 `00 §0.23`(10 §10.7、05 §5.4/§5.7、06 §6.3.6、01 §1.9、14 §14.5、15 §15.1、phase-23)
- **本 phase 抓到的實質缺陷(值得記住)**:
  1. **§10.7 的維度 5 鍵沒有主體**:`ratelimit:{scope}:{endpointClass}:{window}` 照字面是
     **全平台共用一個桶**——一行 `curl` 迴圈就能讓所有人的讀取端點回 429。
     改為含 subject;ADR 0020「分類上限恆低於總上限」本來也只有 per-subject 才成立
  2. **維度 4 會把已認證者綁死在匿名配額**:限流必須在認證前(ADR 0012 決策 16),
     但那時不知道會不會認證成功 → ENTERPRISE 的 client 實際上只有 60/min,**方案分級形同虛設**。
     解法是認證成功後 `refund`;副作用是維度 4 對已認證流量變成「同時進行中的請求數」上限
  3. **bucket4j 把桶設定存進 Redis 後不再更新**:方案降級時舊桶沿用較寬的容量到過期 = fail-open。
     `withImplicitConfigurationReplacement` 只在版本遞增時替換,對降級無效 → 鍵帶容量
  4. **`forward-headers-strategy=framework` 的 Boot 內建 filter 無條件採信 `X-Forwarded-*`**:
     應用只要有一條路徑能被直連,每個請求換一個假 IP 即可完全繞過維度 4
  5. **`spring-boot-data-redis` 一在 classpath 上就會加 actuator 的 redis 健康檢查**——
     而 mvp 的 compose 不啟動 redis,不關掉的話容器永遠 unhealthy、`depends_on` 卡死
  6. `TestRestTemplate` 在 Boot 4 被拆到版本表未列的 `spring-boot-restclient-test`
     (同 MockMvc 的前例)→ 測試改用 JDK `HttpClient`
  7. **判準自己曾經量錯對象**:`SpringApplicationBuilder.properties(...)` 是優先序**最低**的
     `defaultProperties`,`server.port=0` 被 `application.yml` 的 `${SERVER_PORT:8080}` 蓋掉,
     第二個實例綁在固定 8080。單獨跑正常(8080 是空的),但 `dod.sh mvp` 的 M1-38 是在
     **mvp 容器已佔用 8080** 時跑的——請求打到容器裡的另一個 app,三個案例全紅且訊息
     完全看不出原因。改用 `run("--server.port=0", ...)`(命令列參數優先序高於 yml),
     並加兩道啟動守衛(埠不為 0 且與實例 1 不同、第二個實例的後端真的是 redis)
  8. **`01 §1.9` 的 ArchUnit 規則數自 ADR 0016 起就與實作不符**(規則 10 加了實作沒回寫表與計數);
     本 phase 補回並加上規則 11,`14 §14.5`、`15 §15.1`、`00 §0.3` 的引用一併同步
- **給下一 session 的注意事項(Phase 18 = Threat 實體與關聯 + M2 的 STIX 物件)**:
  - **限流的行為變了,寫測試時要注意**:匿名的 write 類別上限只有 **12/min**(60 的 20%)、
    heavy 只有 **3/min**;`AuthHardeningTest` 就是因此改成每個測試方法一個 client IP。
    新測試若會連送十幾個 POST,請先分配自己的 IP(本 phase 用 `10.40.0.x`)
  - 已認證的測試不再受匿名 IP 配額限制(認證成功即歸還),但**維度 3(租戶)**會累積:
    同一個租戶跨測試方法送超過該方案 `requests_per_minute` 的請求就會 429
  - `TestPlans.withPlan(...)` 會改**全域** plans 表;Phase 17 起 `save` 會連帶失效快取,
    因此改動即時生效(不必等 60 秒 TTL)
  - Phase 18 的 `ThreatIntegrationTest` 若要跑限流以外的東西,不需要 Redis:
    整合測試預設 `RATE_LIMIT_BACKEND=memory`,只有 `DistributedRateLimitTest` 自己起 Redis 容器
  - ADR 0020 已為 Phase 18 定調四件事:`ux_ter_identity` 要改成
    `COALESCE(external_id, '')` 的唯一索引(PostgreSQL 的 UNIQUE 不去重 NULL)、
    H6 降格為應用層一致性規則、`ThreatAlias` 以 `TEXT[]` 為準、
    M2 的五種 STIX SDO 對照表要在 Phase 18 依 §7.8.2 的體例補寫進 §7.8
  - threats 的 migration 是 **`V31`**(§4.7 已廢除區段預留,版本號一律遞增),
    且要以 `ALTER TABLE` 補上 V7 保留的 `fk_so_threat`

---

## Phase 18 — Threat 實體與關聯 + M2 的 STIX 物件

- **狀態**:done(2026-08-29)
- **執行單**:`docs/spec/phases/phase-18.md`
- **Commit**:`1619b90`(`Phase 18: threat entity, relationships and M2 STIX objects`)
- **完成判準結果**:全綠 —
  - `test -Ptest-integration -Dtest='ThreatIntegrationTest,StixSchemaValidationTest'`(逐字)✅ **23/23**
  - `cd frontend && npm run test -- ThreatFeedPage ThreatDetailPage`(逐字)✅ **10/10**
  - `clean verify -Ptest-integration` 無過濾 ✅ **815 tests**
    (sdk 13 + core 360 + adapters 33 + app 409;Spotless / Checkstyle / JaCoCo 全過)
  - 前端 `npm run test` ✅ 131、`npx tsc --noEmit` ✅、`npm run lint` ✅、`format:check` ✅
  - `dod.sh phase2 --only M2-21`(Threat 三張表可用)✅;`--only M2-04/06/07/20/27` ✅ 5/5
  - `dod.sh mvp` 回歸 ✅ **37/38 → 38/38**:M1-10(`npm run api:check`)比對的是 **committed** 的
    generated 型別,commit 前必然紅;commit 後單獨複跑 `--only M1-10` ✅(同 Phase 16)
- **交付物**:
  - migration `V31`:表 19–21 + V7 保留的 `fk_so_threat` + **表 8 漏掉的 `ix_so_threat`**
    + `threat:manage` 權限種子(冪等;放在同一個 migration 的理由見 ADR 0027 §7)
  - domain `threat/`:`Threat` 聚合(H1–H5)、`ThreatIndicatorLink`(不可變 record,只存 id)、
    `ExternalReference`(H3/H4 的比較鍵與 DB 的 `COALESCE` 唯一索引同語意)、
    `ThreatType`/`ThreatStatus`/`IndicatorRole`/`ThreatChange`、`ThreatEvents.ThreatUpdated`
  - domain `stix/`:`StixThreatProjector`、`StixObservedDataProjector`、`StixIdentityProjector`、
    `StixRelationshipProjector`、`StixRelationship`、`StixOrigin`、`StixIds`(決定性 UUID)、
    `StixTimestamps`(五種投影共用格式)
  - application:`ThreatRepository` port、`ThreatService`(寫入 + **H6 的唯一執行點**)、
    `ThreatQueryService`(兩段可見度)、`ThreatFilter`、`ThreatStixProjectionService`;
    `StixQueryService` 擴充為服務全部 M2 物件;`IndicatorRepository.findVisibleByIds`;
    `StixObjectPort.findOrigin`;`StixRelationshipPort`
  - infrastructure:`ThreatEntity`/`ThreatIndicatorEntity`/`ThreatExternalReferenceEntity` 與
    mapper／adapter、`ThreatFilterSpecs`(aliases 與 tags 一律走 `ctip_tags_contain_all`)、
    **`ThreatSpecifications`**(threats 的可見度述詞)、`StixRelationshipAdapter`、
    `ThreatConsistencyListener`(domain event 的第一個消費端)
  - REST:`ThreatController`(三個 GET)+ `ThreatWriteController`(五個寫入端點)+
    `ThreatResponseAssembler` + `dto/threat/` 七個 record + `ThreatApi`/`ThreatWriteApi`
  - 前端:`features/threat/`(api + 兩個 hook + 四個元件)、`pages/ThreatFeedPage`/`ThreatDetailPage`、
    路由 `/threats`、`/threats/:id`、主導覽「威脅情報」、MSW handlers
  - 測試:`ThreatIntegrationTest`(判準,10 個情境)、`ThreatTest`、`ThreatServiceTest`、
    `ThreatQueryServiceTest`、`ThreatStixProjectionServiceTest`、`StixSchemaValidationTest` +6、
    support `ThreatCurationClient` / `PublishedIndicators` / `StixProjectionFixtures`、
    core testing `InMemoryThreatRepository` / `InMemoryStixObjects` / `InMemoryStixRelationships` /
    `ThreatTestBuilder`
- **偏離事項 / ADR**:8 項見 `docs/architecture/decisions/0027-phase18-threat-and-m2-stix.md`;
  規格回寫 `00 §0.24`(09 §9.1、10 §10.3、04 表 8/§4.7、02 §2.3/§2.4、03 §3.2.7、07 §7.7/§7.8.6/§7.8.7)
- **本 phase 抓到的實質缺陷(值得記住)**:
  1. **整個 phase 的資料都不可達**:§9.1 的 Threat 只有三個 `GET`,ingestion 不產生 Threat
     (`RawThreatRecord` 沒有威脅欄位)、Phase 19–23 也沒有任何建立管道。照執行單字面實作,
     三張表、聚合的四個行為、三種 STIX 投影、`threat:read` 全部永遠不可達。
     **經使用者裁示補最小寫入端點**(與 v2.0 為 IOC 補寫入端點同源)
  2. **`POST /{id}/retire` 會讓 `ThreatStatus.DORMANT` 永不可達**——改 `PUT /{id}/status`;
     順帶滿足 `OpenApiCompletenessTest` 對 POST 必須有 request schema 的要求
  3. **AFTER_COMMIT 的事件消費端寫資料庫,預設傳播行為下不落庫也不報錯**:
     回呼仍在已提交交易的 synchronization 範圍內(EntityManager 還綁著、交易已結束)。
     第一次實作就是這樣——`malware` 與 `relationship` 一列都沒有,連例外都沒有。
     `REQUIRES_NEW` 是必需的,規則已寫進 `02 §2.4`(對 M3 的 Kafka listener 同樣成立)
  4. **CLEAR 的租戶私有威脅是誰都看不到的東西**:§7.7 只讓 public tenant 的 CLEAR/GREEN 對外可見。
     因此建立 Threat 沿用 §9.7 的規則(預設 AMBER;CLEAR/GREEN 需 `ioc:publish` 且轉為 public tenant)
     ——與 ADR 0019 第 2 節要消滅的缺陷同源
  5. **H6 是單向的**:把私有 IOC 關聯到公開威脅會把該威脅收緊到公開範圍之外,解除關聯也不放寬。
     這是 ADR 0020 定調的必然結果,已寫進端點文件
  6. `malware` 的 `aliases` 有 `minItems: 1`(空集合必須整個省略)、`attack-pattern` 沒有
     `is_family`/`first_seen`/`last_seen`、`observed-data` 必須有 `objects` 或 `object_refs`
     ——三者都是 schema 驗證才會抓到的
  7. **STIX identifier 只接受 v1–v5 的 UUID**:`00000000-…-0000a1` 這種測試常數驗不過。
     正式環境的 id 是 v4 自然合法,但 fixture 若用假形狀,等於用現實不存在的輸入把檢查騙過去
  8. `stix_objects.threat_id` 沒有索引而 `fk_so_threat` 帶 `ON DELETE CASCADE`(執行單已預警)
- **給下一 session 的注意事項(Phase 19 = Elasticsearch 搜尋 + reconciliation + 降級)**:
  - ⚠️ **ES index mapping 必須重建可見度述詞**(ADR 0020 第 8 節):`13 §13.7` 的欄位清單不含
    `ownerTenantId`、`deletedAt`、來源的 `redistributionPolicy`,漏掉任何一個,ES 路徑就整套繞過過濾
  - `SearchPort` 目前回 `CursorPage<Indicator>`,而 `X-Search-Backend` 沒有傳遞通道;
    三個 `SearchPort` bean 的歧義也要處理(`PostgresSearchAdapter` 是 `@Component`)
  - Threat 的搜尋**不在 Phase 19 的交付物內**;`ThreatUpdated` 事件已就緒
    (`ThreatConsistencyListener` 是現成的消費端範例),要索引 Threat 時從那裡接
  - 新的整合測試請自己分配 client IP(本 phase 用 `10.50.0.11/.12`);限流狀態跨測試類共用
  - **AFTER_COMMIT 寫資料庫一律 `REQUIRES_NEW`**(見上方缺陷 3);Phase 19 的
    `SearchIndexStage` 若改由事件驅動會踩同一個坑
  - 前端只交付了兩個唯讀頁面(執行單的交付物就是這兩個);**策展寫入目前只有 API,沒有 UI**,
    若要補 UI 需另行指派
  - `db/seed/sample_data.sql` **沒有 threat 樣本**(§14.7 的清單未列);mvp/dev 環境的
    `/threats` 預設是空的,要看畫面得先用寫入端點建幾筆(或另行指派補 seed)

---

## Phase 19 — Elasticsearch 搜尋 + reconciliation + 降級(M2 收官)

- **狀態**:done(2026-08-29)
- **執行單**:`docs/spec/phases/phase-19.md`
- **Commit**:`0991e6a`(`Phase 19: elasticsearch search, fallback and reconciliation`)
- **完成判準結果**:全綠 —
  - `test -Ptest-all -Dtest='ElasticsearchSearchTest,SearchFallbackTest,SearchReconciliationTest'`(逐字)✅ **14/14**
  - `./environment/scripts/up.sh staging` ✅ 八個服務全 healthy(含 `elasticsearch:9.5.1`)
  - `./environment/scripts/dod.sh phase2` ✅ **27/27**(commit 後單次乾淨跑完,無 `--only` 補跑)
    (commit 前的那一輪是 26/27,唯一的紅是 M2-01 內的 M1-10 `npm run api:check`——它比對
    **committed** 的 generated 型別,commit 前必然紅,同 Phase 16/18)
  - `clean verify -Ptest-integration` 無過濾 ✅ **836 tests**
    (sdk 13 + core 369 + adapters 33 + app 421;Spotless / Checkstyle / JaCoCo 全過)
  - **staging 實機驗證**:索引在啟動後自動補建 1,037 筆 → 搜尋回 `X-Search-Backend: elasticsearch`;
    `docker stop ctip-elasticsearch-1` 之後同一個查詢仍回 **200** 且 `X-Search-Backend: postgres`,結果正確
  - **反向驗證(判準不是假綠)**:
    - 拿掉 ES 端的可見度述詞 → `invisibleDocumentsDoNotConsumeThePage` 與
      `filtersAndCursorPaginationBehaveLikeTheDatabasePath` 轉紅
    - 把 PostgreSQL 補齊改成不看可見度的 `findById` → `poisonedIndexDocumentsStillCannotEscapeVisibility` 轉紅
    - compose 的 `ELASTICSEARCH_URL` 改回空字串預設 → `ConfigSymmetryTest` 轉紅
- **交付物**:
  - core `application/port/`:`SearchQuery`／`SearchResult`／`SearchBackend`(`X-Search-Backend` 的傳遞通道)、
    `SearchPort.search(SearchQuery)`、`SearchIndexPort`／`SearchDocumentPort`／`SearchIndexDocument`／`IndexedDocument`
  - core `application/`:`SearchIndexStage`(pipeline 第 11 格,只標記)、
    `search/SearchIndexWriter`(交易提交後寫出,失敗只記錄)、
    `search/SearchReconciliationService` + `ReconciliationReport`
  - app `infrastructure/elasticsearch/`:`IndicatorSearchIndex`(索引 `ctip-indicators`、`dynamic: strict` mapping)、
    `ElasticsearchSearchAdapter`(可見度 + filter + wildcard/fuzzy + `search_after`;**只取 id,資料由 PostgreSQL 補齊**)、
    `ElasticsearchIndexAdapter`(bulk / count / 掃描)、`SearchVisibilityQuery`／`SearchFilterQuery`／
    `SearchTermQuery`／`SearchFields`／`EpochNanos`
  - app `infrastructure/search/`:`FallbackSearchAdapter`(Resilience4j circuit breaker)、
    `NoopSearchIndexAdapter`、`SearchIndexBootstrap`(索引空而 DB 非空時,啟動後在背景補建一次)
  - app:`SearchDocumentAdapter`、`SearchSchedulers`(每日 05:00)、`SearchConfig`(`@Primary` + ES 條件裝配)、
    `IocController`／`IocApi`／`SearchRequest` 的 `fuzzy` 與 `X-Search-Backend`、`WebCorsConfig` 的 exposedHeaders、
    `CtipProperties.Search`
  - 設定:`spring.elasticsearch.*`、`ctip.search.{backend,reconcile-cron}`、
    `SEARCH_BACKEND`／`ES_RECONCILE_CRON`(compose + §5.4 + 樣板)、
    `application-{mvp,dev}.yml` 關閉 actuator 的 ES 健康檢查
  - 文件:`docs/deployment/licensing.md`(§6.5 自 M1 起要求、一直不存在;含 ES → OpenSearch 與 Redis → Valkey 的替換步驟)
  - 測試:`ElasticsearchSearchTest`(L4,6)、`SearchFallbackTest`(4)、`SearchReconciliationTest`(L4,4)、
    `SearchReconciliationServiceTest`(5)、`SearchIndexWriterTest`、`SearchIndexStageTest`、
    `SearchIndexBootstrapTest`、`SearchQueryBuildingTest`、`ConfigSymmetryTest` 新增一條、ArchUnit 規則 11 擴充
- **偏離事項 / ADR**:16 節見 `docs/architecture/decisions/0028-phase19-elasticsearch-search.md`;
  規格回寫 `00 §0.25`(13 §13.7、09 §9.1、05 §5.3/§5.4/§5.6/§5.8.2、06 §6.3.6/§6.5、01 §1.9、15 §15.2)
- **本 phase 抓到的實質缺陷(值得記住)**:
  1. **§13.7 的搜尋欄位清單漏掉可見度的全部依據**(`ownerTenantId`、`deletedAt`、來源的再散布政策)。
     照字面實作,ES 路徑會整套繞過 `TlpSpecifications` 與 ADR 0015 的 `sourceId` oracle 防護。
     解法是兩層:ES 端完整重建述詞(否則分頁與 `hasMore` 建立在錯誤的候選集合上,
     「本頁少了幾筆」本身就是側信道)+ 回傳前一律以 `findVisibleByIds` 從 PostgreSQL 取回
  2. **`spring-boot-elasticsearch` 一在 classpath 上就會加 actuator 的 ES 健康檢查**——
     ES 只屬 `full` profile,mvp 與 dev 都沒有它。同 Phase 17 的 Redis,但 Redis 屬 `standard,full`,
     當時只需關 mvp;**這次 dev 也要關**
  3. **compose 對 `ELASTICSEARCH_URL` 用空字串預設值,加了 ES 模組之後 mvp 完全無法啟動**
     (`hosts must not be null nor empty`),即使 `SEARCH_BACKEND=postgres`、一個 ES bean 都沒建立
     ——autoconfig 是 Boot 自己的,不受條件裝配影響。守衛只能放**設定層**
     (`ConfigSymmetryTest`);寫成啟動時的 bean 檢查是不可達的,autoconfig 更早失敗(規則 16)
  4. **compose 的 `backend`／`frontend` 沒有 `image:` 鍵,兩個 build target 共用同一個 image 名稱**。
     `docker compose up` 只在 image 不存在時才建置,先 mvp(development)再 staging(production)
     會沿用前者——production 的檔案系統配上 development 的 CMD,兩個容器 crash-loop
  5. **frontend 的 `HEALTHCHECK` 用 `http://localhost/`**,而容器內 `localhost` 只解析到 `::1`、
     nginx 的 `listen 80;` 只綁 IPv4、busybox 的 `wget` 不回退 → production 的 frontend 永遠 unhealthy
     (用 `curl` 手動驗證看不出來,它會回退)。第 4、5 兩項從 Phase 2 就存在,
     但 **M2-25 是 DoD 中唯一會切換 build target、也唯一會實際跑起 production stage 的項目**,
     先前的 phase 都只跑 `--only` 子集,所以到現在才浮現
  6. **`up.sh` 切換環境時不收掉上一個 profile 的服務,gate 跑完一次就重跑不了**:四個環境共用
     同一個 compose 專案名、服務差異只靠 profile,而 M2-25 把環境留在 staging(八個容器)——
     再跑一次時 M1-14「只有三個容器」必然失敗(「預期 3 個,實際 8 個」)。
     ⚠️ **`--remove-orphans` 解決不了**(compose 刻意不把 profile 停用的服務當 orphan,已實測);
     要自己算 `ps --services` 減 `config --services` 的差集再 `rm -sfv`
  7. **M2-22 的判準是空轉通過的**:它是 DoD 全表唯一用 `verify` 的過濾式判準,違反 §15.0 自訂的規則,
     並因此繞過 `dod.sh` 的 `mvn_test` 存在性守衛(ADR 0017)——測試類不存在時 build 成功、該項 `[PASS]`
  8. **全新的 ES 叢集在 05:00 的對帳之前索引是空的,而搜尋照樣回 200 並宣稱 `elasticsearch`**
     ——比降級更糟,降級至少會說出來。`SearchIndexBootstrap` 在啟動後補建(只在索引空而 DB 非空時)
- **給下一 session 的注意事項(M2 里程碑已通過,下一步 Phase 20 = Kafka + 通知)**:
  - **M2 閘門已通過**(`dod.sh phase2` 27/27),可以進入 M3
  - `SearchIndexStage` 目前是 pipeline 內同步(ADR 0020 §3 定調);Phase 20 引入 Kafka 後改非同步時,
    「索引失敗不得使 ingestion 失敗」在兩種模式下都必須成立,而 **AFTER_COMMIT 的消費端一律 `REQUIRES_NEW`**
    (Phase 18 缺陷 3,對 Kafka listener 同樣成立)
  - **自由排序未實作**(§13.7 修訂 3 提到「留待 M2 與 ES 一併設計」):每種排序鍵要一套 cursor 編碼,
    而降級可以發生在翻頁的任何一頁、兩邊 cursor 必須可互換,兩者直接衝突。要做的話得先解這個矛盾
  - Threat 的搜尋不在 Phase 19 的交付物內;`ThreatUpdated` 事件與 `ThreatConsistencyListener` 是現成接入點
  - **`dod.sh` 現在有互斥鎖**,同一個 repo 同時只能跑一個 gate(見下方「收尾強化」);
    但**它擋不住 host 端的 `mvn clean`**——gate 執行期間仍不得在 host 跑任何 Maven 指令
  - **判斷 gate 是否跑完一律用行程結束/退出碼**,不要比對 log 內容:`M2-01` 會巢狀印出
    自己的 `=== 結果` 行。結果行現在帶 gate 名稱(`=== 結果(phase2):…`)以降低誤判
  - `environment/.env.staging` 的 `POSTGRES_*` 密碼必須與 `.env.mvp` 一致:兩個環境共用同一個
    compose 專案名與 `postgres-data` volume,密碼不同會使後切換的那一個認證失敗
    (`up.sh` 現在會偵測並直接告訴你修法)
  - 新的整合測試請自己分配 client IP(本 phase 用 `10.60.0.11/.12`);L4 的 ES 測試共用
    `ElasticsearchTestContainer` 單例,`SearchIndexControl` 提供 refresh / 投毒 / 重建

---

## Phase 19 收尾強化 — 閘門與 up.sh 的可信度(2026-08-29,Phase 19 之後)

- **狀態**:done(使用者指示:把這次踩到的坑做成機制,規格一併更新)
- **Commit**:`04d88dc`(`Tooling: make the DoD gate and up.sh fail loudly instead of misleadingly`)
- **背景**:Phase 19 的實跑本身暴露了三個工具缺陷,它們造成的浪費比 phase 的實作還多。
  不修的話每個後續 phase 都會再付一次。
- **內容**:
  1. **`dod.sh` 互斥鎖**:同一個 repo 同時只能有一個 gate 在跑。兩個 gate 並行會共用
     `backend/*/target`(一邊 `clean` 抽掉另一邊的 classes)、又互搶 Docker 記憶體;
     症狀是 `cannot find symbol: CtipProperties` 與 Testcontainers
     `Timed out waiting for log output`,**完全不像併發問題**,而那一輪的分數是假的。
     鎖以 pid 記錄(殘鎖自動接手);`M2-01` 巢狀呼叫 `dod.sh mvp` 時以 `CTIP_DOD_LOCK`
     傳遞持有權,不重複取鎖也不誤刪。已實測:第二個實例被拒、結束後鎖釋放、巢狀不受影響
  2. **結果行帶 gate 名稱**(`=== 結果(phase2):27/27 通過 ===`)。上面那次並行的起因是
     「等到 log 出現 `=== 結果` 就當成跑完」,而 `M2-01` 的巢狀 mvp gate 會先印一行。
     §15.0 同時明訂:**判斷完成一律用行程結束/退出碼**
  3. **`up.sh` 失敗時印日誌並診斷**:原本只說「未就緒:backend」,而 crash-loop 的容器在 `ps` 裡
     看起來只是「一直在 restart」。本 phase 連續三次靠人工 `docker logs` 才找到原因,
     三種都只有一種成因:`password authentication failed`(共用 volume 的憑證不一致)、
     `hosts must not be null nor empty`(空的 `ELASTICSEARCH_URL`)、
     `mvnw: No such file or directory` / `Could not read package.json`(image 用錯 build target)。
     `_common.sh` 的 `diagnose_startup_failure` 現在把三者翻譯成可執行的修法;
     比對用 bash 的 `case` 而非 `| grep -q`(pipefail 下 SIGPIPE 會使條件假性不成立,同 M1-37 的坑)
  4. **§5.5 開頭補「四個環境共用同一個 compose 專案與具名 volume」的兩個後果**:
     `POSTGRES_*` 必須一致、切換環境要收掉上一個 profile 的服務
- **規格回寫**:`15 §15.0`(dod.sh 契約加兩列 + 修訂 4/5)、`05 §5.5`、`05 §5.10`(第 7 步 + 腳本表)、
  `00 §0.25` 加第 16–19 項、ADR 0028 第 17 節
- **驗證**:互斥鎖三種情境實測 ✅;`dod.sh full --only M3-24` ✅;
  `dod.sh mvp --only M1-01/M1-14`(鎖 + 結果行格式)✅;全部 `.sh` 過 `bash -n` ✅

---
## Phase 20 — Kafka + 通知(WebSocket / SSE / Webhook)`[M3]`

- **狀態**:done(2026-08-29)
- **執行單**:`docs/spec/phases/phase-20.md`
- **Commit**:`614fe2d`(`Phase 20: kafka, notifications and webhooks`)
- **完成判準結果**:全綠 —
  - `test -Ptest-all -Dtest='KafkaEventTest,EventIdempotencyTest,KafkaUnavailableTest,WebhookDeliveryTest,WebhookFilterTest'`
    (逐字)✅ **23/23**
  - `cd frontend && npx playwright test websocket` ✅ **3/3**(M3-05)
  - `clean verify -Ptest-integration` 無過濾 ✅ **931 tests**
    (sdk 13 + core 406 + adapters 33 + app 479;Spotless / Checkstyle / JaCoCo 全過)
  - frontend `tsc` / `eslint --max-warnings 0` / `prettier --check` / `vitest`(155)/ `playwright`(6)✅
  - `dod.sh full --only M3-24`(規格交叉引用)✅
  - **staging 實機驗證**(`up.sh staging`,八個服務全 healthy):
    六個 topic 全部建立且分割數為宣告的 3;註冊 → `TenantCreated`／`UserRegistered` 進
    `ctip.audit.events.v1`(信封五欄齊全、識別碼為字串);提交 IOC → `IndicatorCreated` 進
    `ctip.indicator.updated.v1`,通知投影進 `ctip.notification.events.v1`
    (**severity=HIGH、tags、sourceIds 都是從聚合補齊的**,證明 ADR 0029 §1 的設計對真資料成立),
    consumer 落庫後 `GET /notifications` 讀得到;`GET /events` 回 200 + `text/event-stream`
    (匿名 403);`POST /webhooks` 回 201 且密鑰只此一次
  - **反向驗證(判準不是假綠)**:
    - 拿掉 `WebhookDeliveryService` 的 `events::publish` → `fiveConsecutiveAbandonedEventsDisableTheWebhookAndRaiseWebhookDisabled` 轉紅
      (⚠️ 第一次做這個實驗時只跑了 `-pl ctip-app`,ctip-core 沒重建,結果假綠——
       改動 core 的反向驗證**必須**一併重建 core)
    - `KafkaEventTest` 斷言 topic 的**分割數**而非只斷言存在——正是這條抓到 `List<NewTopic>` 不被 `KafkaAdmin` 讀取
- **交付物**:
  - Flyway `V32`:`webhooks`／`webhook_deliveries`／`notifications` + `notification:read` 權限種子
  - core `domain/notification/`:`Webhook`(W1–W6)、`WebhookFilter`、`HmacSecret`、`WebhookSignature`、
    `WebhookRetryPolicy`、`NotificationEvent`、`EventContext`、`NotificationType`／`WebhookStatus`／`DeliveryStatus`;
    `domain/event/WebhookEvents.WebhookDisabled`
  - core `application/notification/`:`NotificationService`(唯一的 dispatch 入口)、
    `NotificationTransactions`(AFTER_COMMIT 的交易邊界集中處)、`WebhookDeliveryService`、
    `WebhookManagementService`、`NotificationEventFactory`、`NotificationContent`、
    `DeliveryOutcome`／`NewWebhookCommand`／`NotificationRecord`／`WebhookDeliveryAttempt`／`WebhookRequest`
  - core `application/port/`:`NotificationPort`、`WebhookRepository`、`WebhookDeliveryPort`、
    `WebhookSenderPort`、`WebhookPayloadPort`、`RealtimePushPort`、`SecretCipherPort`;
    `QuotaService` 加 `requireWebhookHeadroom`／`requireRealtimePush`
  - app `infrastructure/kafka/`:`KafkaTopics`(六個 topic + 事件對應)、`EventJsonCodec`、
    `ValueObjectJsonModule`、`KafkaEventForwarder`(有界佇列、非阻塞)、`NotificationEventConsumer`
  - app `infrastructure/`:`notification/InProcessEventForwarder`、`scheduling/NotificationSchedulers`、
    `security/AesGcmSecretCipher`、`security/AccessTokenIdentityResolver`(REST 與 WS 握手共用)、
    persistence 三組 entity/repository/adapter + 兩個 native 述句類別
  - app `interfaces/websocket/`:`NotificationWebSocketHandler`、`WebSocketAuthInterceptor`、
    `WebSocketConfig`、`RealtimeSessionRegistry`(含 30s 心跳)、`SseSubscriber`、`RealtimeStreams`、
    `NotificationStreamController`(SSE)
  - app `interfaces/webhook/`:`HttpWebhookSender`、`WebhookHttpRequests`、`WebhookHeaders`、`WebhookPayloadCodec`
  - app `interfaces/rest/`:`NotificationController`、`WebhookController` + DTO／mapper／OpenAPI 介面
  - 設定:`ctip.notification.*`(`NOTIFICATION_TRANSPORT`／`WEBHOOK_SECRET_KEK`／`NOTIFICATION_RETRY_CRON`)、
    `spring.kafka.*`、compose + §5.4 + 五份樣板;`StartupValidator` 的 prod KEK 守衛
  - 前端:`features/notification/`(api／hooks／components,含不依賴 React 的重連狀態機)、
    `pages/NotificationCenterPage`、`pages/WebhooksPage`、路由與導覽、`apiPatch`
  - 文件:`docs/api/events/`(README 對照表 + 兩份 JSON Schema)、`docs/api/webhooks.md`(接收端契約)
  - 測試:`KafkaEventTest`(L4,4)、`EventIdempotencyTest`(4)、`KafkaUnavailableTest`(4)、
    `WebhookDeliveryTest`(5)、`WebhookFilterTest`(6)、`NotificationApiTest`(10)、`RealtimePushTest`(5)、
    `NotificationPipelineTest`(8)、`NotificationEventFactoryTest`(10)、`WebhookAggregateTest`(13)、
    `WebhookFilterSemanticsTest`(6)、`AesGcmSecretCipherTest`(6)、`EventJsonCodecTest`(5)、
    `KafkaTopicsTest`(6)、`WebhookHttpRequestsTest`(2);前端 `notificationStream.test.ts`(10)、
    兩個頁面測試、`e2e/websocket.spec.ts`(3)
- **偏離事項 / ADR**:11 節見 `docs/architecture/decisions/0029-phase20-kafka-and-notifications.md`;
  規格回寫 `00 §0.26`(13 §13.1/§13.2、02 §2.3/§2.4、03 §3.2.9、09 §9.1、
  04 權限清單、10 §10.3、12 §12.5、05 §5.4/§5.5/§5.8.3/§5.10、06 §6.3.6、14 §14.1)
- **本 phase 抓到的實質缺陷(值得記住)**:
  1. **`WebhookFilter` 要的 severity / tags / sourceIds 不在 domain event 上**——它們是多來源合併之後
     才定的,而 §13.1 禁止修改發佈端。照 §3.2.9 的 `matches(DomainEvent)` 字面實作,
     過濾條件永遠比對到空集合。解法是 `NotificationEvent` 投影(W5 不變)
  2. **§13.1 規則 7 照字面實作仍會癱瘓業務路徑**:`KafkaTemplate.send()` 取不到 metadata 時
     **同步阻塞 60 秒**。回 200 卻等一分鐘,與失敗沒有差別 → 轉發移出業務執行緒
  3. **`KafkaAdmin` 看不見 `List<NewTopic>` 型別的 bean**——topic 只能靠 broker auto-create
     (分割數變成預設值),關閉 auto-create 的正式環境則直接沒有 topic
  4. ⚠️ **`up.sh` 從來不重建 image**(Phase 19 加了 `image:` tag 之後的副作用):
     staging 跑的是上一次建置的 jar,而八個服務全 healthy、log 完全正常。
     第一次實跑 staging 時六個 topic 一個都沒建立,最後是看 `/app/app.jar` 的時間戳才找到原因。
     **這個缺陷從 Phase 19 起就影響每一次實機驗證**
  5. **測試 context 的連線池會撞上 `max_connections`**:context 是快取的,每個都有自己的 10 條池。
     新增五個 context 之後整批爆掉,而 `FATAL: remaining connection slots are reserved`
     出現在**後面**才載入的 context 上,看起來完全像那個測試自己的問題
  6. **02 §2.4 的「REQUIRED 會使寫入不落庫也不報錯」在本專案沒有重現**(實測)。
     規則照留但敘述已修正。**反過來**確有一件事必須在交易內做:聚合發出的事件若在
     `REQUIRES_NEW` 交易**外**發佈,會掛到已走完 afterCommit 的交易上而永不觸發
  7. **SSE 的回應標頭要等到第一次寫入才 flush**,`curl -N` 會一直等到逾時 → 開連線後立刻送
     一行 `:keepalive`
  8. **`04` 的權限清單漏掉 `threat:manage`**(Phase 18 只同步了 §10.3 與種子),兩份清單差一項
- **給下一 session 的注意事項(下一步 Phase 21 = Audit Log + 資料保留)**:
  - **M3 的 gate 尚未執行**(`dod.sh full` 25 項);M3-01 要求 mvp 與 phase2 兩個 gate 仍全綠
  - `ctip.audit.events.v1` 已經在收事件(`TenantCreated`／`UserRegistered`／`TokenReuseDetected`／
    `ApiKeyCreated`／`ApiKeyRevoked`／`SubscriptionChanged`／`WebhookDisabled`),
    Phase 21 的稽核消費端直接接上去即可;對照表在 `docs/api/events/README.md`
  - **`V33` 是稽核表**(04 §4.7 已指派);RBAC 種子若要新增權限,依 V31/V32 的慣例寫在建表 migration 內
  - `DELIVERY_CLEANUP_CRON` 與 `INDICATOR_CLEANUP_CRON` 兩個變數**已存在於 compose 與 application.yml
    但仍然沒有任何任務會讀**(ADR 0021 第 6 節列的六項保留任務,Phase 21 交付)
  - 新的整合測試請自己分配 client IP(本 phase 用 `10.70.0.11/.12`);
    **新增 Spring context 時記得它會多佔 4 條資料庫連線**
  - webhook 送達端在測試中一律以 `RecordingWebhookSender` 取代(`WebhookTestConfig`);
    三個 webhook 測試共用同一個 `@Import`,拆開會多起兩個 context
  - **實機驗證前先確認跑的是新 jar**:`up.sh` 現在帶 `--build`,但若手動下 `docker compose up`
    仍要自己加

---

## 總複查 — Phase 1–20(邏輯 / 規格一致性 / 資安 / 弱點)

- **狀態**:done(2026-08-29,使用者指派的跨 phase 複查)
- **Commit**:`2beec20`(message `Review: Phase 1-20 security and consistency fixes`)
- **範圍**:backend 主碼 31.5k 行 / 833 個 Java 檔、frontend 138 檔、規格 16 檔、27 個 migration
- **判準結果**:全綠 —
  - `./backend/mvnw -f backend/pom.xml -Ptest-all verify` ✅(含 Spotless / Checkstyle / JaCoCo / ArchUnit)
  - 新增迴歸測試 5 組:`WebhookTargetTest`(25)、`WebhookTargetGuardTest`(12)、
    `RequestBodySizeLimitFilterTest`(4)、`EndpointClassifierTest` +4、`CorsPreflightTest` +4、
    `UserTest.u7CounterRestartsAfterTheLockExpires`、`NotificationApiTest` SSRF 參數化 5 例
- **修正的五項缺陷**(全數回寫規格 §0.27,逐項理由見 ADR 0030):
  1. ⚠️ **CORS `allowedMethods` 漏 PUT / PATCH** → Phase 18 的兩支 `PUT` 與 Phase 20 的
     `PATCH /notifications/{id}/read` 在瀏覽器端一律 preflight 403,**功能完全打不通**。
     `MockMvc` 不走 preflight,所以測試全綠
  2. ⚠️ **Webhook 送達是未設防的 SSRF 入口** → W1 只要求 https,租戶可指向
     `169.254.169.254`(雲端 metadata)或任意內網位址。加兩道防線(建立時查字串、
     送達前查解析後位址)
  3. ⚠️ **登入鎖定期滿後計數不歸零 → 帳號可被永久鎖定**:每 15 分鐘一個錯密碼即可
  4. **匯入端點的請求本文沒有容器層上限** → 數 GB 本文可耗盡堆積(Tomcat 對非表單本文無預設上限)
  5. **限流的端點分類可被 `%69mport` / `;v=1` 繞過** → heavy 的 5% 變成 write 的 20%
- **檢查過但未發現問題**(下一輪複查可跳過,清單在 ADR 0030 末尾):JWT(無 alg confusion)、
  API key 常數時間比對、refresh token 輪替與重用偵測、RBAC 矩陣與端點授權宣告、
  TLP/再散布的兩份可見度實作、SQL 參數化、`LIKE` 與 ES wildcard 跳脫、Bloom 位元序與 delta 編碼、
  cursor 精度、Kafka 轉發、前端 XSS 與 token 保存
- **給下一 session 的注意事項**:
  - **新增端點時必須同步 `WebCorsConfig.allowedMethods`**——這是第 1 項缺陷的成因,
    而它只有 preflight 整合測試驗得到
  - `WebhookTarget`(domain)與 `WebhookTargetGuard`(送達端)是同一組判定的兩個位置,
    **改其中一邊必須改另一邊**;範圍清單以 `WebhookTarget.isBlockedIpv4/6` 為準
  - `Webhook.reconstitute` **刻意**只驗 scheme(不做完整目標檢查):一列舊資料不得讓
    整個租戶的送達扇出停擺;這不是漏寫
  - `RequestBodySizeLimits.MAX_IMPORT_BYTES` 是 filter 與 controller 共用的常數,不要各寫一份
  - surefire 會把外層 `@Test` 歸到第一個 `@Nested` 類別的報表裡,
    看到 `Tests run: 0` **不代表沒執行**(已實測確認)
  - Phase 21 仍是下一步,本次複查未改動任何 phase 範圍

---

## Phase 21 — Audit Log + 資料保留 `[M3]`

- **狀態**:done(2026-08-30)
- **執行單**:`docs/spec/phases/phase-21.md`
- **Commit**:`53ad108`(`Phase 21: audit log, data retention and admin endpoints`)
- **完成判準結果**:全綠 —
  - `test -Ptest-integration -Dtest='AuditAppendOnlyTest,AuditFailureIsolationTest,RetentionTaskTest,AuditCompletenessTest'`
    (逐字)✅ **13/13**
  - `clean verify -Ptest-integration` 無過濾 ✅ **1,055 tests**
    (sdk 13 + core 459 + adapters 33 + app 550;Spotless / Checkstyle / JaCoCo 全過)
  - frontend `tsc` / `eslint --max-warnings 0` / `prettier --check` / `vitest`(162)✅;`npm run build` ✅
  - `dod.sh full --only M3-24`(規格交叉引用)✅
    (順手修掉 §0.27 留下的兩個壞 anchor:`10-identity-plans.md#104-認證-…` 與 `#107-限流-phase-1417--m2`)
  - **mvp 實機驗證**(`up.sh mvp`,三容器 healthy):
    - `\dp audit_logs` 顯示 `ctip_app=ar`(只有 INSERT/SELECT)、`ctip_retention=d` + 欄位層級
      `id`/`occurred_at` 的 `r`——**與設計完全一致**
    - 以 `ctip_app` 連線下 `DELETE`/`UPDATE audit_logs` → `permission denied for table audit_logs`
    - 以 `ctip_retention` 下保留期 DELETE → 成功;`SELECT action` → `permission denied`
    - 註冊 → `TENANT_CREATED`/`USER_CREATED` 落庫(actorType=SYSTEM);已認證請求 → `API_ACCESS`(帶 ip)
    - `POST /auth/change-password` 回 `{"revokedSessions":1}`,**原本的 refresh token 隨即 401**
      (ADR 0015 的 M3 責任端到端成立)
    - `GET /iocs` 沒有留下 `IOC_QUERY`——正確,`AUDIT_SAMPLE_READ_RATE` 預設 1%
- **交付物**:
  - Flyway `V33`:`audit_logs`(含補上的 `ck_al_action`)+ 四個索引 +
    `REVOKE UPDATE, DELETE … FROM ${appRole}` + 清理角色的**欄位層級**授權(五張表);
    角色名以 Flyway placeholder 帶入(`spring.flyway.placeholders.{appRole,retentionRole}`)
  - core `domain/audit/`:`AuditAction`(26)、`AuditActorType`、`AuditResult`
  - core `application/audit/`:`AuditEvent`(+`AuditMetadata` 憑證遮蔽)、`AuditRecord`、
    `AuditLogQuery`、`AuditActorSummary`、`AuditQueryService`;port:`AuditPort`(非同步寫入面)、
    `AuditLogPort`(append + 查詢 + 行為者摘要)
  - core `application/admin/`:`TenantAdminService`、`SourceAdminService`、`SubscriptionAdminService`、
    `StixRebuildService`、`DataSubjectService` + 兩個例外;`application/identity/PasswordChangeService`;
    `application/stix/StixProjectionFactory`(自 stage 8 抽出,rebuild 與 ingestion 共用)
  - app `infrastructure/audit/`:`AuditWriter`(有界佇列 + 單執行緒 + pending 計數)、`AuditContext`
    (補 ip/ua/traceId/行為者)、`AuditAccessFilter`(chain 尾端)、`AuditEndpoints`(§13.5 對照表)、
    `AuditEventListener`(9 種 domain event)、`AuditSampler`、`AuditSignals`、`AuditClientIp`
  - app `infrastructure/retention/`:`RetentionConnection`(**非 DataSource 型別**的 bean)、
    `RetentionTasks`(五項 SQL,分批 ≤10,000)、`RetentionService`(六項、失敗隔離、記筆數)、
    `RetentionPolicy`／`RetentionReport`;`infrastructure/scheduling/RetentionSchedulers`(六個 cron)
  - app `infrastructure/persistence/`:`AuditLogEntity`／`AuditLogStatements`／`AuditLogAdapter`
  - app `interfaces/rest/`:`AuditLogController`、`AdminTenantController`、`AdminSourceController`、
    `AdminStixController`、`AdminDataSubjectController`、`AuthController` 的 `change-password`;
    DTO／mapper／五個 OpenAPI 文件介面;`ApiExceptionHandler` 兩個新 handler
  - 設定:`ctip.audit.sample-read-rate`、`ctip.retention.{username,password,crons.*}`、
    compose 七個新變數、05 §5.4 清單
  - 前端:`features/audit/`、`features/admin/`(eslint FEATURES 同步)、`pages/AuditLogPage`、
    `pages/AdminPage`、路由與導覽、`api/client.ts` 三個新包裝(無 body 的 POST、帶 body 的 PATCH、
    有回應的 DELETE)、MSW handlers 與 fixture
  - 文件:`docs/deployment/privacy.md`(M3-23 的 12 份必要文件之一,一直不存在)
  - 測試:`AuditAppendOnlyTest`(4)、`AuditFailureIsolationTest`(2)、`RetentionTaskTest`(6)、
    `AuditCompletenessTest`(1,26 種行為逐一驅動)、`AuditRecordContentTest`(6)、
    `AuditEndpointsTest`(17)、`AuditSamplerTest`(4)、`RetentionServiceTest`(3)、
    `AuditActionTest`(2)、`AuditEventTest`(5)、`AuditQueryServiceTest`(1)、
    `PasswordChangeServiceTest`(5)、四個 admin service 測試(11);前端兩個頁面測試(7)
- **偏離事項 / ADR**:12 節見 `docs/architecture/decisions/0031-phase21-audit-and-retention.md`;
  規格回寫 `00 §0.28`(13 §13.4/§13.5、09 §9.1、04 表 27、05 §5.4、12 §12.2/§12.5)
- **本 phase 抓到的實質缺陷(值得記住)**:
  1. **§13.5 規則 2「清理角色無 SELECT 業務表之權限」照字面授權,六項清理全部 `permission denied`**
     ——PostgreSQL 對 `DELETE/UPDATE … WHERE` 仍要求 WHERE 欄位的 SELECT 權限。改欄位層級授權,
     並因此不能用 `ctid` 分批(系統欄位不在欄位授權範圍),改用 `id IN (SELECT … LIMIT n)`
  2. **`SUBSCRIPTION_CHANGED` 原本永不可達**:`Subscription.changePlan`/`cancel` 自 Phase 14 就存在,
     全專案零生產呼叫端,而它是 26 種強制稽核行為之一 → 補 `PATCH /admin/tenants/{id}/subscription`
  3. **稽核不能靠改業務服務**:`AuthService` 與 `RefreshTokenRotator` 的建構子已經是 5 個參數
     (checkstyle 上限),加 `AuditPort` 就違規 → 兩個橫切消費端(filter + event listener),
     業務服務一行不改。副作用是「稽核失敗不影響業務」變成結構保證而非自律
  4. **`AuditWriter.awaitQuiescence` 不能看佇列長度**:工作被取出佇列到 `activeCount` 加一之間
     兩者同時為零,測試因此讀到空表(實測)。改用自己的 pending 計數
  5. **多宣告一個 `DataSource` 型別的 bean 會讓主資料源整個不建立**
     (`DataSourceAutoConfiguration` 是 `@ConditionalOnMissingBean(DataSource.class)`)→
     清理連線包在 `RetentionConnection` 裡
  6. **整合測試互相污染的兩個新來源**:`AuditCompletenessTest` 真的跑一次來源同步會弄髒
     `IngestionEndToEndTest` 的「第一次同步」斷言(它連 `last_sync_at` 都會讓那支少同步一個來源);
     `RetentionTaskTest` 留下的拒絕記錄會讓「拒絕 12 筆」變成 13 筆。兩支現在都自己清乾淨
  7. **`LogCapture` 對背景執行緒不安全**:logback 的 `ListAppender` 收在普通 ArrayList 裡,
     而稽核/Kafka/重試都會在背景寫日誌 → `SecurityTest` 條號 8 拿到 `ConcurrentModificationException`,
     症狀完全像被測程式的問題。改用自己的 `CopyOnWriteArrayList` appender
  8. **compose 的 postgres 服務早就宣告了 `POSTGRES_RETENTION_*`,backend 服務沒有**——
     批次改檔時兩處都命中,產生重複鍵而 `docker compose` 直接拒絕解析(`up.sh` 立刻抓到)
- **給下一 session 的注意事項(下一步 Phase 22 = 監控、日誌、追蹤)**:
  - **M3 的 gate 仍未執行**(`dod.sh full` 25 項);M3-01 要求 mvp 與 phase2 兩個 gate 仍全綠
  - `npm run api:check` 在 commit 之前必然紅(它比對 **committed** 的 generated 型別),同 Phase 16/18/19
  - **新增端點時的三件事**:同步 `WebCorsConfig.allowedMethods`、補 `interfaces/rest/openapi/*Api`
    文件介面(否則 `OpenApiCompletenessTest` 擋)、若屬 §13.5 對照表則在 `AuditEndpoints` 補一列
  - `AuditEndpoints` 是 26 種行為的**唯一**對照表(另 9 種在 `AuditEventListener`);
    改任何一邊都要看 `AuditCompletenessTest` 是否仍全綠——它是真的把 26 條路徑各走一遍
  - 新的整合測試請自己分配 client IP(本 phase 用 `10.80.0.11`、`10.90.0.11`、`10.90.0.21`);
    **會動到共用資料(sources、indicators、ingestion_rejections)的測試必須自己還原**
  - `AUDIT_SAMPLE_READ_RATE` 在整合測試基底固定為 `1.0`(取樣是機率,測試不能靠機率);
    比率本身由 `AuditSamplerTest` 驗
  - **回報三件未做的事**(見 ADR 0031 末段):`TOKEN_CLEANUP_CRON`(08 §8.7,標 M2)至今無實作;
    12 §12.5 的 Settings 頁(`/settings`,M2)不存在,改密碼端點因此還沒有前端入口。
    兩者都屬 M2 的遺漏,建議指派給 Phase 23

---

## Phase 22 — 監控 · 日誌 · 追蹤 `[M3]`

- **狀態**:done(2026-08-30)
- **執行單**:`docs/spec/phases/phase-22.md`
- **Commit**:`1657061`(`Phase 22: monitoring, structured logging and tracing`)
- **完成判準結果**:全綠 —
  - `test -Ptest-all -Dtest='MetricsCompletenessTest,SensitiveLogTest,TracePropagationTest'`(逐字)✅ **23/23**
  - `clean verify -Ptest-integration` 無過濾 ✅ **1,109 tests**
    (sdk 13 + core 459 + adapters 33 + app 604;Spotless / Checkstyle / JaCoCo 全過)
  - frontend `tsc` / `eslint --max-warnings 0` / `prettier --check` / `api:check` / `vitest`(162)/ `build` ✅
  - `dod.sh full --only M3-24`(規格交叉引用)✅、`--only M3-13`(Grafana provisioning JSON)✅
  - 四種環境的 `docker compose config -q` ✅
  - **staging 實機驗證**(`up.sh staging`,八個服務全 healthy):
    - `curl /actuator/prometheus | grep ctip_ingestion_stage_duration` ✅ **40 行**(判準逐字)
    - 六個 `ctip.*` 指標全部有序列;**`lettuce.*`(76 行)、`kafka_consumer_lag`、
      `elasticsearch_cluster_health` 三個在 mvp 測試 context 驗不到的指標,在這裡實測存在**
    - `ctip_source_sync_lag{source="MOCK_OPENPHISH"} 8418.0`、其餘三個來源 `NaN`
      ——語意如設計(從未成功 ≠ 剛剛同步過);`elasticsearch_cluster_health 1.0`(單節點 yellow)
    - `/actuator/{env,beans,configprops,heapdump,metrics}` 全部 **404**
    - 帶 `traceparent` 的請求:錯誤回應的 `traceId` = 傳入的 trace-id;**同一次請求的 JSON 日誌**
      帶同一個 `traceId` + `spanId` + `requestId`(M3-16 端到端成立)
    - JSON 日誌九個欄位齊全(背景執行緒的關聯欄位為空字串,如設計)
    - Prometheus 的 `up{job="ctip-backend"} = 1`——來源 IP 白名單放行 compose 網段
  - **反向驗證(判準不是假綠)**:
    - 拿掉 `IngestionPipeline` 的 stage 計時 → `MetricsCompletenessTest` 轉紅 3 項
      (逐 stage 計時器、`ctip.ingestion.stage.duration` 存在、Prometheus 抓取輸出)
    - 把 `TracingAspect` 的 application service 切入點改成不存在的套件 →
      `TracePropagationTest.oneRequestProducesOneTraceCoveringTheApiServiceAndDatabase` 轉紅
    - 兩次都先 `install` 重建 ctip-core / ctip-app 才跑(Phase 20 的假綠教訓)
- **交付物**:
  - 相依:`micrometer-registry-prometheus`、`spring-boot-micrometer-tracing-opentelemetry` +
    `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`(SDK 模式)、
    `logstash-logback-encoder 9.0`、`spring-boot-starter-aspectj`;ctip-core 新增 `micrometer-core`
  - core `application/observability/`:`CtipMetricNames`(六個 `ctip.*` 名稱的單一來源)、
    `IngestionMetrics`(records{result} + stage.duration{stage},建構時就註冊)、
    `BloomMetrics`、`RedistributionMetrics`
  - core 埋點:`IngestionPipeline`(逐 stage 計時)、`IngestionBatchExecutor`(批次與單筆的筆數)、
    `BloomGenerationService`(per-scope 計時,迴圈自 `BloomSnapshotService.generateAll()` 上移)、
    `RedistributionFilter`(被濾掉的來源明細)
  - app `infrastructure/observability/`:`RateLimitMetrics`、`SourceSyncLagBinder`(MultiGauge)、
    `KafkaConsumerLagBinder`、`ElasticsearchClusterHealthBinder`、`TracingAspect`、
    `TraceIdFilter`(改排在觀測 filter 之後、traceId 取自 span、加 requestId)、`LoggingContextFilter`、
    `PrometheusAccessFilter`、`LogFields`、`CtipJsonEncoder`、`CtipContextJsonProvider`、
    `SensitiveMasks`／`SensitiveValueMasker`／`MaskingMessageConverter`
  - app:`ObservabilityConfig`、`MetricsSchedulers`、`logback-spring.xml`、
    `RedisConfig` 的 lettuce 延遲記錄器、`SearchConfig` 的 ES 健康 binder、
    `StartupValidator` 的 prod actuator 暴露守衛、`CtipProperties.Observability`
  - 設定:`application.yml` 的 `management.tracing.*`／`management.opentelemetry.*`、
    `ctip.observability.*`;compose 與五份樣板新增 `PROMETHEUS_ALLOWED_IPS`／`TRACING_*`;
    staging 的 `ACTUATOR_EXPOSED_ENDPOINTS` 改為含 `prometheus`
  - Grafana:`ctip-overview.json` 由 5 張圖增至 14 張(六個 `ctip.*` 指標 + Kafka lag + ES 健康 + Redis 延遲)
  - 測試:`MetricsCompletenessTest`(16)、`SensitiveLogTest`(4)、`TracePropagationTest`(4)、
    `SensitiveMasksTest`(6)、`CtipJsonEncoderTest`(11)、`PrometheusAccessFilterTest`(4)、
    `RateLimitMetricsTest`(3)、`SourceSyncLagBinderTest`(3)、`TracingAspectTest`(4);`LogCapture` 加 `mdcValues()`、
    新 `LoggingFormats` 測試工具、core 新 `TestMetrics`
- **偏離事項 / ADR**:14 節見 `docs/architecture/decisions/0032-phase22-observability.md`;
  規格回寫 `00 §0.29`(13 §13.6、01 §1.9、05 §5.4/§5.5、06 §6.3.6)
- **本 phase 抓到的實質缺陷(值得記住)**:
  1. ⚠️ **關掉 `management.tracing.export.enabled` 會連「接收傳入的 `traceparent`」一起關掉**——
     Boot 的 `TextMapPropagator` bean 也掛在 `@ConditionalOnEnabledTracingExport` 上。
     沒有 collector 時照直覺關掉全域 export,傳入的 trace 就被忽略、server span 另開一個 trace,
     §13.6 要的唯一關聯線索等於不存在。改為只關 `management.tracing.export.otlp.enabled`
  2. **AOP 切入點不能用整個套件**:`infrastructure.elasticsearch` 內的 `final`
     `IndicatorSearchIndex` 被切到時 CGLIB 建不出代理,**整個 ES context 起不來**
     (症狀出現在 `SearchFallbackTest`,看起來完全像搜尋的問題)
  3. **`logback-spring.xml` 讀 `ctip.environment` 會使 context 起不來**:它的值是 `${ENVIRONMENT}`
     這個必填佔位符,而日誌系統在 environment-prepared 階段就初始化——測試的
     `DynamicPropertySource` 那時還沒進來
  4. **以程式加入的 logback 元件必須自己 `start()`**:Joran 只啟動 XML 裡宣告的子元件,
     漏掉時 `MaskingJsonGeneratorDecorator` 的 delegate 是 null,一寫日誌就 NPE
  5. **判準與規格自相矛盾**:phase-22 的判準是 `up.sh staging` + `curl /actuator/prometheus`,
     而 05 §5.5 把 staging 列為 `health,info` —— 照字面設定判準必然 404(已回寫)
  6. ⚠️ **Prometheus 的 exemplar 與 Lettuce 指標在啟動時死鎖**(最嚴重的一項,ADR 0032 §15):
     exemplar 取樣器會在**記錄指標的那條執行緒**(netty event loop)上向 bean factory 要 `Tracer`,
     而啟動時主執行緒正握著 singleton 建立鎖、在 `RedisConfig` 等 Redis 連線完成——那條連線
     只能由同一個 event loop 完成。`RATE_LIMIT_BACKEND=redis` 的環境(dev / staging / prod)
     **卡在啟動且沒有任何錯誤訊息**。抓到它的是 Phase 17 的 `DistributedRateLimitTest`
     (整個 verify 停在那支測試上),用 thread dump 才看出成因;
     修法 `management.tracing.exemplars.include: none`,並以
     `MetricsCompletenessTest.prometheusExemplarsStayDisabled` 鎖住設定
  7. **`ConfigSymmetryTest` 是有效的守門員**:新增五個 `application.yml` 變數時它立刻抓到
     compose 與 05 §5.4 都沒宣告;其中 `SOURCE_LAG_REFRESH_MS` 因此改為不開放成環境變數
  8. **定義了卻沒被引用的 logback appender 會每次啟動印一則 WARN** → 兩個 appender 各自定義在
     自己的 `<springProfile>` 區塊內
- **給下一 session 的注意事項(下一步 Phase 23 = CI/CD 完整化、安全掃描、文件)**:
  - **M3 的 gate 仍未執行**(`dod.sh full` 25 項);M3-01 要求 mvp 與 phase2 兩個 gate 仍全綠。
    M3-12/13/14/15/16 的判準指令在本 phase 已全部就位並實測過
  - **新增端點時的四件事**:同步 `WebCorsConfig.allowedMethods`、補 `interfaces/rest/openapi/*Api`、
    若屬 §13.5 對照表則補 `AuditEndpoints`,**若新增 `application.yml` 變數則同步 compose 與 05 §5.4**
  - 新的整合測試請自己分配 client IP(本 phase 用 `10.100.0.11`／`.12`／`.13`);
    `TracePropagationTest` 以 `@Import` 帶了一個 `SpanProcessor`,因此**自成一個 Spring context**
  - `TracingAspect` 的切入點只點名 `*Adapter` 與具名類別;**新增 `final` 的 infrastructure 類別
    不會有問題,但把切入點放寬到整個套件會**
  - **不要重新打開 Prometheus exemplar**(`management.tracing.exemplars.include`):
    它與 Lettuce 指標的組合會在啟動時死鎖,而症狀是「啟動很慢」沒有錯誤訊息
  - 一次完整 `verify` 約 25 分鐘,`KafkaUnavailableTest` 的連線逾時就佔掉約 10 分鐘(既有現象)
  - 日誌格式由 profile 決定(mvp/dev plain、staging/prod JSON);要在測試裡驗 JSON 版,
    用 `LoggingFormats.encodeAsJson`,驗實際生效的 appender 用 `encodeWithConfiguredAppender`
  - **仍未做而回報的事**(沿用 Phase 21):`TOKEN_CLEANUP_CRON`(08 §8.7,標 M2)無實作;
    12 §12.5 的 Settings 頁(`/settings`,M2)不存在,改密碼端點仍無前端入口

---

## Phase 23 — CI/CD 完整化 · 安全掃描 · 文件 `[M3]`

- **狀態**:done(2026-08-30)
- **執行單**:`docs/spec/phases/phase-23.md`
- **Commit**:`40d999b`(`Phase 23: CI/CD 完整化、安全掃描、文件`)
- **完成判準結果**:
  - `clean verify -Ptest-integration` 無過濾 ✅ **1,120 tests**
    (sdk 24 + core 459 + adapters 33 + app 604;Spotless / Checkstyle / JaCoCo 全過,5:18 min)
  - frontend `lint` / `format:check` / `tsc --noEmit` / `test`(**178**)/ `build` / `api:check` ✅
  - 四種環境 `docker compose config -q` ✅
  - `dod.sh full` 逐項(可在本機執行者):**M3-13 / M3-17 / M3-20 / M3-21 / M3-22 / M3-23 / M3-24 全 PASS**
  - ⚠️ **`dod.sh full` 整輪未執行**:依 CLAUDE.md,里程碑閘門由**獨立 session** 執行;
    且 **M3-19 在本機必然 FAIL**(見下方「未完成」)。M3-02~M3-16、M3-18 的判準都是
    同一份 `-Ptest-integration` 套件的 `-Dtest=` 子集,已隨上面那輪全綠
  - `.github/**` 的 12 份 YAML 全部通過解析驗證(js-yaml)
- **交付物**:
  - **workflow 11 支**(13 §13.8):新增 `backend-test`、`backend-lint`、`frontend-test`、
    `build`、`docker-build`、`security`、`heavy-test`、`deploy-staging`、`deploy-prod`;
    既有 `compose-validate`、`openapi-check` 不動
  - `.github/dependabot.yml`:maven / npm / github-actions / docker 四個 ecosystem,
    major 不自動開 PR(06 §6.1.2:**不得自行合併版本升級 PR**)
  - 安全掃描:Gitleaks(`fetch-depth: 0`,掃整段歷史)、Trivy fs(相依弱點,`exit-code: 1` +
    `ignore-unfixed`)、Trivy image(兩個 production 映像);**兩個 action 釘 commit SHA**
  - SBOM:parent pom 接上 `cyclonedx-maven-plugin`(`makeAggregateBom` 綁 `package`、
    `includeTestScope=false`);frontend 新增 `npm run sbom`。兩者皆**建置產物不進版控**
  - `ctip-sdk`:`example/ExampleThreatSourceAdapter` + `ExampleAdapterTest`(11 測試)
  - 前端:`/stix/:id` STIX Viewer(`features/stix/graph.ts` 純函式 + `StixGraph`(Cytoscape.js)
    + `useStixGraph` + `StixViewerPage`;10 + 6 測試),cytoscape **3.34.2**,
    **唯一 code-split 的路由**;IOC / 威脅詳情的 STIX 面板加「在 STIX Viewer 開啟」入口
  - `dod.sh`:`dod_ci_green()`(M3-19)、需人工確認清單新增 **P-07**
  - 文件:`docs/architecture/overview.md`、`docs/architecture/security.md`、
    `docs/development/getting-started.md`、`docs/development/plugin-sdk.md`
    (M3-23 的 12 份齊備);`SECURITY.md` / `CONTRIBUTING.md` 擴充
  - ADR **0033–0040**(八則跨 phase 架構決策)+ **0041**(本 phase 決策)
- **偏離事項 / ADR**:9 節見 `docs/architecture/decisions/0041-phase23-cicd-security-docs.md`;
  規格回寫 `00 §0.30`(06 §6.2.3/§6.2.3b、12 §12.6、13 §13.8、15 §15.3/§15.5)
- **本 phase 抓到的實質問題(值得記住)**:
  1. **範例 adapter 的 `SourceType` 無處可放**:四個成員都被真實 adapter 佔用,
     新增 `EXAMPLE` 會留下永不可達的列舉值(規則 16)。解法是放 **SDK 的測試原始碼**並沿用既有成員
     ——CI 照樣編譯與執行(M3-22 要的就是這個),但永不成為 bean
  2. **`deploy-prod` 的人工核准有一半版控檔案表達不了**:required reviewers 存在 GitHub repo 設定,
     綁了 `production` environment 但規則是空的,workflow 照跑——典型的「看起來有、實際沒有」。
     處置:M3-19 至少驗 environment 綁定,其餘列為需人工確認 P-07
  3. **Dependabot alerts 不會擋 PR**:它是 repo 面板。照 §6.2.3b 字面二擇一,CI 上等於沒有
     相依弱點這一道 → 另加 Trivy fs 掃描提供會失敗的訊號
  4. **`text` 欄位不能用 `strip()` 切**:範例 adapter 解析 TAB 分隔的行時,
     `line.strip()` 會把**末欄的分隔 TAB 一起吃掉**,空的最後一欄因此消失(5 欄而非 6 欄)。
     只去 `\r` 才對
  5. **`prettier --check .` 會掃到 `npm sbom` 的產物** → `frontend/.prettierignore` 補 `sbom.json`
  6. **`${{ inputs.* }}` 不得直接內插進 `run:`**(GitHub Actions 的 script injection 面)
     → 經 `env:` 傳入再用 `"$VAR"`
  7. **cytoscape 自帶型別宣告**(`types: index.d.ts`),不需要 `@types/cytoscape`;
     但它約 370 kB,因此 `/stix/:id` 是唯一 `React.lazy` 的路由(主 bundle 896 kB / STIX 頁 445 kB)
  8. **CycloneDX 在多 module 下會產生 5 份 BOM**:聚合的在 reactor 根 `backend/target/bom.json`,
     各 module 另有自己的一份並嵌入 jar 的 `META-INF/sbom/`。判準指的是聚合那一份
- **未完成 / 需要人接手(依規則 17 回報)**:
  1. ⚠️ **M3-19 在本機無法通過**:`gh` 未安裝,且 `git remote`(`ctip`)的 host key 未驗證
     (`git ls-remote` 直接 `Host key verification failed`)——本 repo **從未推上 GitHub,CI 從未跑過**。
     這是 15 §15.3 註明、ADR 0022 明列「沒有歸位」的操作者前置
  2. **首次啟用 CI 時的兩件 GitHub 設定**:建立 `production` environment 並加 required reviewers
     (P-07)、啟用 Dependabot alerts。步驟寫在 getting-started §6
  3. **`TOKEN_CLEANUP_CRON`**(08 §8.7,標 M2)至今無實作——**第三次回報**
  4. **12 §12.5 的 Settings 頁(`/settings`,標 M2)不存在**,`POST /auth/change-password`
     仍無前端入口——**第三次回報**。第 3、4 項屬 M2 遺漏且不在 phase-23 交付物內,故未實作
- **給下一 session 的注意事項(下一步 = M3 閘門 `dod.sh full`)**:
  - **`environment/.env.prod` 已於本機由樣板產生**(M3-17 需要它;隨機佔位值、`chmod 600`、
    在 .gitignore 內未進版控)。**它不是任何真實環境的憑證**,上線前必須重新產生
  - `dod.sh full` 的三項本機前置:M3-17 的 `.env.prod`(已備)、M3-19 的 `gh` + 已推上 GitHub
    (**未備**)、M3-20 的「先跑過一次建置」(BOM 與 sbom.json 目前都在)
  - gate **不得並行**,執行期間**不得在 host 端跑任何 Maven**;判斷結束一律看行程退出碼
  - 新增 workflow 時記得同步 `dod_ci_green()` 的 11 支清單與 13 §13.8 的表
  - 本輪 `verify` 只花 5:18 min(Phase 22 是 25 min)——差別在 Testcontainers 的映像已在本機快取

---

## Phase 23 補件 — 兩項 M2 遺漏(過期 token 清理 · Settings 頁)

- **狀態**:done(2026-08-30,使用者指派)
- **Commit**:`28f402d`(`Phase 23 補件: 過期 token 清理與 Settings 頁(兩項 M2 遺漏)`)
- **背景**:兩項標 `[M2]` 的交付物**不在任何 phase 執行單的交付物清單裡**,因此不會在任何一次
  收尾被抓到,自 Phase 21 起連續三次只被回報(ADR 0031 末段、ADR 0041 §9)
- **完成判準結果**:全綠 —
  - `clean verify -Ptest-integration` 無過濾 ✅ **1,128 tests**
    (sdk 24 + core 464 + adapters 33 + app 607;Spotless / Checkstyle / JaCoCo 全過,5:11 min)
  - frontend `lint` / `format:check` / `tsc --noEmit` / `test`(**186**)/ `build` / `api:check` ✅
  - 四種環境 `docker compose config -q` ✅;`ConfigSymmetryTest` ✅(新變數三處對稱)
  - `dod.sh full` 逐項(可本機執行者)7/8 —— 只有 **M3-19** 因 `gh` 未安裝而 FAIL(同前)
- **交付物**:
  - core `application/identity/ExpiredTokenCleanupService`、
    `RefreshTokenRepository.revokeExpired(now, reason, batchSize)`(新 port 方法)、
    app `infrastructure/scheduling/IdentitySchedulers`、`RefreshTokenJpaRepository` 的批次 native UPDATE
  - `ctip.scheduler.token-cleanup-cron` + `TOKEN_CLEANUP_CRON`(compose、五份樣板、05 §5.4)
  - 前端 `/settings`(`SettingsPage`、`ChangePasswordForm`、`useChangePassword`、
    `authApi.changePassword`、AppLayout 的「設定」入口、MSW handler)
  - 測試:`ExpiredTokenCleanupServiceTest`(5)、`TokenCleanupTest`(3,整合)、
    `SettingsPage.test`(6)、`router.test` +2
  - ADR `0042`;規格回寫 `00 §0.30`、`05 §5.4`、`08 §8.7`、`12 §12.5`
- **本次抓到的實質問題(值得記住)**:
  1. **`EXPIRED_CLEANUP` 與 `ix_rt_gc` 是「schema 先行、實作沒跟上」的證據**:04 表 15 從 Phase 13
     就有這兩樣東西,它們的唯一用途就是這個任務。設計時留下的鉤子若沒有被任何 phase 承接,
     不會有任何測試或閘門發現——只有逐表回讀 schema 才看得出來
  2. **清理任務不刪列**:刪列等於偷偷新增第七項保留政策,而 13 §13.4 只有六項;
     `ip`／`user_agent` 的移除屬資料主體刪除。這一點寫進了 §8.7 與服務 javadoc
  3. **這不是安全邊界**:過期 token 本來就通不過認證(U6 的 `isUsable` 已檢查 expiry)。
     javadoc 明寫此事,否則下一個人會以為刪掉它有安全後果
  4. ⚠️ **`isIdempotent` 是這一組測試裡最重要的一個**:述詞若漏掉 `revoked_at IS NULL`,
     每次清理都會重寫全表並洗掉所有撤銷原因,而任務照樣回報「成功」
  5. **改 `CtipProperties.Scheduler` 會打到兩處測試的建構子**
     (`PlanOverridesInitializerTest`、`StartupValidatorTest`)——record 沒有具名參數
  6. **變更密碼成功後必須清 session**:後端撤銷的是含呼叫端自己在內的全部 family,
     留著會讓使用者在 15 分鐘後莫名被踢出。新密碼要求輸入兩次也是同一個理由——
     打錯字的代價是被鎖在門外,而伺服器端沒有任何攔它的機會
  7. **頁面單獨渲染時測不到 toast**:`Toaster` 掛在 `AppLayout`,頁面測試要驗 `toastSlice` 佇列
- **README 的階段敘述校正(使用者指出)**:
  - 現況表的 `.github/` 與「必要文件」兩列被一個空行切在表格外,渲染成第二張表 → 併回主表
  - `backend/`／`frontend/` 兩列仍寫「🟡 M2 進行中(Phase 13 完成)」並對 Phase 14–22
    逐一標 🟡,而 M2/M3 早已完成 → 改為 `✅ M2(Phase 13–19)完成` / `✅ M3(Phase 20–23)完成`,
    其下逐 phase 不再標記進行中
  - 「這是什麼」仍寫「完成 Phase 1–22、M3 進行中」→ 改為 23 個 phase 全部交付,
    並明說 **M3 的 `dod.sh full` 尚待執行**;「需人工確認 6 項」→ 7 項(P-07)
  - 系統摘要新增第 18 列(CI/CD 與供應鏈安全 + 12 份文件);
    `licensing.md`／`privacy.md` 原標「M3 Phase 23 產出」實為 Phase 19／21 → 更正並補連結
- **給下一 session 的注意事項**:
  - **下一步仍是 M3 閘門 `./environment/scripts/dod.sh full`**(25 項),由獨立 session 執行
  - M3-19 仍必然 FAIL:`gh` 未安裝、repo 從未推上 GitHub(`git ls-remote` 是
    `Host key verification failed`)。這是操作者前置,不是專案交付物
  - `environment/.env.prod` 已於本機由樣板產生(M3-17 需要),隨機佔位值、未進版控
  - 新增排程時三件事:`application.yml` 的 cron、compose 與五份樣板的變數、05 §5.4 的清單
    ——`ConfigSymmetryTest` 三缺一就紅

---

## M3 閘門 — `dod.sh full` 首次實跑(23/25)

- **日期**:2026-08-30
- **結果**:`=== 結果(full):23/25 通過 ===`,失敗 **M3-01**、**M3-19**
- **完整記錄**:`docs/architecture/decisions/0043-gate-run-findings.md`;規格回寫 `00 §0.31`、`13 §13.6`

### M3-01(已修,待完整重跑)

巢狀的 `dod.sh mvp` 是 **37/38**,掛在 **M1-37**(後端 reload);因為 `mvp` 沒全綠,
`&&` 短路使 `dod.sh phase2` **根本沒執行**,M2 那 27 項這輪沒驗到。

**重跑仍失敗,不是 flake。**追下去發現是 **Phase 22 的日誌格式迴歸**:

- M1-37 找 `restartedMain`(**執行緒名**)或 `Started … in … seconds`
- Phase 22 換掉 mvp/dev 的 plain pattern 時**沒帶 `%thread`**(Boot 預設有 `[%15.15t]`)
  → `restartedMain` 永遠不會出現在日誌裡
- 只剩第二條路,而重啟實測要 **12.3 秒** > 10 秒視窗

修法:plain pattern 加回 `[%15.15t]`。M1-37 立刻轉綠;
`SensitiveLogTest`／`CtipJsonEncoderTest`／`SensitiveMasksTest`(20)全過,
完整 `clean verify -Ptest-integration` **1,128 tests 全綠**。

### M3-19(一項已修,兩項待人決定)

裝 `gh` 到 VM 之後才看得到 CI 實況(host 沒有 gh、log API 匿名一律 403):

1. **`openapi-check` 自 2026-08-27 上線起 29 次 run、0 次成功** —— 用了
   `verify -Dtest=<類名>`,`verify` 綁 JaCoCo `check`,只跑一個測試類時
   `ctip-app` 覆蓋率 **0.18** < 門檻 0.60。這正是 ADR 0017 規則 2 說的
   「兩處原本不一致」(`dod.sh` 用 `test`,只有 workflow 用 `verify`)。**已改用 `test`**,
   而且此前從未被執行過的另外三個步驟也在本機逐步驗過(產出一致 ✅、無破壞性變更 ✅)
2. **`security` 沒有壞** —— Trivy 真的掃到四組 HIGH 且上游已有修補:
   `org.postgresql:postgresql` 42.7.11→42.7.12、`httpcore5`/`httpcore5-h2` 5.4.2→5.4.3
   (三者皆 **Boot BOM 納管**)、`eclipse-temurin` 內 `pebble` 的 Go stdlib(8 項)、
   `nginx:1.30-alpine` 的 OpenSSL。**四組全部要動版本**,依 §0.4 規則 6 / 06 §6.1.2
   不得由 AI 自行升版 → **不修、也不放寬門檻**,回報並交回人決定
3. host 端仍未安裝 `gh`(`dod.sh` 是在 host 上呼叫它;VM 那份看不到)

### 本輪最值得記住的一件事

重現 `openapi-check` 的失敗時,**第一次沒有 `clean`,因而複製不出 CI 的失敗**——
JaCoCo 的 `jacoco.exec` 預設 **append**,前一輪完整 `verify` 的覆蓋資料還留在 `target/` 裡把門檻墊過去,
於是「舊指令」在本機是 BUILD SUCCESS。CI 是全新 checkout。
**要重現 CI 的覆蓋率失敗,本機必須先 `clean`** —— 少了它會得到一個看起來像「修好了」的假綠。

### 給下一輪的注意事項

- **M3-01 需要完整重跑**(mvp 38 + phase2 27),本輪只單獨驗過 M1-37
- **M3-19 需要三件事**:`security` 的四組弱點被處理(人決定是否合併 Dependabot PR)、
  修好的 workflow 推上去重跑一次 CI、host 安裝 `gh`
- Dependabot 目前有 **14 個開啟中的 PR**(其中 #12 spring-boot-starter-parent 同時解掉
  postgresql 與 httpcore5 兩組);**AI 不得自行合併**(06 §6.1.2)
- **git 操作走 VM**(`/Users/yusen/workspace/vagrantBox/ubuntu_20_lts`,專案在
  `/home/vagrant/java/TIP/CTIP`);host 的 SSH known_hosts 未設定,`git ls-remote` 會失敗。
  VM 裡已裝 `gh 2.98.0` 並完成 `gh auth login`

---

## 弱點處置 + README Phase 一覽(2026-08-30,閘門後)

- **Commit**:見 git log(`Security: ...`)
- **完整記錄**:`docs/architecture/decisions/0044-security-findings-remediation.md`;規格回寫 `06 §6.2.2`

### CI 現況(使用者推上 `e94f4b2` 後)

`openapi-check` **completed/success —— 30 次 run 以來第一次綠**(ADR 0043 §1 的修法在 CI 驗證通過)。
其餘 `compose-validate`／`backend-lint`／`frontend-test`／`build`／`docker-build`／`deploy-staging` 全綠;
`security` 仍紅(弱點當時尚未處置)。

### 第一類弱點:一個 patch 升版全解(已修)

三個 CVE 全落在 **Boot BOM 納管**的傳遞相依上,而 06 §6.1.3 規則 1 禁止硬寫納管者的版本
→ **正確修法只有升 parent**。`spring-boot-starter-parent` **4.1.0 → 4.1.1**:

| 元件 | → | CVE |
|---|---|---|
| `org.postgresql:postgresql` 42.7.11 | **42.7.13** | CVE-2026-54291 |
| `httpcore5` / `httpcore5-h2` 5.4.2 | **5.4.3** | CVE-2026-54399 / -54428 |

版本以 `mvn dependency:list` **實測**確認(不是照 release note 推斷);
`clean verify -Ptest-integration` **1,128 tests 全綠**;前端六項全綠(186)。

### 第二類弱點:基底映像(未修,回報)

`eclipse-temurin:25-jre` 內 `pebble` 的 Go stdlib(8 項)、`nginx:1.30-alpine` 的 OpenSSL
——**這個 repo 沒有任何動作可做**,映像每次 CI 重建,上游一修補就會自動消失。

⚠️ **Dependabot PR #1(nginx 1.30-alpine → 1.31-alpine)不得合併**:
1.31 是**奇數 minor = mainline**,而 00 §0.6 修正版本錯誤時的理由逐字就是
「1.29 是 mainline(奇數 minor)且已退役」,本專案明確選 stable 分支。應關閉該 PR。

### 沒有做的事

**沒有放寬 `security.yml` 的門檻**。但結構性問題仍在:第二類的兩組無法在本 repo 修,
卻會擋住每一個 PR。要處理的話應把「應用相依(擋)」與「基底映像(只回報)」分兩道
——那是政策決定,應由人決定後寫進 13 §13.8。

### 本機 Trivy 重驗的坑

`docker run aquasec/trivy fs …` 在本機**跑不完**:它會連 Maven Central 解析 pom,
撞到 **429 Too Many Requests(Retry-After: 1800,且會封鎖該 IP 後續請求)**。
要在本機重驗必須把 `~/.m2` 掛進容器。CI 端不受影響(runner IP 不同、且有 maven cache)。

### README:補上 Phase 一覽(23 個)

使用者指出「專案有 23 個 phase,但 README 表格看不到 23 個」。逐表檢查後,
既有的三張表(Maven module／domain 模組／前端 feature)的 Phase 欄本來就涵蓋到 23,
**真正缺的是一張把 23 個 phase 逐一列出的總表** —— 那份清單此前只存在於
`00-master.md §0.5`,README 沒有。已在「系統摘要」下新增
**「Phase 一覽(23 個)」**,含每個 phase 的內容、里程碑、狀態,以及三個閘門的結果列。

### 給下一輪的注意事項

- **M3-19 仍待**:需要 `security` 轉綠(視上游是否已重建基底映像)、修好的 commit 推上去、
  以及 **host 安裝 `gh`**(`dod.sh` 在 host 上呼叫它,VM 那份看不到 —— VM 是另一台機器,
  有自己的檔案系統與 PATH)
- **M3-01 需要完整重跑**(mvp 38 + phase2 27);M1-37 已修並單獨驗過
- Dependabot 仍有 14 個開啟中的 PR;**#1 應關閉**(理由見上),**#12 已由本次升版涵蓋**

---

## 全專案複查 — 程式 vs 規格(2026-08-30)

- **完整記錄**:`docs/architecture/decisions/0045-full-project-review-doc-sync.md`;規格回寫 `00 §0.32`
- **使用者指示**:逐一 review 整個專案程式碼、確認產出的程式與規格一致、規格或 README 有需要更正的就更正

### 程式端:無偏離,建置全綠

機械比對能對的全對:Flyway 建的 28 張表 vs `04`、`openapi.json` vs `09 §9.1`、
RBAC 種子 vs `10 §10.3`、`AuditAction` vs `13 §13.5`、TLP 2.0 五個 marking UUID vs `07 §7.8.4`、
Bloom 位元布局 vs `11 §11.4`、ArchUnit 11 條 vs `01 §1.9`、DoD 90 項 vs `15`。

實測:後端 `clean verify -Ptest-integration` **1,128 tests 全綠**(Spotless／Checkstyle／ArchUnit／JaCoCo 門檻都綁在 `verify`);
前端 `lint` 0 warning、`build` 通過、`test` **186 全綠**、`api:check` 無型別漂移。
`09 §9.1` 列的 54 個端點與 `openapi.json` 的 53 個 HTTP operation 完全對得上(差的是 `GET /ws`,
WebSocket 升級不是 OpenAPI operation)——**沒有多做、也沒有少做**。

### 本輪最值得記住的一件事

**問題全部集中在「規格宣告了自動化,而那個自動化不存在」。**

`03 §3.3` 的 ERD 標著 🔴 **規範·自動驗證**,內文寫「由 CI 比對 Flyway 產生的 schema 與 04」——
`dod.sh` 與 11 支 workflow 裡**沒有任何一步做這件事**。
結果是 Phase 14 新增的 `import_jobs` 只進了 `04 §4.3` 的欄位定義與 `§4.7` 的 `V28` 對應,
而 §4.1 的表清單、§3.3 的 ERD、兩處「27 張表」的計數全部漏了同步,
**連續九個 phase(14→23)沒有任何檢查變紅**。

這與 ADR 0016 第 3 項是同一類:一個假的守門比明說「這裡沒有守門」更危險。
反過來看,RBAC 之所以沒出現同類漂移,是因為 `RbacMatrix.parseSpecificationTable()`
**直接 parse 規格的 markdown 表**——種子與規格任一動了就紅。**計數要靠測試釘,不能靠人記得改。**

### 處置

- 新增 `DataDictionaryConsistencyTest`(L3,`@Tag("integration")`):§4.1 清單、§3.3 ERD、
  04 檔尾「表數：n」三者**雙向**比對 `pg_tables`(規格漏登、建表沒寫規格,兩邊都紅)。
  **否定驗證**:移除 `import_jobs` → 三項全紅;還原後全綠。CI 由 `backend-test.yml` 的
  `clean verify -Ptest-integration` 涵蓋。**不新增 DoD 項目**(「90 項」是契約)
- 規格計數同步:表 27→**28**、端點 43→**54**、domain event 19→**21**、pipeline stage 10→**12**、
  `spec/README` 索引表七處計數 + 行數欄重算、`05 §5.1` 結構契約補 `openapi-breaking-check.py`
- README:二十五→**二十六輪**／§0.7–**§0.32**、ADR 0001–**0045**、8→**9 支腳本**、27→**28 張表**、
  M3 閘門列補上 ADR 0044 之後的實況(`openapi-check` 已在 CI 轉綠、三個 CVE 已由 Boot 4.1.1 解掉)

### 依規則 17 回報(1 項,未處置)

`WebhookTargetGuard` 判定後、`HttpClient` 送出時**會再解析一次 DNS**,兩次之間有理論上的 rebinding 窗口。
根治要把已判定的 IP 釘進連線,會動到 HTTP client 的裝配方式;現況已是兩道防線且 JVM DNS 快取使窗口極窄。
**不自行改動**,交回使用者定調。

### 給下一輪的注意事項

- **M3-01 仍需完整重跑**(mvp 38 + phase2 27);**M3-19 仍待** host 安裝 `gh` 與 CI `security` 轉綠
  (剩餘兩組弱點在基底映像,上游重建即消失)
- Dependabot 仍有開啟中的 PR;**#1 應關閉**(nginx 1.31 是 mainline,見 `00 §0.6`)
- 下次若再新增資料表,`DataDictionaryConsistencyTest` 會強迫你同時改 §4.1、§3.3 與檔尾計數

---

## M3-01 完整重跑 + README 重構 + demo 補拍(2026-08-30)

- **完整記錄**:ADR `0046`(README 重構)、`0047`(M1-02 優化);規格回寫 `15 §15.1`
- **使用者指示**:三件事——把 M3-01 完整重跑一次、更新 readme demo、README 太冗長要重新精簡優化

### 1. M3-01:通過 ✅

```
=== 結果(mvp):38/38 通過 ===      MVP_EXIT=0
=== 結果(phase2):27/27 通過 ===   PHASE2_EXIT=0
```

零失敗,用時 **76 分鐘**(17:11:36 → 18:27:57)。判準是 `dod.sh mvp && dod.sh phase2`,兩邊都綠才算過。
上一輪掛的 M1-37(後端 reload)在**完整重跑**下確認修好,不是單項驗過就算。

### 2. 為什麼跑這麼久 —— 以及一個真的浪費(已修)

使用者問「為什麼會等這麼久」。沒有卡住,三個結構性原因疊加,其中**一個是純粹浪費**:

- mvp gate 會跑兩次(M3-01 = `mvp && phase2`,而 phase2 的 M2-01 又是 `dod.sh mvp`)——**規格明文的回歸設計,正確**
- 45 次獨立的 `-Dtest=` maven 呼叫,各付 JVM + reactor + Spring context + Testcontainers——**§15.0 逐項歸因的必然代價,正確**
- ⚠️ **M1-01 與 M1-02 是逐字相同的指令**,實測各 **5:00**。規格指令欄寫「同上(JaCoCo check 綁在 verify)」,
  語意是**一次執行同時證明兩件事**,`dod.sh` 卻照字面又跑了一次

M1-02 改為 `jacoco:check@check`(綁 pom 裡那個 execution,用的是**完全相同的 rules**,
不是 plugin 預設值)。**5 分 00 秒 → 4.216 秒**,而且驗得比原本多——原本只是「同一指令再退出 0 一次」。

**三項實測驗證**:正向 `[PASS]` 4.2 秒;否定 1(門檻調 0.999)`[FAIL]` 並逐套件印出實際值
(`domain.notification` 0.858、`stix` 0.891、`indicator` 0.971、`normalization` 0.943)——
證明套用的是 `ctip-core` 的 PACKAGE override;否定 2(移走 `jacoco.exec`)`[FAIL]`。
**回歸**:完整 `dod.sh mvp` 38/38、EXIT=0(且 M1-38 對新 README 也通過)。

⚠️ **假綠風險是實測確認的**:裸 `jacoco:check@check` 在沒有覆蓋率資料時 **exit 0**,
所以那道「四個 module 的 jacoco.exec 都要存在」的守衛是必要的,不是裝飾。

⚠️ **不能宣稱整輪省了多少**:mvp gate 牆鐘 26:08 → 25:08,只少 1 分,與單項的 5 分對不上。
兩輪起始環境不同(第二輪緊接在 phase2 之後,M2-25 把環境留在 staging,M1-14 要先收掉五個容器),
而 `dod.sh` 的 `check` 只在失敗時印出被捕捉的輸出,**無法從日誌證實**。只主張單項數字。

### 3. README:59.8 KB → 14.1 KB

三個巨大表格儲存格(`backend/`／`frontend/`／`.github/` 的沿革)佔了約 34 KB。
成因是 README 自己寫的「本檔隨里程碑擴充而不覆寫」——施工期正確,施工結束後讓 README 變成
append-only 的施工日誌。**那條規則已移除**(理由見 ADR 0046)。

- 「系統摘要」(18 列能力表)與「Phase 一覽」(26 列)**合併**為依里程碑分節、一列一個 phase 的三張表;
  **23 個 phase 仍逐一列出**(折疊會退回使用者上次指出的「看不到 23 個」)
- 沿革 → 新增 **`docs/history.md`**;「CI/CD」整段與 `getting-started.md` §6 幾乎逐字重複,改為指路;
  「疑難排解」搬進 `getting-started.md` §3;快速開始的 20 列功能導覽移入 `docs/demo/`
- 文件分工:README(現在是什麼)→ history(怎麼走到這裡)→ progress(逐 phase 判準)→ ADR(決策理由)

### 4. demo 補拍 11 張,並因此抓到兩個實質缺陷

`docs/demo/` 原本只有 M1 四張(2026-08-27),M2/M3 的 15 個頁面從未出現。補拍後共 15 張。

**截圖是一種沒有測試在做的檢查**——它逼你真的去看每一個頁面。兩個缺陷因此浮出來(皆已修):

1. `ForbiddenState` 的文案寫「**M1 尚未開放註冊與登入**」——那是 Phase 12 的實況,
   而登入/註冊**自 Phase 13 就存在**。§12.6 #4 要顯示原因,顯示一個不成立的原因比空白更糟
2. **STIX Viewer 在最常見的情況下看起來像壞掉** —— cytoscape 的 `fit()` 無 zoom 上限,
   單節點無關聯(目前全部的 indicator)會放大到標籤撐爆畫布;且 `text-wrap: 'ellipsis'` 不處理 `\n`,
   程式碼刻意組的兩行標籤從來沒生效過。加 `minZoom`/`maxZoom`、改 `wrap` + 長名稱截短

### 給下一輪的注意事項

- **拍前端截圖的坑**:token **只存記憶體**(避免 XSS),Playwright 每次 `page.goto()` 的整頁重載
  都會清掉登入狀態,拍出來全是「需要登入」。登入後必須走 SPA 內導航(`history.pushState` + `popstate`)
- 截圖用的示範租戶手動指派了 `ENTERPRISE` + `SYSTEM_ADMIN`,威脅資料由 `POST /threats` 建立
  ——兩者**都已在 `docs/demo/README.md` 標明**,不是預設狀態
- **M3-19 仍待**:host 安裝 `gh`、CI `security` 轉綠(剩兩組弱點在基底映像,上游重建即消失)
- M1-02 優化後,下一次乾淨的 `dod.sh full` 才量得到真正的節省幅度

---

## gh 安裝 + M3-19 假綠 + backend-test 順序相依(2026-08-30)

- **完整記錄**:[ADR 0048](architecture/decisions/0048-ci-green-and-test-isolation.md);規格回寫 `15 §15.3` 註 ²
- **起點**:使用者問「host 安裝 gh 不會影響我本地的 git 嗎」「vm 裡有安裝了不行嗎」,續以「我想徹底解決問題」

### 先更正一個前提:host 與 VM 是**同一個 repo**

`Vagrantfile:55` 把 `/Users/yusen/workspace/java/` 掛成 VM 的 `/home/vagrant/java/`(synced folder)。
兩邊同一份工作目錄、同一個 `.git`、同一個 remote。**不是兩個 remote。**

VM 的 gh 實測可用,但 **VM 缺 docker / node / npm / jq**,`dod.sh full` 主體跑不了 → host 需要 gh。

### 1. gh 裝在 host,且對 git 零影響(已驗證)

`brew install gh` 只放 binary,不碰 git。**唯一的風險是 `gh auth login` 的那一題**
(「Authenticate Git with your GitHub credentials?」),答 yes 會在 `~/.gitconfig` 寫入
`credential."https://github.com".helper`,影響**所有**用 HTTPS github remote 的 repo。

**改用 `GH_TOKEN` 環境變數**(gh 官方支援,不寫入任何設定檔,連 `~/.config/gh/hosts.yml` 都不產生)。
token 為 fine-grained PAT,僅 `YSShih/CTIP` + **Actions: Read-only**,
存於 `~/.config/ctip/gh-token`(0600,**repo 外**,gitleaks 掃不到),由 `~/.zshrc` 帶入。

零影響證據:`git config --global --list` diff 無差異、`~/.gitconfig` sha 相同(`8f14859…`)、
`credential.helper` 仍是 `osxkeychain`、`~/.config/gh` 不存在、remote 未被修改。

⚠️ **`zsh -lc`(非互動 login shell)不會讀 `.zshrc`**,驗證要用 `zsh -ic`。

### 2. M3-19 的假綠(已修)

`gh run list --limit 1` 只看**最近一次 run**,而九支 workflow 在同一次 push 同時觸發,
「最近一次」是哪一支基本上是任意的。**實測抽到 `build`(success)→ M3-19 回報 PASS**,
而同一個 commit 上 `security` 與 `backend-test` 都是 failure。

與 [ADR 0022](architecture/decisions/0022-orphan-deliverables.md)「只有兩支且都綠也會通過」同一形狀,
只是換到 run 結論這一半——當時補了檔案存在性,沒動 run 結論,同一類缺陷又躲一次。

改為:**HEAD 的九支 push 觸發 workflow 全部 `completed` + `success`**
(排除 `deploy-prod` 只有 dispatch、`heavy-test` 是 schedule——實測對 HEAD 無 run,列入必檢會永遠 FAIL)。

### 3. `backend-test` 19 次 run、0 次成功(已修)

**先前的複查從未檢查過它** —— `progress.md:2036` 列了六支綠 + security 紅,backend-test 不在任何一邊。
它能一直躲著,正是因為第 2 點的 `--limit 1`。

根因:整個 `ctip-app` 共用同一個 Testcontainers PostgreSQL,而 surefire 未設 `runOrder`、
預設 `filesystem` —— **APFS(本機)與 ext4(CI runner)給的測試類順序不同**,所以本機全綠、CI 全紅。

**三個順序相依的測試,三種不同成因**:

| 測試 | 成因 | 修法 |
|---|---|---|
| `NotificationApiTest` | 斷言絕對值(`items[0]`、`length()==0`),但可見度是 `tenant_id IN (自家, public)`,而來源事件的 `SOURCE_FAILURE` 掛 public tenant、**對每個租戶都可見** | `@BeforeEach` 清 `notifications`,讓「跨租戶看不到」這個意圖真的成立 |
| `IngestionEndToEndTest` | 只在 `@AfterAll` 收拾,**沒在 `@BeforeAll` 建立前提**;斷言絕對筆數卻假設自己先跑 | `@BeforeAll` 清 `ingestion_rejections` / `source_sync` |
| `SampleDataIntegrationTest` | **斷言本身寫錯**:用 `containsExactlyInAnyOrder` 宣稱 public tenant「只有」CLEAR/GREEN,把**種子的歸屬規則**當成全表不變量 | 改 `contains` |

第三項一度被我懷疑是規格違反,追下去**不是**:`ThreatIntegrationTest` 測的是 H6 ——
已發布的公開 IOC 被新來源以更嚴格 TLP 回報 → 合併收緊 → `IndicatorTlpTightened`。
**TLP 事後收緊是合法行為,且不改變擁有權**,public 持有 AMBER 沒有違反任何不變量。

**不採用「把 runOrder 釘死」當修法** —— 那只是把今天的巧合凍結,下一個新測試照樣踩,
而且會以更難理解的方式踩(「為什麼加一個測試會讓另一個不相關的測試變紅」)。
固定順序只當**重現工具**。

### 本輪最值得記住的一件事

**只驗「原本會紅的那個順序」會漏掉東西。** 修完 `NotificationApiTest` 後 `alphabetical` 轉綠,
但 `reversealphabetical` 立刻抓出 `SampleDataIntegrationTest` —— 成因完全不同。
**順序無關才算修好,只驗預設順序等於沒驗。**

### 給下一輪的注意事項

- **M3-19 仍會 FAIL**,因為 `security` 紅。剩兩組弱點在基底映像,本 repo 無動作可做
  ([ADR 0044](architecture/decisions/0044-security-findings-remediation.md))。
  要讓它能綠,得先決定那個**政策問題**:把「應用相依(擋)」與「基底映像(只回報)」分兩道,
  寫進 `13 §13.8`。**那不由 AI 定調。**
- `backend-test` 的修法要**推上去由 CI 確認**才算數——本機三種順序全綠只是必要條件
- 新增整合測試時:**斷言絕對值(筆數、`items[0]`、`containsExactly`)就必須自己建立前提**,
  因為整個 `ctip-app` 共用一個資料庫

---
