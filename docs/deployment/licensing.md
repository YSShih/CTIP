# 第三方元件的授權與替代方案

> 對應規格:[06-tech-stack.md §6.5](../spec/06-tech-stack.md#65-授權注意事項)
> (「`docs/deployment/licensing.md` 必須說明上述授權情形與替代方案」)。
> 相關實作決策:[ADR 0026](../architecture/decisions/0026-phase17-redis-cache-and-distributed-rate-limit.md)(Redis)、
> [ADR 0028](../architecture/decisions/0028-phase19-elasticsearch-search.md)(Elasticsearch)。

CTIP 本身以開源為目標("open-source-ready")。程式碼與 Maven／npm 相依皆為寬鬆授權
(Apache-2.0 / MIT / EPL / BSD),**唯二需要留意的是兩個以容器形式部署的基礎設施元件**:
Redis 與 Elasticsearch。兩者的授權都不再是 OSI 認可的開源授權。

---

## 1. 兩個需要留意的元件

| 元件 | 版本 | 授權 | 為什麼要留意 |
|---|---|---|---|
| Redis | 8.x | AGPLv3 / RSALv2 / SSPLv1(三選一) | AGPLv3 的網路 copyleft 條款對 SaaS 有疑慮;RSALv2 / SSPLv1 皆非 OSI 開源授權 |
| Elasticsearch | 9.x | Elastic License 2.0 / SSPL / AGPLv3(三選一) | 同上。ELv2 明文禁止「把本軟體當成託管服務提供給第三方」 |

**對本專案自身的散布沒有影響**:CTIP 透過網路協定(RESP、HTTP)使用它們,不連結、不重新散布
它們的程式碼,容器 image 也是各自從上游取得。授權問題只有在**你打算把 CTIP 當成託管服務對外販售**時
才會實際發生——那正是 ELv2 與 SSPL 要限制的情境。

若你的部署屬於這一類,請改用下方的替代方案;兩者的替換成本都被刻意壓在「改 image 名稱」的等級,
這是 [§6.5](../spec/06-tech-stack.md#65-授權注意事項) 對抽象層(`SearchPort`、`CachePort`)的明文要求。

---

## 2. Redis → Valkey

Valkey 是 Redis 7.2.4 的 Linux Foundation fork,**BSD-3-Clause**,且對每個 minor 提供
三年維護承諾(Redis 沒有等價承諾)。在本專案的版本政策([§6.1.1](../spec/06-tech-stack.md))下,
Valkey 其實是**更合規的預設選擇**,不只是為了避開 AGPL。

替換步驟與相容性說明見 [`rate-limiting.md` §4](rate-limiting.md#4-換成-valkey):
只需改 compose 的 image 與 `command` / `healthcheck` 的執行檔名,程式一行都不用改
(`RedisCacheAdapter` 只用 `GET` / `SET key value EX ttl` / `DEL`,
`RedisRateLimiter` 只用 `GET` / `EVAL`,兩者在 Valkey 上完全相容),
環境變數沿用 `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD`。

---

## 3. Elasticsearch → OpenSearch

OpenSearch 是 Elasticsearch 7.10 的 fork,**Apache-2.0**,由 Linux Foundation 治理。

搜尋在應用層只透過兩個 port 使用:讀取面 `SearchPort`、寫入面 `SearchIndexPort`,
兩者的簽章只含 domain 與 JDK 型別(ArchUnit [規則 11](../spec/01-architecture.md#19-archunit-規則強制共-11-條)
會強制這件事)。因此替換的範圍是 **`com.ctip.infrastructure.elasticsearch` 這一個套件加上 image 名稱**。

### 3.1 compose

```yaml
  elasticsearch:                          # 服務名沿用,環境變數才不必跟著改
    image: opensearchproject/opensearch:3  # 原為 elasticsearch:9.5.1
    environment:
      discovery.type: single-node
      OPENSEARCH_JAVA_OPTS: ${ES_JAVA_OPTS:--Xms1g -Xmx1g}
      plugins.security.disabled: ${ES_SECURITY_ENABLED:+false}
      OPENSEARCH_INITIAL_ADMIN_PASSWORD: ${ELASTICSEARCH_PASSWORD:?}
```

`ELASTICSEARCH_URL` / `ELASTICSEARCH_USERNAME` / `ELASTICSEARCH_PASSWORD` / `SEARCH_BACKEND`
四個環境變數的語意完全不變。

### 3.2 應用

1. `backend/ctip-app/pom.xml`:把 `spring-boot-starter-elasticsearch` 換成 OpenSearch 的
   Java client(`org.opensearch.client:opensearch-java`),並自行提供一個 client bean
   (Spring Boot 沒有 OpenSearch 的 auto-configuration)。
   ⚠️ 依[執行規則 6](../spec/00-master.md#04-coding-llm-執行規則),新增相依必須先補進
   [§6.2 版本表](../spec/06-tech-stack.md)。
2. 改寫 `com.ctip.infrastructure.elasticsearch` 的六個類別。OpenSearch 的 Java client 是
   ES 8.x client 的 fork,查詢建構 API 幾乎一對一;mapping JSON 完全通用。
3. `application-{mvp,dev}.yml` 的 `management.health.elasticsearch.enabled: false` 可移除
   (該健康檢查隨 Boot 的 ES 模組而來)。
4. 測試:`ElasticsearchTestContainer` 換成 `opensearchproject/opensearch` image;
   `ElasticsearchSearchTest` / `SearchReconciliationTest` 的斷言不需要改——它們量的是行為,不是 client。

### 3.3 不需要改的東西

- 索引名、mapping、文件結構、`X-Search-Backend` 的值(`elasticsearch` 這個字面值是
  「非 PostgreSQL 的搜尋後端」的代稱,換成 OpenSearch 後**不要改**,否則是 API 的破壞性變更)。
- `FallbackSearchAdapter` 的降級邏輯、對帳排程、pipeline 的 `SearchIndexStage`。
- **PostgreSQL 永遠是 source of truth**([§13.7](../spec/13-platform-ops.md#137-搜尋-phase-12--m1postgresqlphase-19--m2elasticsearch)):
  搜尋索引隨時可以整個刪掉重建,換後端不會有資料遷移的問題。

---

## 4. 完全不用它們

兩個元件都不是必要的:

| 元件 | 關掉的方式 | 失去什麼 |
|---|---|---|
| Elasticsearch | `SEARCH_BACKEND=postgres`(預設值) | 大規模搜尋的延展性與模糊查詢(typosquatting);其餘搜尋能力由 `pg_trgm` + GIN 索引提供,語意完全相同 |
| Redis | `RATE_LIMIT_BACKEND=memory` | 多實例下的共用配額與跨實例快取失效——**單一實例部署才可接受**,多實例會使配額變成實例數的倍數 |

mvp 環境兩者都不啟動,功能完整可用。

---

## 5. 其他相依

| 類別 | 授權 | 備註 |
|---|---|---|
| Spring Boot 及其生態、Resilience4j、Bucket4j、MapStruct、Testcontainers、Kafka、Prometheus、Grafana | Apache-2.0 | 無限制 |
| PostgreSQL | PostgreSQL License(BSD 類) | 無限制 |
| nginx | BSD-2-Clause | 無限制 |
| OASIS STIX 2.1 JSON Schema(vendored 於 `ctip-app/src/test/resources/stix-schemas/`) | BSD-3-Clause | 出處記於該目錄的 README |
| 前端相依(React、Vite、TanStack、Tailwind、Zod…) | MIT / Apache-2.0 | 無限制 |
| zstd-jni | BSD-2-Clause | Bloom artifact 壓縮 |

完整清單以 SBOM 為準(`cyclonedx-maven-plugin`,[§13.8](../spec/13-platform-ops.md) 的 CI 產出)。
