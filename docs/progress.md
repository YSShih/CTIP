# CTIP 實作進度(跨 session 交接檔)

> append-only。每個 phase 完成後由該 session 更新。新 session 開場先讀這裡。
> Phase 順序與內容見 `docs/spec/00-master.md` §0.5。

## 總覽

| Milestone | Phase | 狀態 |
|---|---|---|
| M1 — MVP | 1–12 | **完成(dod.sh mvp 38/38)** |
| M2 — Platform | 13–19 | 進行中:Phase 13 完成,下一步 Phase 14 |
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
