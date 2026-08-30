# 架構總覽

> 這份文件是**進入這個 repo 的第一站**:一頁看懂系統長什麼樣、東西放在哪、為什麼這樣分。
> 規範性內容在 [`docs/spec/`](../spec/README.md);本檔只做導覽,兩者衝突時**以規格為準**。

---

## 1. 系統在做什麼

CTIP 從異質的威脅情資來源(feed、API、手動提交)攝取 IOC,正規化成單一領域模型,
去重、跨來源合併、評分,再以三種方式對外提供:

```text
        ┌──────────────┐
Feeds ─▶│  Ingestion   │──▶ PostgreSQL ──┬──▶ REST API(cursor 分頁、TLP 過濾)
        │  (10 stages) │                 ├──▶ Bloom Filter(離線比對,兩層)
        └──────────────┘                 ├──▶ STIX 2.1(單物件 / bundle)
                                         └──▶ 事件(Kafka → WebSocket / SSE / Webhook)
```

多租戶、以 TLP 2.0 分級、有再散布法遵限制,而且**這三件事都在查詢述詞裡**,不是應用碼的後處理。

---

## 2. 分層與依賴方向

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
| `application` | domain、`ctip-sdk`、`spring-context`、`spring-tx` | JPA entity、`spring-data-*`、`spring-web`、任何 infrastructure 型別 |
| `infrastructure` | 全部 | — |
| `interfaces` | application、domain、`ctip-sdk`、`spring-web` | `infrastructure.persistence`(JPA entity) |

