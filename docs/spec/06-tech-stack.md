# 06 — 技術棧與版本政策

> **規範等級：強制。** 版本表與版本政策不得由 Coding LLM 自行變更。
>
> 相關檔案：[05-environment.md](05-environment.md)、[14-testing.md](14-testing.md)

---

## 6.1 版本政策（強制）

### 6.1.1 分級支援窗口政策

**「最新 LTS」不是可執行的指令**——本技術棧中有 LTS／stable 分支制度的只有少數專案（Java、Node.js、nginx、PostgreSQL、Valkey）。Spring Boot、React、Vite、TypeScript、ESLint、Redis、Elasticsearch、Kafka 等**沒有 LTS 制度**。因此政策分兩級：

| 情況 | 規則 |
|---|---|
| **上游有 LTS 或 stable 分支制度** | **必須** pin 該分支的最新版。例：Java 取 LTS（25）而非最新（26）；nginx 取 stable（偶數 minor，1.30）而非 mainline（奇數 minor） |
| **上游無 LTS 制度** | pin 最新 GA minor，且其支援窗口須延伸至**規劃的 M3 完成日 + 12 個月**之後。不使用 beta／RC／preview |

**任何版本都必須在 pin 當下處於上游 active support 窗口內。** 已 EOL 或已退役分支視為規格違規。

### 6.1.2 凍結與浮動（強制）

| 類別 | 規則 |
|---|---|
| **Docker image tag** | 用 **major 浮動 tag**（`postgres:18-alpine`、`nginx:1.30-alpine`、`redis:8-alpine`），自動吃到安全修補 |
| **Maven 相依** | 由 Spring Boot BOM 納管者**不得**寫版本號；未納管者於 parent pom `<properties>` 精確指定 |
| **npm 相依** | `package-lock.json` 鎖定，CI 用 `npm ci` |
| **升版權限** | **Coding LLM 不得自行升版任何 Maven／npm 相依，只能回報過期。** major 升級須人工核准並寫 ADR |

> Maven 沒有 lockfile，因此後端必須以 `mvn versions:display-dependency-updates` 定期複查（見 6.4）。v1.1 對前端要求了 `npm ci`，對後端沒有等價要求——本節補上。

### 6.1.3 其他規則

1. 凡 Spring Boot BOM 已納管的相依（Flyway、Testcontainers、Jackson、Hibernate、PostgreSQL JDBC、Lettuce、Micrometer、spring-kafka…），**不得**在 pom 硬寫版本號。刻意覆寫時必須加註解說明原因
2. 未納管的相依（springdoc、MapStruct、Resilience4j、Bucket4j、ArchUnit…）必須在 parent pom 的 `<properties>` 集中定義
3. 啟用 Dependabot 或 Renovate：patch/minor 自動開 PR、major 需人工審核

---

## 6.2 版本表

> **pin 日：2026-08-21。首次複查日：2027-02-21。**
>
> **查證狀態（誠實標示）**：以下 19 項已於 2026-08-21 對 Maven Central / npm registry / 上游官方來源逐一查證 —
> Java 25、Node 24、Spring Boot 4.1.0、springdoc 3.1.0、MapStruct 1.6.3、React 19.2.8、Vite 8.2.2、TypeScript 7.0.2、
> React Router 8.3.0、ESLint 10.8.1、Vitest 4.1.11、Tailwind 4.3.3、TanStack Query 5.101.4、Zod 4.4.3、
> PostgreSQL 18.6、Redis 8.10、Valkey 9.0.4、Kafka 4.2.1、Elasticsearch 9.5.1、nginx 1.30.4。
>
> 其餘項目（Resilience4j、Bucket4j、ArchUnit、Spotless、Checkstyle、JaCoCo、`@vitejs/plugin-react`、
> `@hookform/resolvers`、React Hook Form、lucide-react、Recharts、Cytoscape.js、`@testing-library/react`、
> Playwright、openapi-typescript、MSW、Prettier、`eslint-plugin-import`、Prometheus、Grafana）
> **僅標出 major 系列，支援終止日為推估**。這些套件的 major 選擇風險低，但**複查日必須逐一查證**（§6.4）。
> 「支援終止」欄標 ✅ 為已查證，標 ~ 為依上游釋出節奏推估。**推估值在複查日必須重新查證。**

