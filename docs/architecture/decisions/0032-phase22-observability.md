# ADR 0032 — Phase 22:監控、日誌、追蹤

- **狀態**:已採納(2026-08-30)
- **範圍**:`docs/spec/phases/phase-22.md`、治理規格 [13 §13.6](../../spec/13-platform-ops.md#136-監控日誌追蹤-phase-22--m3)、[09 §9.4](../../spec/09-api.md#94-統一錯誤回應)
- **前置**:Phase 21 完成判準全綠

---

## 1. Micrometer 進 `ctip-core`(而不是再定義一個 `MetricsPort`)

§13.6 的六個 `ctip.*` 指標,產生點全部在 application 層:攝取筆數與 stage 耗時在
`IngestionBatchExecutor` / `IngestionPipeline`、Bloom 生成耗時在 `BloomGenerationService`、
再散布過濾筆數在 `RedistributionFilter`。三個選項:

1. 在 core 定義 `MetricsPort`,app 以 Micrometer 實作
2. 直接讓 core 依賴 `io.micrometer:micrometer-core`
3. 把產生點全部搬到 app(以 decorator 包住 core 的 bean)

選 **2**。理由:`micrometer-core` 與 `slf4j-api` 同性質——是**門面**而非基礎設施 client,
而 core 早就直接用 slf4j 而沒有 `LoggerPort`。選項 1 是為了對稱而加的抽象(執行規則 18);
選項 3 對 `RedistributionFilter` 這種具體類別根本包不起來(它不是介面)。

代價是 domain 層多了一條可能被誤用的路。因此**同時把 `io.micrometer..` 加進 ArchUnit 規則 1
的禁止清單**(01 §1.9):application 可用,domain 不得碰。

## 2. 指標在啟動時就註冊,不等第一次命中

`ctip.ingestion.stage.duration` 依 pipeline 的 stage 清單、`ctip.ingestion.records` 的三個
`result`、`ctip.bloom.generation.duration` 的兩個 scope、`ctip.ratelimit.rejected` 的六個維度,
全部在建構時註冊。Prometheus 的「序列不存在」與「值為 0」在告警規則上是兩件事:
剛啟動的實例若整組指標都不存在,dashboard 與 alert 都會看到 no data 而不是 0。

## 3. `ctip.source.sync.lag` 是 gauge 而不是 timer

§13.6 只寫「來源同步延遲」。同步**耗時**已經由 `source_sync` 表與健康狀態記錄;
運維要看的是「距上次成功同步過了多久」,因此以 `MultiGauge` 實作
(`SourceSyncLagBinder`),來源清單由 `MetricsSchedulers` 每分鐘重整。
**從未成功過的來源回 `NaN` 而不是 0**——0 的意思是「剛剛才同步過」,對從未成功的來源是相反的結論。

## 4. `kafka.consumer.lag` 是彙總視角

Micrometer 綁 Kafka client 產生的名稱是 `kafka.consumer.fetch.manager.records.lag`
(每個 topic-partition 一條),與規格指名的 `kafka.consumer.lag` 不同。
`KafkaConsumerLagBinder` 註冊的是那組序列的**最大值**——consumer lag 的運維語意本來就是
「最落後的分割落後多少」。一條都還沒出現時回 `NaN`,不回 0。

## 5. `lettuce.*` 與 `elasticsearch.cluster.health` 要自己綁

Boot 4 的 `spring-boot-data-redis` 與 `spring-boot-elasticsearch` **都沒有 metrics autoconfig**
(已逐一檢查 jar)。前者以 `ClientResourcesBuilderCustomizer` 掛
`MicrometerCommandLatencyRecorder`;後者自寫 `MeterBinder`,以 supplier 取 cluster health,
查詢失敗回 `NaN` 且只記 debug(§13.7 明令 ES 不可用不得影響應用,而抓取頻率是每 15 秒)。

## 6. ⚠️ `management.tracing.export.enabled=false` 會連「接收 traceparent」一起關掉

**本 phase 抓到的最大地雷。** 原本的設定是「沒有 OTLP collector,所以關掉 export」,
結果 `ErrorResponseTest.incomingTraceparentIsHonored` 轉紅:傳入的 `traceparent` 被忽略,
server span 變成一個全新的 trace。原因是 Boot 的 `TextMapPropagator` bean 也掛在
`@ConditionalOnEnabledTracingExport` 上——關掉全域 export 等於連 W3C 傳遞都不裝配。

解法:全域 `management.tracing.export.enabled` 維持 `true`,真正要控制的是
`management.tracing.export.otlp.enabled`(`TRACING_EXPORT_ENABLED`,預設 false)。
這樣 traceId 與 W3C 傳遞照常,只是不把 span 送出去。

## 7. `TraceIdFilter` 改排在觀測 filter 之後,traceId 以 span 為準

Boot 的 `ServerHttpObservationFilter` 註冊在 `HIGHEST_PRECEDENCE + 1`。
`TraceIdFilter` 因此改為 `HIGHEST_PRECEDENCE + 2`:進來時 server span 已建立,
直接取它的 traceId。若這裡仍自行產生亂數,錯誤回應上的 traceId 與 OTel 送出的 trace
會是兩個不同的值,§13.6 要的「唯一關聯線索」等於不存在(M3-16 就是在驗這件事)。

`requestId`(§13.6 的九個必含欄位之一)同樣由這道 filter 產生:接受用戶端帶入的
`X-Request-Id`(格式不合就自己產生,不把外部字串原樣放進日誌),並回寫同名回應標頭。
它不是 09 §9.x 的 API 契約、也不在 CORS 的 `exposedHeaders` 內——用途是伺服器端與反向代理的關聯。

它仍是**最外層的錯誤網**(只有觀測 filter 在它之前)。一個細節:例外往上拋時觀測 scope 已關閉、
MDC 的 traceId 已被移除,因此 catch 區塊要**重新放回** traceId,錯誤回應與那則 ERROR 日誌才有值。

## 8. 追蹤鏈用一個切面,而且切入點只點名 adapter

`TracingAspect` 對五處建立 span:`com.ctip.application..*Service`、
`infrastructure.persistence..*Adapter`、`infrastructure.redis..*`、
`infrastructure.elasticsearch..*Adapter`、Kafka 的兩個具名類別。

- 用切面而非在每個 adapter 手寫 `Observation`:追蹤是橫切關注,而多數 adapter 的建構子
  已接近 checkstyle 的 5 參數上限(Phase 21 的稽核是同一個理由)。
- **切入點不能用整個套件**:`infrastructure.elasticsearch` 內有 `final` 的
  `IndicatorSearchIndex`,被切到時 CGLIB 直接建不出代理,**整個 context 起不來**
  (實測:`SearchFallbackTest` 的 ES context)。`infrastructure.kafka` 的 `KafkaTopics` 同型態。
- span 名稱是低基數的五個;類別#方法放在 contextual name 與**高基數**欄位——
  `Observation` 同時會產生計時指標,低基數欄位會變成指標的 tag。

## 9. 日誌格式由 profile 決定,不做成環境變數

mvp / dev 用人看的單行格式,staging / prod 用 JSON。logback 的條件式 `<if>` 需要 Janino
(版本表沒有它);而「用變數當 appender 名稱」在打錯字時會**安靜地不輸出任何日誌**,
那是最糟的失敗模式。

`logback-spring.xml` 讀的是 `ENVIRONMENT` 環境變數而非 `ctip.environment` 屬性:
後者的值是 `${ENVIRONMENT}` 這個必填佔位符,而日誌系統在 environment-prepared 階段就初始化,
那時測試的 `DynamicPropertySource` 還沒進來,佔位符解不開會讓整個 context 起不來(實測)。

## 10. 九個必含欄位由 `CtipJsonEncoder` 保證,不是由 XML 保證

provider 清單寫在 Java:九個欄位是規格的強制項,寫在 Java 才有辦法用單元測試逐項驗證
(`CtipJsonEncoderTest`)。五個 MDC 關聯欄位由自訂 provider **一律輸出**,沒有值就是空字串——
缺欄位與空值在下游(Loki / ES)的查詢是兩件事。

以程式加入的 logback 元件必須自己 `start()`:Joran 只啟動 XML 裡宣告的子元件,
漏掉時 decorate 期的 delegate 是 null(實測 NPE)。

## 11. 遮罩是第二道防線,規則只有一份

`SensitiveMasks` 同時供 JSON(`ValueMasker`)與純文字(`%mask(...)` 轉換器)使用。
第一道防線是不把憑證交給 logger(`SensitiveLogTest` 的第一個案例驗這件事),
但字串會經由例外訊息、第三方函式庫的 debug 輸出等不受本專案控制的路徑漏出來。

**刻意不遮罩十六進位摘要**:指紋與 traceId 是查問題的主線索。判別方式是
「同時含大小寫字母的 40 碼以上 base62 串」——refresh token(48)與 webhook 密鑰(40)符合,
SHA-256 摘要與 UUID 不符合。

## 12. `/actuator/prometheus` 的來源 IP 限制只能是 filter,且預設不是全開

§13.6 要求限制來源 IP,而 `SecurityConfig` 是 `anyRequest().permitAll()`(授權一律在方法層),
actuator 端點沒有任何方法層宣告可掛——這道限制因此只能是一個 filter
(`PrometheusAccessFilter`,排在 `TraceIdFilter` 之後,被拒絕的抓取仍拿到 §9.4 的統一錯誤結構)。

`PROMETHEUS_ALLOWED_IPS` **空清單 = 拒絕所有來源**。指標端點會洩漏租戶數、來源清單、
端點路徑與流量樣態,「沒設定就全開」對安全優先的預設值是錯的方向;預設值涵蓋 loopback 與
RFC1918(compose 網段),對外一律拒絕。

另補一條啟動守衛:prod 的 `ACTUATOR_EXPOSED_ENDPOINTS` 只得是 `health` / `info` / `prometheus`,
其餘(`env`、`beans`、`configprops`、`heapdump`…)**拒絕啟動**而不是記 WARN。

## 13. staging 必須暴露 `prometheus`(規格回寫)

phase-22 的完成判準是 `up.sh staging` 之後 `curl /actuator/prometheus`,
而 05 §5.5 的差異表把 staging 列為 `health,info`——照字面設定,判準必然 404。
staging 改為 `health,info,prometheus`(它本來就是 full profile、有 Prometheus 容器在抓)。

## 14. 移除 `BloomSnapshotService.generateAll()`

`ctip.bloom.generation.duration{scope}` 要的是每個 scope 各自的耗時,因此逐一 scope 的迴圈
必須在呼叫端(`BloomGenerationService`)。`generateAll()` 因而沒有生產呼叫端,直接移除
(執行規則 16:不留永不可達的程式碼);失敗隔離的粒度不變,仍是 scope。
`BloomSnapshotService` 的 `planner` 參數也隨之不再需要(5 → 4 個參數)。

## 15. ⚠️ Prometheus 的 exemplar 與 Lettuce 指標會在啟動時死鎖

`management.tracing.exemplars.include: none` **不是可選的調校**,而是修一個會讓
`RATE_LIMIT_BACKEND=redis` 的環境(dev / staging / prod)**完全起不來**的死鎖:

```text
main 執行緒        : 建立 singleton(持有 bean factory 的建立鎖)
                    → RedisConfig.ctipRedisConnection → RedisClient.connect() 等連線 future
lettuce netty 執行緒: 收到 Redis 的回應 → CommandHandler.recordLatency
                    → MicrometerCommandLatencyRecorder → PrometheusTimer.record
                    → ExemplarSampler → Boot 的 LazyTracingSpanContext
                    → 向 bean factory 要 Tracer → 卡在同一把建立鎖
```

連線 future 只能由那條 netty 執行緒完成,而它正在等 main 放開鎖。兩邊互等,啟動永遠不會結束——
**而且沒有任何錯誤訊息**,看起來就只是「啟動很慢」。

抓到它的是 Phase 17 的 `DistributedRateLimitTest`(它是唯一一支真的連 Redis 起兩個 context 的測試),
症狀是整個 `verify` 卡在那支測試上;是 thread dump 才看出來的。exemplar 不是 §13.6 的要求項,
關掉它是最小的解;`MetricsCompletenessTest.prometheusExemplarsStayDisabled` 把這個設定鎖住並附上理由。

> 教訓可一般化:**任何在「非主執行緒」記錄的 Prometheus 指標,都不得在記錄路徑上向 Spring 要 bean**。
> 這是 exemplar 的預設行為,不是本專案的程式碼。
