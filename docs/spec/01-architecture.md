# 01 — 架構

> **規範等級：強制。** 模組邊界、依賴方向、ArchUnit 規則、抽象判準皆為規範性內容。
>
> 相關檔案：[02-ddd-model.md](02-ddd-model.md)、[03-diagrams.md](03-diagrams.md)、[14-testing.md](14-testing.md)

---

## 1.1 架構原則

**Backend 必須遵循**

- Domain Driven Design（聚合、不變量、Ubiquitous Language — 見 [02](02-ddd-model.md)）
- Clean Architecture / Hexagonal Architecture（Ports and Adapters）
- Event Driven Architecture（M1–M2 為程序內事件，M3 加入 Kafka）
- SOLID，特別是 Dependency Inversion
- Explicit boundaries、High cohesion、Low coupling

**明確不採用**

| 不採用 | 理由 |
|---|---|
| CQRS | 讀寫分離的需求由 Elasticsearch 讀取索引（[09](09-api.md)）自然滿足。引入 CQRS 框架會被 Coding LLM 過度解讀成「每個查詢都要一個 handler」 |
| Event Sourcing | PostgreSQL 是 source of truth，狀態即真相。事件用於通知與投影，不用於重建狀態 |
| 多個 Bounded Context | 見 [02-ddd-model.md](02-ddd-model.md#20-為何是單一-bounded-context) |
| 微服務 | 單一 deployable。模組邊界以 Maven module + ArchUnit 強制，不需網路邊界 |

**Frontend 必須遵循**

- Feature-based architecture（依業務能力切分，非依技術類型）
- UI / state / API / domain 型別分離
- Server state 與 client state 分離（TanStack Query vs Redux Toolkit）
- Typed API contracts，型別**由 OpenAPI 產生**，不手寫
- Reusable component system

---

## 1.2 分層與依賴方向（強制）

```text
        interfaces  ──────→  application  ──────→  domain
             │                    │                   ▲
             │                    │                   │
             └────────────→ infrastructure ───────────┘
                            (實作 domain/application 宣告的 port)
```

| 層 | 允許 import | 禁止 import |
|---|---|---|
| `domain` | JDK、`ctip-sdk`、`jakarta.validation` | **任何** Spring、JPA、Hibernate、Jackson、Kafka、Redis、Elasticsearch 型別 |
| `application` | domain、`ctip-sdk`、`org.springframework.context`、`org.springframework.transaction` | JPA entity、`spring-data-*`、`spring-web`、任何 infrastructure 型別 |
| `infrastructure` | 全部 | — |
| `interfaces` | application、domain、`ctip-sdk`、`spring-web` | `infrastructure.persistence`（JPA entity） |

> ⚠️ `application` **不得** import `org.springframework.data.domain.*`（含 `Page`、`Pageable`、`Sort`）。回傳型別一律使用 `CursorPage`（[02-ddd-model.md](02-ddd-model.md#26-值物件清單)）。

---

## 1.3 Maven Multi-Module

```text
backend/
├── pom.xml              (parent, packaging: pom)
├── mvnw / mvnw.cmd / .mvn/
├── ctip-sdk/
├── ctip-core/
├── ctip-adapters/
└── ctip-app/
```

| Module | 內容 | 允許依賴 | 備註 |
|---|---|---|---|
| `ctip-sdk` | **Shared Kernel**：adapter 契約 + 跨界列舉與值物件 | JDK、`jakarta.validation-api` | 必須可獨立發布至 Maven Central |
| `ctip-core` | `domain` + `application`。業務規則所在 | `ctip-sdk`、spring-context、spring-tx | 不得有 JPA、不得有 spring-data |
| `ctip-adapters` | 內建與 mock 的 Threat Source Adapter | `ctip-sdk`、HTTP client、Resilience4j | 不得依賴 `ctip-core` |
| `ctip-app` | Spring Boot 啟動類、`infrastructure`、`interfaces`、Flyway、設定檔 | 全部 | 唯一產生可執行 jar 的模組 |

`ctip-adapters` **不依賴 `ctip-core`** 是刻意的：adapter 只認識 SDK 契約，這保證第三方 adapter 與內建 adapter 走同一條路。

---

## 1.4 Package 結構

```text
backend/ctip-sdk/src/main/java/com/ctip/sdk/
├── ThreatSourceAdapter.java   SourceMetadata.java    FetchContext.java
├── FetchResult.java           RawThreatRecord.java   SourceType.java
├── IocType.java               IocHashType.java       FingerprintAlgorithm.java
├── Tlp.java                   Severity.java          Confidence.java
└── RedistributionPolicy.java

backend/ctip-core/src/main/java/com/ctip/
├── domain/
│   ├── indicator/      Indicator、IndicatorSource、HashRecord、IndicatorMergePolicy、值物件
│   ├── threat/         Threat、ThreatIndicatorLink、ExternalReference          [M2]
│   ├── source/         Source、SourceHealth、Reputation
│   ├── tenant/         Tenant、TenantSlug
│   ├── identity/       User、RefreshToken、ApiKey、值物件                      [M2]
│   ├── subscription/   Subscription、BillingPeriod                            [M2]
│   ├── fingerprint/    FingerprintStrategy、Sha256FingerprintStrategy、Fingerprint
│   ├── bloom/          BloomVersion、BloomArtifact、BloomParameters、Checksum  [M2]
│   ├── stix/           STIX 物件模型與 builder
│   ├── notification/   Webhook、WebhookFilter                                 [M3]
│   ├── event/          DomainEvent 與 19 個具體事件
│   └── shared/         Cursor、CursorPage、共用型別
│
└── application/
    ├── indicator/      IndicatorQueryService、IndicatorSubmissionService [M2]
    ├── ingestion/      IngestionPipeline、IngestionStage 與各 stage
    ├── search/         （M1 實際由 indicator/IndicatorQueryService 承擔；
    │                    獨立的 search/ 套件待 Phase 19 的 ES 降級邏輯才成立，見 ADR 0016）
    ├── source/         SourceSyncService、SourceHealthService
    ├── stix/           StixExportService
    ├── sync/           BloomSyncService                                       [M2]
    ├── identity/       AuthService、ApiKeyService                             [M2]
    ├── subscription/   SubscriptionService、QuotaService                      [M2]
    ├── notification/   NotificationService                                    [M3]
    ├── audit/          AuditService                                           [M3]
    └── port/           out-port（介面，僅使用 domain 型別）
        ├── IndicatorRepository.java     ThreatRepository.java      [M2]
        ├── SourceRepository.java        TenantRepository.java
        ├── UserRepository.java  [M2]    ApiKeyRepository.java      [M2]
        ├── SearchPort.java              CachePort.java             [M2]
        ├── EventPublisherPort.java      RateLimiterPort.java
        ├── BloomStoragePort.java [M2]   AuditPort.java             [M3]
        └── ClockPort.java               （可測試性：禁止直接呼叫 Instant.now()）

backend/ctip-adapters/src/main/java/com/ctip/adapters/
├── mock/    MockOpenPhishAdapter、MockAbuseIPDBAdapter、MockAlienVaultAdapter
├── manual/  ManualSubmissionAdapter                                          [M2]
└── http/    共用 HTTP feed 基礎設施（含 Resilience4j 裝配）

backend/ctip-app/src/main/java/com/ctip/
├── CtipApplication.java
├── config/            Spring 設定類（每個關注點一個 @Configuration）
├── infrastructure/
│   ├── persistence/   JPA entity、*RepositoryAdapter、package-private *JpaRepository、MapStruct mapper
│   ├── security/      TenantContext、匿名綁定 filter、TLP Specification    [M1 最小版]
│   ├── redis/                                                              [M2]
│   ├── elasticsearch/                                                      [M2]
│   ├── bloom/         位元運算與序列化實作                                  [M2]
│   ├── kafka/                                                              [M3]
│   ├── notification/                                                       [M3]
│   └── source/        AdapterRegistry
└── interfaces/
    ├── rest/          controller、DTO（record）、CursorCodec、@RestControllerAdvice
    ├── websocket/                                                          [M3]
    └── webhook/                                                            [M3]
```

`bloom` 與 `fingerprint` 放在 `domain` 之下是刻意的：**去重策略與 Bloom 的語意（正向不確定、負向確定）是業務規則**，不是技術細節。位元運算與序列化在 `infrastructure/bloom/`。

---

## 1.5 三模型 vs 兩模型（強制分類）

**判準**

> 該表是否有跨欄位不變量，或是否有狀態機？
> **有** → 屬於某聚合 → **三模型**：domain model（`record` 或聚合類別）／JPA entity／DTO
> **沒有**（純參考資料、關聯表、append-only 記錄、衍生投影）→ **兩模型**：JPA entity／DTO

完整分類表見 [04-data-dictionary.md](04-data-dictionary.md#41-表清單與模型分層)。

**為什麼不全部三模型**：為 `audit_logs`、`webhook_deliveries` 這類 append-only 記錄建立 domain model，產出的是 anemic layer——多一層轉換、零不變量強制。anemic layer 比沒有 layer 更糟。

**為什麼不全部兩模型**：`Indicator` 有 14 條不變量。若 domain model 就是 JPA entity，這些不變量會被 Hibernate 的 proxy、lazy loading 與 no-arg constructor 需求侵蝕，且 `domain` 會依賴 JPA，違反 1.2。

**兩模型的邊界規則**：兩模型的表，其 JPA entity **可以**直接映射為 response DTO（透過 MapStruct），但 controller 仍**不得**回傳 JPA entity 本身。

---

## 1.6 Repository 分層（強制）

```text
application/port/IndicatorRepository            介面，僅使用 domain 型別
        ▲
        │ implements
infrastructure/persistence/IndicatorRepositoryAdapter    @Repository
        │ delegates to
        ▼
infrastructure/persistence/IndicatorJpaRepository        package-private
        extends JpaRepository<IndicatorEntity, UUID>
```

- `IndicatorJpaRepository` 為 **package-private**，只有同 package 的 adapter 看得到
- Adapter 負責 domain model ↔ JPA entity 的映射（MapStruct）
- **不得**在 Spring Data 之上再疊第二層自訂抽象：禁止 generic repository、禁止自寫 query DSL、禁止 `AbstractRepository`
- **不得**讓 controller 或 domain 物件持有任何 repository

> v1.1 的 §35.1 寫「使用 Spring Data 提供的即可，不要再自己包一層」，字面上連 port 都禁止了——那會把 spring-data 與 JPA entity 型別拖進 `ctip-core`，違反 1.2。本節為修正後的正確表述。

---

## 1.7 抽象判準（強制）

> **若移除某個抽象之後，程式仍然只有一種可能的實作，就不要加這個抽象。**

### 必須使用的模式

| 模式 | 用於 | 為何不可省 |
|---|---|---|
| **Adapter** | `ThreatSourceAdapter` | 每個來源格式不同，是 plugin 架構的地基 |
| **Strategy** | `FingerprintStrategy`、`ThreatScorer` | 評分未來要換 ML；指紋演算法可能升級 |
| **Repository（Port）** | 持久化抽象 | 見 1.6 |
| **Specification** | IOC 複雜查詢、TLP／tenant 過濾 | 使用 JPA 內建 `Specification`，**不自寫 DSL** |

### 改用框架內建，不要手刻

| 想用的 | 改用 |
|---|---|
| Factory 建立 adapter | Spring 注入 `List<ThreatSourceAdapter>` 後轉 Map |
| Observer | `ApplicationEventPublisher` →（M3）Kafka listener |
| Command（排程） | `@Scheduled` 直接呼叫 application service |
| Builder（一般物件） | Java `record` + 靜態工廠。**僅 STIX 物件**允許手寫 builder（欄位多且有條件必填） |
| Singleton | Spring bean 預設即是 |
| DI 容器／event bus／ORM | 一律使用框架，禁止自製 |

### 明確禁止

| 禁止 | 原因與替代 |
|---|---|
| **Decorator** 用於限流／配額 | 堆疊 decorator 使 stack trace 難讀。改用單一 `HandlerInterceptor` 或 Security filter |
| **Template Method** 用於 ingestion | 繼承鏈難追蹤。改用明確的 stage 列表（[08](08-ingestion-sdk.md)） |
| **抽象基底類別**（`AbstractXxxService`） | 一律改用組合。介面 + 實作即可 |
| 為「展示模式」而加的抽象 | 違反本節判準 |
| `Optional` 作為欄位或參數 | 僅作為回傳型別 |
| 靜態可變狀態 | 除 `static final` 常數外禁止 |
| 直接呼叫 `Instant.now()` / `UUID.randomUUID()` 於 domain | 一律經 `ClockPort` / `IdGenerator`，否則不可測 |

---

## 1.8 可讀性硬性規則與**執行機制**

| 規則 | 執行工具 | 失敗行為 |
|---|---|---|
| 單一類別 ≤ 300 行 | Checkstyle `FileLength` | `mvn verify` 失敗 |
| 單一方法 ≤ 50 行 | Checkstyle `MethodLength` | `mvn verify` 失敗 |
| 方法參數 ≤ 5 個 | Checkstyle `ParameterNumber` | `mvn verify` 失敗 |
| 巢狀深度 ≤ 3 | Checkstyle `NestedIfDepth`、`NestedTryDepth` | `mvn verify` 失敗 |
| 一律建構子注入，禁 `@Autowired` 欄位 | ArchUnit 規則 6 | 測試失敗 |
| 公開 API 的 DTO 一律 `record` | ArchUnit 規則 7 | 測試失敗 |
| 禁止循環相依 | ArchUnit 規則 5 | 測試失敗 |
| 格式一致 | Spotless（palantir-java-format） | `mvn verify` 失敗 |

> **v1.1 的問題**：這七條規則裡有五條沒有任何工具支撐，等於指引而非規則。在全 AI 實作下，這正是最容易被靜默違反的一類——AI 不會故意寫 400 行的類別，但會在多次迭代後不知不覺寫出來，而沒有任何東西會叫停。
>
> Checkstyle **只開上述五條規則**，不使用 `sun_checks.xml` 或 `google_checks.xml`（會產生數千條噪音而被關掉）。設定檔：`backend/config/checkstyle/ctip-checks.xml`。
>
> Spotless 選確定性格式化工具的理由不是美觀，是**兩個不同的 AI session 會產出位元相同的排版**，使 diff 只反映語意變更。

`-Xlint:all` 全模組啟用；`-Werror` 於 `ctip-sdk` 與 `ctip-core` 啟用（`ctip-app` 不啟用，避免第三方註解處理器的警告擋住建置）。

前端對應：ESLint 10 flat config + Prettier + `import/no-restricted-paths`（實作 feature 依賴規則，見 [12-frontend.md](12-frontend.md)）。

---

## 1.9 ArchUnit 規則（強制，共 11 條）

位於 `ctip-app/src/test/java/com/ctip/architecture/ArchitectureTest.java`（**跨模組掃描**，需在 `ctip-app` 執行）。

| # | 規則 |
|---|---|
| 1 | `com.ctip.domain..` 不得依賴 Spring、JPA、Hibernate、Jackson、Kafka、Redis、Elasticsearch、**Micrometer** |
| 2 | `com.ctip.sdk..` 不得出現 `org.springframework..` 或 `jakarta.persistence..` |
| 3 | `com.ctip.interfaces..` 不得 import `com.ctip.infrastructure.persistence..` |
| 4 | `..interfaces.rest..` 的類別不得直接依賴 `..application.port..Repository` |
| 5 | 無循環套件相依（`slices().should().beFreeOfCycles()`） |
| 6 | 標註 `@Service`／`@Component`／`@Repository`／`@RestController` 的類別不得有 `@Autowired` 欄位 |
| 7 | `..interfaces.rest.dto..` 的所有類別必須是 `record` |
| 8 | `com.ctip.application..` 不得 import `org.springframework.data.domain..` |
| 9 | `com.ctip.domain..` 不得呼叫 `java.time.Instant.now()`、`java.time.LocalDate.now()`、`java.util.UUID.randomUUID()` |
| 10 | `com.ctip.domain..`／`com.ctip.sdk..` 的類別名不得使用 [02 §2.1](02-ddd-model.md#21-ubiquitous-language-詞彙表中英對照) 詞彙表「常見誤用」欄的命名 |
| 11 | `com.ctip.application..` 不得依賴基礎設施 client 的型別：`io.lettuce..`、`redis.clients..`、`org.springframework.data.redis..`、`io.github.bucket4j..`、`co.elastic.clients..`、`org.elasticsearch..`、`org.springframework.data.elasticsearch..`、`io.github.resilience4j..` |

規則 8、9 為本版新增。規則 8 強制 1.2 的 `CursorPage` 決定；規則 9 保證 domain 可測。

> **規則 10（2026-08-28；[ADR 0016](../architecture/decisions/0016-phase1-13-spec-backfill.md)）**：
> `15 §15.5` 明文要求人工項 P-02 的「可自動化部分必須實作」，該規則當時已寫進
> `ArchitectureTest` 卻**沒有回寫本節**（表格與「共 9 條」的計數都沒動）。此處補上。
>
> **規則 11（2026-08-29，Phase 17；[ADR 0026](../architecture/decisions/0026-phase17-redis-cache-and-distributed-rate-limit.md)）**：
> `phases/phase-17.md` 禁止 `CachePort` 把 Lettuce／Redis 型別洩漏到 application 層。
> 規則 1 只擋 domain，而 **port 定義在 application 層**——真正會發生洩漏的地方是那裡：
> 一個收 `StatefulRedisConnection` 的 port 簽章，會讓 [06 §6.5](06-tech-stack.md#65-授權注意事項)
> 要求的「Redis → Valkey 只需改 infrastructure 實作」變成不可能。
>
> **規則 11 擴充（2026-08-29，Phase 19；[ADR 0028](../architecture/decisions/0028-phase19-elasticsearch-search.md)）**：
> `phases/phase-19.md` 的「不得讓 `ElasticsearchSearchAdapter` 的型別洩漏到 `application` 層」
> 與上述是同一條規則的另一個實例，因此**擴充規則 11 的套件清單而非新增規則 12**——
> 本節與 [00 §0.3](00-master.md#03-強制契約coding-llm-不得自行變更) 的「11 條」契約維持不變。
> 一併擋 Resilience4j：§13.7 的降級屬於 `FallbackSearchAdapter`，斷路器的型別不該出現在 port 上。
>
> **規則 1 擴充（2026-08-30，Phase 22；[ADR 0032](../architecture/decisions/0032-phase22-observability.md) §1）**：
> 禁止清單加入 `io.micrometer..`。[13 §13.6](13-platform-ops.md#136-監控日誌追蹤-phase-22--m3) 的六個
> `ctip.*` 指標，產生點都在 **application 層**（攝取、Bloom、再散布），因此 `ctip-core` 直接依賴
> `micrometer-core`——它與 `slf4j-api` 同性質，是門面而非基礎設施 client，不入規則 11 的清單。
> 代價是 domain 多了一條可能被誤用的路，故在規則 1 明確擋掉：**指標是基礎設施關注，不是不變量的一部分**。

---

## 1.10 交易邊界

- 交易邊界定義在 **application service**，不在 controller、不在 domain、不在 repository
- **絕不**把外部網路呼叫包在交易內
- Ingestion：adapter fetch 在交易之外；每批（預設 500 筆）寫入為一個交易
- 唯讀查詢使用 `@Transactional(readOnly = true)`
- Domain event 於 `AFTER_COMMIT` 發佈

---

## 1.11 M1 最小安全層（強制，Phase 4）

v1.1 把 `infrastructure/security/` 標為 M2，但 DoD-MVP 要求「匿名只看得到 `CLEAR`」，§24.2 要求「匿名請求在 security filter 層綁定到 public tenant」，§25.1 要求 `TenantContext` 由 security filter 設定——三者皆為 M1。**本版於 Phase 4 建立最小安全層。**

M1 範圍（約 150 行）：

```text
infrastructure/security/
├── TenantContext.java              RequestScope，持有 TenantId 與 AuthState
├── AnonymousTenantFilter.java      OncePerRequestFilter，無憑證 → 綁 public tenant
├── TlpSpecifications.java          統一的 TLP + tenant 過濾 Specification
└── AuthState.java                  ANONYMOUS | AUTHENTICATED（M2 擴充為完整身分）
```

**過濾條件（強制，唯一一套邏輯）**

```java
// 所有 tenant-scoped 查詢一律附加此條件，不得在 controller 手動傳 tenantId
owner_tenant_id IN (:currentTenantId, PUBLIC_TENANT_ID)
AND tlp <= :maxVisibleTlp
```

`maxVisibleTlp` 由 `AuthState` 決定，**與方案無關**（見 [07-domain-intel.md](07-domain-intel.md#tlp-可見度)）：

| AuthState | 可見範圍 |
|---|---|
| `ANONYMOUS` | public tenant 的 `CLEAR` |
| `AUTHENTICATED` | public tenant 的 `CLEAR` + `GREEN`，**加上**自家 tenant 的全部 TLP |

Phase 13 只是在此之上加入使用者認證與 RBAC，**不需重寫任何 query**。

> **實作回饋修訂（2026-08-27，Phase 13；ADR 0012 決策 3）**
> `phases/phase-13.md` 寫「`AuthState` 擴充為完整身分」,但 `AuthState` 正是上表的軸,
> 改動它就等於改動 TLP 過濾邏輯(同一份執行單明令不得改)。實作因此**保留 `AuthState` 兩態**,
> 另以 `application/identity/AuthenticatedIdentity`(`userId`、`tenantId`、`role`、`permissions`、
> `apiKeyId`)承載完整身分,由 `TenantContext.bindAuthenticated` 綁定。
> `TlpSpecifications` 與 `Visibility` 零修改;可見度與角色、方案仍完全解耦。
> 同時 `AnonymousTenantFilter` 併入 Spring Security filter chain,更名為
> `CtipAuthenticationFilter`(Bearer JWT / `X-API-Key` / 無標頭匿名三路經同一條 chain,§9.2)。

> 為什麼不延到 M2：安全過濾的 retrofit 失敗模式是「漏掉某個端點」，而這是全 AI 實作最不擅長的收尾工作——沒有人類會逐一點過三十個端點。

---

*檔案結束。上次校對：2026-08-21。*
