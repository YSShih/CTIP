# ADR 0018 — 版本表補齊與環境可啟動(批 2、批 3)

- **狀態**:accepted
- **日期**:2026-08-28
- **範圍**:`06 §6.1.2/§6.2/§6.3.6`、`backend/pom.xml` 的 properties、
  `environment/docker-compose.yml`、`environment/config/monitoring/**`
- **背景**:清障計畫的批 2 與批 3。批 2 解除規則 6 對 Phase 15–23 的阻擋;
  批 3 讓 `up.sh staging` 真的能起來。

---

## 批 2:版本表補齊

`06 §6.2` 開頭明訂「版本表與版本政策不得由 Coding LLM 自行變更」,而 §6.1.3 規則 2 要求
「未由 BOM 納管的相依必須在 parent pom `<properties>` 集中定義」。兩條合起來的效果是:
**版本表沒有列的東西,實作 phase 一律動不了**。

盤點 Phase 15–23 需要的相依,發現版本表**一項都沒有列**。這會讓每一個 phase 在第一天就撞牆。
使用者已授權補齊,故一次列完:

| Phase | 補列項目 | 版本來源 |
|---|---|---|
| 15/16 | **Bloom 位元實作 = 自行實作** | §11.4 的 layout(LSB-first + SHA-256 fingerprint 的 Kirsch-Mitzenmacher 雙雜湊)**排除所有現成函式庫**——Guava `BloomFilter` 用 murmur3_128 與自有 layout,產不出該格式。明文寫死「不得引入 Bloom 函式庫」,免得該 phase 誤以為可以 |
| 15/16 | ZSTD(`com.github.luben:zstd-jni`) | **BOM 未納管** → parent property。JDK 只內建 GZIP/Deflate,而 §11.4 與 04 表 23 都把 ZSTD 定為預設壓縮 |
| 17 | Spring Data Redis、`bucket4j-redis`、`com.redis:testcontainers-redis` | 前兩者隨 BOM／既有 property |
| 19 | ES client + `testcontainers-elasticsearch` | 隨 BOM |
| 20 | Spring Kafka、`testcontainers-kafka`、WebSocket starter | 隨 BOM |
| 22 | `micrometer-registry-prometheus`、OTel bridge/exporter、`logstash-logback-encoder` | 前兩者隨 BOM;第三者**未納管** → parent property |
| 23 | CycloneDX Maven plugin | **未納管** → parent property |

### 三個新 property 的版本是查證後的實際 release

我第一版憑印象寫了 `1.5.6-9` / `8.0` / `2.9.1`,**三個全錯**。改為對 Maven Central
`maven-metadata.xml` 查證後的值:`zstd-jni 1.5.7-15`、`logstash-logback-encoder 9.0`、
`cyclonedx-maven-plugin 2.9.3`,並在版本表標 ✅(已查證)。

### 兩個記進表裡的相容性風險

1. **`bucket4j-redis` 的 provided 相依編譯目標是 Lettuce 6.1.8**,而 Boot 4.1.0 BOM 納管 7.5.2。
   已逐一比對該 artifact 參照的 class/method 在 7.5.2 皆存在,但升級 Lettuce 時須重驗。
2. **BOM 的 ES client 落後 server image 一個 minor**(client 9.4.x vs `elasticsearch:9.5.1`)。
   ES client 相容前後一個 minor,但 §6.1.3 禁止硬寫 BOM 納管的版本,所以只能接受並在複查日重驗。

### §6.3.6 補 autoconfig 座標對照表

Flyway 的教訓(缺 `spring-boot-flyway` → migration 靜默不執行)在 Redis/Kafka/ES 上會重演。
補一張 `spring-boot-<tech>` / starter / test slice 的對照表。

**現成的活體樣本**:`application.yml` 已宣告 `spring.data.redis.host/port/password`,
但 classpath 上沒有任何 Redis autoconfig ——**那些屬性目前完全惰性**。

