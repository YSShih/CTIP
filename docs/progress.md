# CTIP 實作進度(跨 session 交接檔)

> append-only。每個 phase 完成後由該 session 更新。新 session 開場先讀這裡。
> Phase 順序與內容見 `docs/spec/00-master.md` §0.5。

## 總覽

| Milestone | Phase | 狀態 |
|---|---|---|
| M1 — MVP | 1–12 | Phase 3 完成,下一步 Phase 4 |
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