以 **11 條 ArchUnit 規則**強制([01 §1.9](../spec/01-architecture.md#19-archunit-規則強制共-11-條)),
違反時是編譯後測試失敗,不是 code review 意見。

詳見 [01 §1.2](../spec/01-architecture.md#12-分層與依賴方向強制)。

---

## 3. Maven module(4 個)

| Module | 內容 | 允許依賴 |
|---|---|---|
| `ctip-sdk` | **Shared Kernel**:adapter 契約 + 跨界列舉與值物件 | JDK、`jakarta.validation-api` |
| `ctip-core` | `domain` + `application`。業務規則所在 | `ctip-sdk`、spring-context、spring-tx |
| `ctip-adapters` | 內建與 mock 的 Threat Source Adapter | `ctip-sdk`、HTTP client、Resilience4j |
| `ctip-app` | 啟動類、`infrastructure`、`interfaces`、Flyway、設定 | 全部 |

兩件容易看漏但很重要的事:

- **`ctip-adapters` 不依賴 `ctip-core`**——adapter 只認識 SDK 契約,
  這保證第三方 adapter 與內建 adapter 走同一條路([ADR 0039](decisions/0039-ctip-sdk-shared-kernel.md))
- **`ctip-sdk` 必須可獨立發布**(零 Spring、零 JPA;DoD M3-21)

---

## 4. 領域模型

九個聚合根,**跨聚合只能以 ID 參照**,跨聚合一致性以 domain event 達成:

`Tenant`、`Source`、`Indicator`、`User`、`ApiKey`、`Subscription`、`Threat`、`BloomVersion`、`Webhook`

沒有不變量、沒有狀態機的表(`audit_logs`、`webhook_deliveries`、`stix_objects`、關聯表…)
**不建 domain model**,直接 JPA entity → DTO——這是刻意的分類規則,不是省略
([01 §1.5](../spec/01-architecture.md#15-三模型-vs-兩模型強制分類))。
不變量清單見 [02 §2.3](../spec/02-ddd-model.md#23-聚合不變量)。

命名一律依 [02 §2.1](../spec/02-ddd-model.md#21-ubiquitous-language-詞彙表中英對照) 的詞彙表。

---

## 5. 攝取管線(12 個 stage)

```text
Adapter → 1 Parse → 2 Validate → 3 Normalize → 4 Fingerprint
        → 5 Deduplicate → 6 Merge → 7 Score → 8 StixProject → 9 Persist
        → 10 BloomUpdate → 11 SearchIndex → 12 PublishEvent
```

stage 順序是**一處可見的顯式 `List.of`**([08 §8.2](../spec/08-ingestion-sdk.md#82-攝取管線)),
不是繼承鏈也不是註解掃描——要知道資料經過什麼,只需讀那一行。

每個 stage 都有自己的計時器(`ctip.ingestion.stage.duration{stage}`),
被拒絕的記錄連同原因寫 `ingestion_rejections`(八種 reason,[07 §7.3](../spec/07-domain-intel.md#73-拒絕規則強制))。
STIX 投影在批次交易提交後逐筆寫出,**投影失敗不會使 ingestion 失敗**
([00 §0.9](../spec/00-master.md))。

---

## 6. 讀取路徑

| 路徑 | 後端 | 降級 |
|---|---|---|
| 清單 / 詳情 / 統計 | PostgreSQL + cursor 分頁 | — |
| 全文與模糊檢索 | Elasticsearch | 斷路器跳開後回落 PostgreSQL(`X-Search-Backend` 標頭指出實際來源) |
| 離線比對 | Bloom snapshot + delta | — |
| 情資交換 | STIX 2.1 單物件 / bundle | — |

**每一條路徑都套用同一套可見度述詞**
(`owner_tenant_id IN (current, public) AND tlp <= maxVisibleTlp`)加上再散布過濾
([07 §7.7](../spec/07-domain-intel.md#tlp-可見度)、[§7.9](../spec/07-domain-intel.md#79-再散布政策法遵強制))。

---

## 7. 前端

React 19 + TypeScript,型別**由 `docs/api/openapi.json` 產生**(不得手寫後端型別)。
`features/<name>/` 各自封裝,以 ESLint 的 `no-restricted-paths` 強制四條依賴規則:
feature 之間不得互相 import,只有 `pages/`、`routes/`、`app/` 可跨 feature
([12 §12.2](../spec/12-frontend.md))。

---

## 8. 基礎設施

| 服務 | 用途 | 哪些環境 |
|---|---|---|
| PostgreSQL | 權威資料庫(Flyway migration) | 全部 |
| Redis / Valkey | 快取 + 分散式限流 | dev / staging / prod |
| Kafka(KRaft) | domain event | staging / prod(dev 可選) |
| Elasticsearch | 全文檢索 | staging / prod(dev 可選) |
| Prometheus + Grafana | 指標與 dashboard | staging / prod |
| nginx | 前端靜態檔 | staging / prod |

**只有一份 compose 檔**,環境差異全部用變數與 profiles 表達([ADR 0034](decisions/0034-single-compose-strategy.md))。

---

## 9. 關鍵決策索引

跨越單一 phase 的架構決策,各自有一則 ADR:

| 決策 | ADR |
|---|---|
| 不採用 CQRS | [0033](decisions/0033-no-cqrs.md) |
| 單一 compose 檔策略 | [0034](decisions/0034-single-compose-strategy.md) |
| 兩層 Bloom(public / tenant) | [0035](decisions/0035-two-tier-bloom.md) |
| 移除 Lombok | [0036](decisions/0036-no-lombok.md) |
| 停用 CSRF | [0037](decisions/0037-csrf-disabled.md) |
| TLP 與方案解耦 | [0038](decisions/0038-tlp-decoupled-from-plans.md) |
| `ctip-sdk` 作為 Shared Kernel | [0039](decisions/0039-ctip-sdk-shared-kernel.md) |
| Repository port 分層 | [0040](decisions/0040-repository-port-layering.md) |

逐 phase 的實作決策見 [`decisions/`](decisions/) 的其餘 ADR(0001–0032、0041)。

---

## 10. 延伸閱讀

- 安全架構:[`security.md`](security.md)
- 環境與啟動:[`../development/getting-started.md`](../development/getting-started.md)
- 寫一個 adapter:[`../development/plugin-sdk.md`](../development/plugin-sdk.md)
- API 契約:[`../api/openapi.json`](../api/openapi.json)、[`../api/README.md`](../api/README.md)
- 規格書:[`../spec/README.md`](../spec/README.md)