### 6.2.1 Runtime / Platform

| 項目 | 版本 | 分支類型 | 支援終止 | 備註 |
|---|---|---|---|---|
| Java | **25**（Temurin） | **LTS** | ~2029-09 ✅ | JDK 25 為現行 LTS（至少 4 年支援）。**不使用 26**（非 LTS）。最低相容 21 |
| Maven | 3.9.x | — | — | 使用 Maven Wrapper，版本進版控 |
| Node.js | **24** | **Active LTS** | 2028-04-30 ✅ | ⚠️ 見 6.3 Node 改制 |
| npm | 隨 Node 24 附帶 | — | — | |
| Docker Engine | 27+ | — | — | Compose v2.24+（需完整 profiles 語意） |

### 6.2.2 Backend

| 套件 | 版本 | 支援終止 | 備註 |
|---|---|---|---|
| Spring Boot | **4.1.0** | 2027-07-31 ✅ | 2026-06-10 釋出。**無 LTS 制度**；OSS 支援 12 個月，之後僅 Tanzu 商業支援 |
| Spring Framework | 7.0.x | 隨 Boot | 由 BOM 決定 |
| Spring Security | 7.1.x | 隨 Boot | 由 BOM 決定 |
| Hibernate ORM | 7.4.x | 隨 Boot | 由 BOM 決定 |
| Flyway | 由 BOM 決定 | 隨 Boot | 不硬寫版本 |
| Testcontainers | 由 BOM 決定 | 隨 Boot | |
| JUnit 5 (Jupiter) | 由 BOM 決定 | 隨 Boot | |
| Mockito | 由 BOM 決定 | 隨 Boot | |
| Micrometer + OpenTelemetry | 由 BOM 決定 | 隨 Boot | |
| springdoc-openapi | **3.1.0** | ~隨 Boot 4 | 3.x 才相容 Spring Boot 4。**不得使用 2.x** |
| MapStruct | **1.6.3** | ~ | 已查證：1.7.0.Beta2（2026-06）仍未 GA，不使用。**複查日確認 1.7 是否已 GA** |
| Resilience4j | 2.3.x | ~ | circuit breaker / retry / timeout / bulkhead |
| Bucket4j | 8.x | ~ | 限流；Redis 後端用 `bucket4j-redis` |
| ArchUnit | 1.4.x | ~ | 分層規則測試 |
| Spotless (maven-plugin) | 2.x | ~ | 格式化，搭 palantir-java-format |
| palantir-java-format | 2.x | ~ | **確定性格式化**：兩個 AI session 產出位元相同的排版 |
| Checkstyle (maven-plugin) | 3.x | ~ | 僅五條規則，見 [01](01-architecture.md#18-可讀性硬性規則與執行機制) |
| JaCoCo | 0.8.x | ~ | 覆蓋率門檻 |
| ~~Lombok~~ | **不使用** | — | **見 6.3.1** |

### 6.2.3 Frontend

| 套件 | 版本 | 支援終止 | 備註 |
|---|---|---|---|
| React | **19.2.x** | ~ | |
| Vite | **8.2.x** | ~ | |
| TypeScript | **7.0.x** | ~ | Go 重寫的編譯器。⚠️ 見 6.3.2 |
| `@vitejs/plugin-react` | 6.x | ~ | 複查日確認相容 Vite 8 的版本 |
| React Router | **8.3.x** | ~ | |
| TanStack Query | **5.101.x** | ~ | server state |
| TanStack Virtual | 3.x | ~ | 大量 IOC 虛擬化表格 |
| Redux Toolkit | 2.x | ~ | client state |
| Tailwind CSS | **4.3.x** | ~ | ⚠️ **不要用 npm 的 `v3-lts` tag**，見 6.3.3 |
| `@tailwindcss/vite` | 隨 Tailwind | ~ | v4 的 Vite 整合 |
| shadcn/ui | CLI 產生 | — | 元件複製進專案，非套件相依 |
| React Hook Form | 7.x | ~ | |
| Zod | **4.4.x** | ~ | ⚠️ v4 API 與 v3 不同 |
| `@hookform/resolvers` | 支援 Zod 4 的版本 | ~ | 複查日確認 |
| lucide-react | 1.x | ~ | icon |
| Recharts | 3.x | ~ | dashboard 圖表 |
| Cytoscape.js | 3.x | ~ | STIX 關聯圖（M3） |
| Vitest | **4.1.x** | ~ | |
| `@testing-library/react` | 16.x | ~ | |
| Playwright | 1.x | ~ | E2E（M2 起） |
| ESLint | **10.8.x** | ~ | flat config |
| Prettier | 3.x | ~ | 格式化 |
| `eslint-plugin-import` | 2.x | ~ | `no-restricted-paths`（feature 依賴規則） |
| openapi-typescript | 7.x | ~ | 型別產生 |
| MSW | 2.x | ~ | 前端 API mock |

### 6.2.4 Infrastructure Images

| 服務 | Image | tag | 分支類型 | 支援終止 | 備註 |
|---|---|---|---|---|---|
| PostgreSQL | `postgres` | **18-alpine** | 現行 major | ~2030-11 ✅ | 18.6（2026-08-13）。⚠️ PG 19 GA 約 2026-09/10，**不自動升級** |
| Redis | `redis` | **8-alpine** | 現行 stable | ~ | 8.10（2026-07）。AGPLv3 三選一，見 6.5 |
| Valkey（替代） | `valkey/valkey` | **9-alpine** | 每 minor 3 年維護 | 2028+ ✅ | BSD，API 相容。**授權敏感時的首選** |
| Kafka | `apache/kafka` | **4.2.1** | — | ~ | **KRaft only，不使用 ZooKeeper** |
| Elasticsearch | `elasticsearch` | **9.5.x** | — | ~2027-02 | ⚠️ **9.3 已於 2026-08-04 EOL**，不得使用 |
| OpenSearch（替代） | `opensearchproject/opensearch` | 3.x | — | ~ | Apache 2.0，見 6.5 |
| Nginx | `nginx` | **1.30-alpine** | **stable**（偶數 minor） | ~2027-04 ✅ | ⚠️ **1.29 是 mainline 且已退役**，不得使用 |
| Prometheus | `prom/prometheus` | 3.x | — | ~ | M3 |
| Grafana | `grafana/grafana` | 12.x | — | ~ | M3 |

**相對 v1.1 的版本修正（三項，皆為「已無支援」類）**

| 項目 | v1.1 | 修正為 | 原因 |
|---|---|---|---|
| Elasticsearch | 9.3.x | **9.5.x** | 9.3 於 2026-08-04 EOL |
| Nginx | 1.29-alpine | **1.30-alpine** | 1.29 是奇數 minor = mainline，且已退役；stable 為 1.30 |
| Kafka | 4.2.0 | **4.2.1** | 4.2.1（2026-05-30）為現行 bugfix |

---

## 6.3 版本相關注意事項

### 6.3.1 不使用 Lombok（強制）

v1.1 已把 Lombok 縮到只剩 `@Slf4j` 與 `@RequiredArgsConstructor`。本版**完全移除**。

**理由**：留下這兩個註解換來四筆成本——

1. JDK 23 起 `javac` 預設 `-proc:none`，必須明確開啟 annotation processing
2. Lombok + MapStruct 的 processor 順序若錯誤，會產生**空的 mapper 實作**：編譯成功，runtime 全是 null。這是全 AI 實作幾乎不可能自行診斷的一類 bug——所有它會檢查的訊號都是綠的
3. 額外的 `lombok-mapstruct-binding` 版本 pin
4. Lombok 使用 JDK 內部 API，且**沒有 LTS**，而本政策要求 Java 走最新 LTS（2027 年將是 27）

**它們省下的程式碼**：

```java
private static final Logger log = LoggerFactory.getLogger(IndicatorService.class);
```

加上一個**本來就該讓人類看見**的建構子——[01-architecture.md](01-architecture.md#18-可讀性硬性規則與執行機制) 要求一律建構子注入，而手寫建構子在「人類易讀」這條標準上是加分，不是減分。

**移除後的 `maven-compiler-plugin` 設定**（parent pom）

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <release>25</release>
    <proc>full</proc>
    <compilerArgs>
      <arg>-parameters</arg>
      <arg>-Xlint:all</arg>
    </compilerArgs>
    <annotationProcessorPaths>
      <path>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct-processor</artifactId>
        <version>${mapstruct.version}</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

只剩一個 processor path，順序陷阱消失。`-parameters` 為 springdoc 正確推導參數名稱所必需。
`ctip-sdk` 與 `ctip-core` 額外加 `<arg>-Werror</arg>`。

### 6.3.2 TypeScript 7

TypeScript 7 是以 Go 重寫的編譯器，效能大幅提升，但部分舊型別定義套件可能尚未相容。

- **若在 M1 遇到生態系相容性問題，允許降級至 TypeScript 5.9.x**，並在 `docs/development/` 記錄原因
- 這是本規格中**唯一**允許主動降版的套件

### 6.3.3 Tailwind CSS v4 — 不要用 `v3-lts` tag

`tailwindcss` 在 npm 上有一個 `v3-lts` dist-tag（指向 3.4.x）。**不得使用**：

- 本專案要求 v4 的 CSS-first 設定（`@theme` 區塊 + `@tailwindcss/vite`，**沒有 `tailwind.config.js`**）
- 選 `v3-lts` 會是一次 major 降級，並推翻本檔與 [12-frontend.md](12-frontend.md) 的所有 Tailwind 相關規定

這是「最新 LTS」若照字面執行會自傷的實例——**支援窗口政策（6.1.1）優先於字面上的 LTS 標籤。**

### 6.3.4 Node.js 於 2026-10 改制

自 2026 年 10 月起 Node.js：一年一個 major、版號對齊年份、**每個 release 都成為 LTS**、新增 Alpha 通道。

- 本專案在複查日（2027-02-21）之前**維持 Node 24**
- 複查時依 6.1.1 重新判定「有 LTS 制度」下的最新 LTS

### 6.3.5 PostgreSQL 19

PG 19 GA 預計 2026-09/10，落在本專案開發期內。**不自動升級**——`postgres:18-alpine` 的 major 浮動 tag 不會跳到 19。升級為 major 變更，須人工核准並寫 ADR（含 migration 相容性驗證）。

### 6.3.6 Spring Boot 4 模組化與 Testcontainers 2.x（編譯地雷）

Phase 3 實測發現的四個地雷（2026-08-21 補入；皆已在 Phase 3 修正，此處為後續 phase 的預警）：

1. **Boot 4 把 auto-configuration 拆進各技術模組**。只加 `flyway-core` + `flyway-database-postgresql`
   而缺 `org.springframework.boot:spring-boot-flyway` 時，Flyway 的 autoconfig 根本不存在——
   **migration 靜默不執行**、應用照常啟動。日後引入 Redis／Kafka／ES 等時，同樣要確認對應的
   `spring-boot-<tech>` 模組在 classpath 上（皆由 BOM 納管，不寫版本）。
2. **Testcontainers 2.x（Boot 4 BOM 納管）改了座標與套件**：artifact 一律帶 `testcontainers-` 前綴
   （`testcontainers-junit-jupiter`、`testcontainers-postgresql`……）；`PostgreSQLContainer` 移至
   `org.testcontainers.postgresql` 且**不再是泛型**。
3. **Boot 的 `spring.sql.init` script initializer 預設在 Flyway 之前執行**——對 Flyway 建立的表種資料
   必然失敗。本專案以自定義 initializer bean + `@DependsOn("flywayInitializer")` 修正
   （`ctip-app` 的 `SeedDataConfig`），勿退回 Boot 預設行為。
4. **多 module reactor 下 `-Dtest=<類名>` 會使無該測試的 module 失敗**（surefire
   `failIfNoSpecifiedTests` 預設 true）。parent pom 已於 surefire 設定
   `<failIfNoSpecifiedTests>false</failIfNoSpecifiedTests>`，本規格與 DoD 的判準指令依賴此設定。
5. **MockMvc 測試支援也被拆出**（2026-08-25 Phase 6 實測補入）：`spring-boot-starter-test` 已
   **不含** `@AutoConfigureMockMvc`。需另加 test 相依 `org.springframework.boot:spring-boot-webmvc-test`
   （BOM 納管），且註解套件改為 `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`
   （不是 Boot 3 的 `org.springframework.boot.test.autoconfigure.web.servlet`）。
6. **Boot 4 的 Jackson 是 3.x，座標與套件整個改名**（2026-08-26 Phase 8 實測補入）：
   `ObjectMapper` 等 core/databind 類別在 `tools.jackson.*`（Maven 座標
   `tools.jackson.core:jackson-databind`），**不是** Boot 3 的 `com.fasterxml.jackson.*`；
   序列化例外改為 unchecked（`JacksonException`），`writeValueAsString` 不再宣告 checked exception。
   僅 `jackson-annotations` 仍維持 `com.fasterxml.jackson.core` 座標（2.x 版號）。
   注意 IDE 自動 import 很容易誤引 fasterxml——classpath 上兩者可能並存
   （test-scope 工具如 json-schema-validator 會傳遞引入 Jackson 2）。

---

## 6.4 版本複查程序（強制）

每次複查產出一筆記錄於 `docs/development/version-audit.md`（append-only）。

```bash
# 後端：列出可升級的相依
./mvnw versions:display-dependency-updates
./mvnw versions:display-plugin-updates

# 前端：列出過期套件
npm outdated
```

複查時對每一列執行三個判斷：

1. **仍在支援窗口內？** 若否 → 立即開 issue，標為 blocking
2. **是否有 LTS/stable 分支？** 若有且目前未 pin 在該分支 → 修正
3. **是否有更新的 GA minor？** 若有，記錄但**不自動升級**（依 6.1.2）

複查頻率：每 6 個月，或任何一項套件被通報 CVE 時立即執行。

---

## 6.5 授權注意事項

| 元件 | 授權 | 影響 |
|---|---|---|
| Redis 8.x | AGPLv3 / RSALv2 / SSPLv1 三選一 | AGPL 對 SaaS 有 copyleft 疑慮 |
| Elasticsearch 9.x | Elastic License 2.0 / SSPL / AGPL | 同上 |

本專案定位為 open-source-ready，因此：

- `docs/deployment/licensing.md` 必須說明上述授權情形與替代方案
- 抽象層（`SearchPort`、`CachePort`）必須讓 **Elasticsearch → OpenSearch**、**Redis → Valkey** 的替換只需改 infrastructure 實作與 image 名稱
- **Valkey 在支援窗口上也優於 Redis**：Valkey 對每個 minor 提供三年維護承諾，Redis 沒有等價承諾。在本政策（6.1.1）下 Valkey 是更合規的選擇，不只是為了避開 AGPL

---

## 6.6 已知編譯與建置地雷

| # | 地雷 | 症狀 | 對策 |
|---|---|---|---|
| 1 | JDK 23+ 預設 `-proc:none` | `cannot find symbol`（MapStruct 產生的 mapper 找不到） | parent pom 明確設 `<proc>full</proc>` |
| 2 | Tailwind v4 無 `tailwind.config.js` | 照抄 v3 教學後樣式完全不生效 | 設定寫在 CSS 的 `@theme` 區塊，透過 `@tailwindcss/vite` 整合 |
| 3 | Zod v4 error 格式與 v3 不同 | `@hookform/resolvers` 拋型別錯誤 | 使用支援 Zod 4 的 resolvers 版本 |
| 4 | Dockerfile build context | `COPY mvnw pom.xml ./` 在 repo root context 下找不到檔案 | 見 [05-environment.md](05-environment.md#53-dockerfile-契約)，所有 COPY 路徑必須含模組前綴 |
| 5 | Alpine / distroless 無 `curl` | compose healthcheck 永遠失敗 | 見 [05-environment.md](05-environment.md)，healthcheck 不依賴 curl |
| 6 | Vite HMR 跨 port 對映 | 前端可載入但 HMR 不生效 | dev 環境 host port 與容器 port 一致（皆 5173） |
| 7 | PostgreSQL 部分唯一約束 | `CONSTRAINT ... WHERE` 語法錯誤 | 必須寫成 `CREATE UNIQUE INDEX ... WHERE`，不能放在 `CONSTRAINT` 子句 |
| 8 | PostgreSQL `UNIQUE` 不去重 null | 廣播型通知重複插入 | 以 `COALESCE(col, sentinel)` 建唯一索引 |

---

*檔案結束。上次校對：2026-08-21。下次複查：2027-02-21。*