另補記 Testcontainers 2.x 的例外:`org.testcontainers` **沒有** redis module
(已核對 2.0.5 BOM 的 64 個 artifact),BOM 納管的是第三方 `com.redis:testcontainers-redis`。

### GitHub Action 的版本政策(§6.1.2 原本沒有)

§6.1.2 只涵蓋 Docker image / Maven / npm。補一列:Action 用 major 浮動 tag,
但**安全掃描類 action(Gitleaks、Trivy)必須釘 commit SHA**——它們讀得到 repo 內容與 token,
浮動 tag 等於把供應鏈信任交給第三方。

---

## 批 3:環境可啟動

### 1. Prometheus 掛空目錄 → 容器啟動即退出(實測)

`config/monitoring/prometheus/` 只有 `.gitkeep`,而 compose 把**整個目錄**掛成 `/etc/prometheus:ro`
——連 image 內建的預設設定檔都被遮蔽。

```
level=ERROR msg="Error loading config (--config.file=/etc/prometheus/prometheus.yml)"
err="open /etc/prometheus/prometheus.yml: no such file or directory"
```

`up.sh` 對 exited 的服務直接 die,所以**`up.sh staging`(M2-25)與所有 full profile 環境都起不來**,
並連鎖使 M3-01 失敗。這是 Phase 19 的第一個硬阻斷。

**修法**:補 `prometheus.yml`(scrape 自身 + backend 的 `/actuator/prometheus`)。
**驗證**:同一個掛載下容器 `Up`。

> 設定檔裡註記了一個陷阱:prod 的 `ACTUATOR_EXPOSED_ENDPOINTS` 必須含 `prometheus`,
> 否則 `ctip-backend` 這個 job 會一直 404(見批 6 的 §5.5 差異表修正)。

### 2. Grafana provisioning 目錄是空的 → M3-13 直接 FAIL

**修法**:補 datasource 與 dashboard provider + 一張 `ctip-overview` dashboard。

過程中發現我第一版把檔案放在 `grafana/provisioning/**` ——**層級錯了**:compose 是把
`config/monitoring/grafana` 直接掛成 `/etc/grafana/provisioning`,所以正確位置是
`grafana/datasources/` 與 `grafana/dashboards/`。另外 `/var/lib/grafana` 是 named volume,
dashboard JSON 進不去,故 provider 的 `options.path` 指向 provisioning 目錄本身。

**驗證**:以真實 image + 真實掛載啟動,容器 `Up` 且日誌顯示 provisioning 路徑被載入;
`jq empty` 通過(M3-13 的判準)。

### 3. Kafka 沒有 healthcheck

`up.sh` 只能看容器是否 running,無法判斷 broker 是否真的可用——依賴它的服務會在
「已啟動但還沒 ready」的窗口失敗。

**修法**:`kafka-broker-api-versions.sh --bootstrap-server localhost:9092`。
**驗證**:對真實 `apache/kafka:4.2.1` 實測,broker ready 後第 2 次嘗試即成功。

### 4. ES 開了 security 卻沒有密碼

`.env.prod.example` 設 `ES_SECURITY_ENABLED=true`,但 compose 的 ES 服務沒有傳
`ELASTIC_PASSWORD`——ES 會自動產生密碼並只印在日誌裡,healthcheck 與應用都拿不到,
容器 healthy 不了。而 healthcheck 也沒有帶憑證,security 開啟時 `_cluster/health` 回 401。

**修法**:傳入 `ELASTIC_PASSWORD: ${ELASTICSEARCH_PASSWORD:-}`;healthcheck 改為
`${ELASTIC_PASSWORD:+-u elastic:$ELASTIC_PASSWORD}`——沒設密碼時不帶憑證,設了才帶。

> **未處理**:`ELASTICSEARCH_URL` 在 prod 仍是明文 `http://`。ES 只在 compose 內網,未對外發佈 port,
> 因此不是立即的暴露;真正的 TLS 屬 M3 的部署議題,應在 `docs/deployment/` 定案。
