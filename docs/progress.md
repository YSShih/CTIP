# CTIP 實作進度(跨 session 交接檔)

> append-only。每個 phase 完成後由該 session 更新。新 session 開場先讀這裡。
> Phase 順序與內容見 `docs/spec/00-master.md` §0.5。

## 總覽

| Milestone | Phase | 狀態 |
|---|---|---|
| M1 — MVP | 1–12 | Phase 8 完成,下一步 Phase 9 |
| M2 — Platform | 13–19 | 未開始 |
| M3 — Production | 20–23 | 未開始 |

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
