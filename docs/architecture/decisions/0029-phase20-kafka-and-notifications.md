# ADR 0029 — Phase 20:Kafka、通知與 Webhook 的實作決策

- **狀態**:accepted
- **日期**:2026-08-29
- **範圍**:`13 §13.1/§13.2`、`02 §2.3/§2.4`、`03 §3.2.9`、`09 §9.1`、`04` 表 24–26 與權限清單、
  `05 §5.4/§5.5`、`10 §10.3`、`12 §12.5`;`docs/api/events/`、`docs/api/webhooks.md`

Phase 20 交付 Kafka(KRaft)、事件 schema、站內通知、WebSocket/SSE 推送與 webhook 送達。
以下是規格未定義、或照字面實作會出錯的地方。

---

## 1. `WebhookFilter` 要的欄位不在 domain event 上

`03 §3.2.9` 寫 `Webhook.matches(DomainEvent)`、`WebhookFilter.accepts(DomainEvent)`,
而過濾維度是 `iocTypes` / `minSeverity` / `tags` / `sourceIds`。
但 `02 §2.4` 的事件身上**沒有這四個欄位**:`IndicatorCreated` 只帶 `type` 與 `tlp`,
severity 與 tags 是**多來源合併之後**才定的,`sourceIds` 更是只存在於聚合裡。

把欄位加進事件等於修改發佈端,而 `13 §13.1` 明文「**不修改任何發佈端**」。

**定調**:新增值物件 `NotificationEvent`(`domain/notification`)——domain event 的
**通知形狀投影**,由 application 層在事件送出之前從聚合補齊過濾欄位。
`Webhook.matches(NotificationEvent)`、`WebhookFilter.accepts(NotificationEvent)`。
不變量 W5(過濾在伺服器端)完全保留;變的只是判定的輸入型別。
它同時是 `ctip.notification.events.v1` 的 payload,schema 見
[`docs/api/events/notification-event.schema.json`](../../api/events/notification-event.schema.json)。

讀不到聚合(例如同批次內已被合併掉)時**通知照發**,只是沒有過濾維度:
漏一則通知比整條管線中斷好得多。

---

## 2. 七種通知型別容不下三個 domain event

`13 §13.2` 的通知型別是**封閉的七項**,而 §2.4 有 12 個事件的消費者包含 Notification(M3)。
三個對不上:

| 事件 | 處置 |
|---|---|
| `SourceRecovered` | 映射到 `SOURCE_FAILURE`(severity `INFO`,標題「來源已恢復」)。它是**來源健康這個頻道**上的一則訊息;訂閱來源失敗的人本來就需要知道它恢復了。為它新增第八種型別會偏離規格明列的清單 |
| `IngestionFailed` | 同上,它就是來源層面的失敗 |
| `IndicatorMerged` | 映射到 `NEW_IOC`(「既有 IOC 取得新的來源佐證」)。對訂閱者而言與新增屬同一個頻道 |

完整對照表在 [`docs/api/events/README.md`](../../api/events/README.md),
並由 `KafkaTopicsTest` 與 §2.4 的事件清單三方綁定。

---

## 3. `@TransactionalEventListener(AFTER_COMMIT)` 在本專案是多餘的

`phase-20.md` 與 §13.1 都寫轉發 listener 用 `@TransactionalEventListener(phase = AFTER_COMMIT)`。
但 `SpringEventPublisherAdapter` **自 Phase 6 起就已經在 `afterCommit` 回呼裡才發佈信封**
——事件抵達 listener 時交易早已提交,再宣告一次 transactional phase 沒有作用。
與 `ThreatConsistencyListener`(Phase 18)同一個判斷,故一律用 `@EventListener`。

**實測補充**:02 §2.4 要求 AFTER_COMMIT 的消費端寫入必須 `REQUIRES_NEW`。
本 phase 把 `NotificationAdapter.recordIfAbsent` 改回預設的 `REQUIRED` 實測,
`EventIdempotencyTest` **仍然通過**——afterCommit 回呼期間連線尚未歸還,
寫入會在歸還(還原 autoCommit)時一併提交。
**規則照留**:`REQUIRES_NEW` 讓寫入有自己明確的提交邊界,而不是依賴連線歸還的副作用。
但「不加就不落庫且不報錯」這個說法在本專案的 JPA + PostgreSQL 組合下**沒有重現**,
不應該被當成既成事實引用。

反過來有一件事**必須**在 `REQUIRES_NEW` 交易**內**做:`Webhook` 因連續失敗而停用時發出的
`WebhookDisabled`。`EventPublisherPort` 會把它掛在**當前交易**的 AFTER_COMMIT 上;
在交易外呼叫,它會掛到一個已經走完 afterCommit 階段的交易上,那一份**永遠不會被觸發**。
`WebhookDeliveryTest.fiveConsecutiveAbandonedEventsDisableTheWebhookAndRaiseWebhookDisabled`
以反向驗證確認了這條斷言不是空的(拿掉發佈 → 轉紅)。

---

## 4. 轉發不得在業務執行緒上進行

`13 §13.1` 規則 7 只說「Kafka 不可用時不得使業務操作失敗」。
照字面實作(在 listener 裡直接 `kafkaTemplate.send()`)會滿足這條規則的字面,
但 **`send()` 在取不到 metadata 時會同步阻塞到 `max.block.ms`(預設 60 秒)**
——broker 掛掉時,每一個事件都讓剛提交完交易的那個請求多等一分鐘。
回 200 但要等一分鐘,實務上與失敗沒有差別。

**定調**:`KafkaEventForwarder` 把轉發交給單執行緒、**有界**佇列的 executor(容量 10,000,
滿了就丟棄並記錄);另在 `application.yml` 把 producer 的 `max.block.ms` 收到 5 秒。
無界佇列在長時間斷線下會把堆積吃光,那才是真的讓業務操作失敗。

---

## 5. `KafkaAdmin` 看不見 `List<NewTopic>` 型別的 bean

`KafkaAdmin` 只會去找 `NewTopic` 與 `KafkaAdmin.NewTopics` 兩種型別的 bean,
一個 `List<NewTopic>` bean **完全不會被看到**——topic 於是只能靠 broker 的
auto-create 產生(分割數與副本數變成 broker 預設值),而且在關閉 auto-create 的
正式環境會直接沒有 topic。

`KafkaEventTest` 因此不只斷言「topic 存在」,還斷言**分割數是我們宣告的 3**
——只驗存在會被 auto-create 蒙混過去。這個缺陷就是被那條斷言抓到的。

---

## 6. SSE fallback 與 WebSocket 共用方案閘門

`09 §9.1` 只在 WebSocket 那一列寫「需 `plans.websocket_enabled`」。
只擋 WebSocket 等於任何 client 改連 `GET /api/v1/events` 就繞過方案限制
——兩者是同一個能力的兩種傳輸。**定調:SSE 一併受 `websocket_enabled` 管制。**

握手的子協定另有一項細節:client 送 `Sec-WebSocket-Protocol: ctip.auth.<jwt>`(§9.1),
但**伺服器回選的是不帶 token 的 `ctip.auth`**——回應標頭會進反向代理與瀏覽器的 log,
不該把 token 原樣送回去。client 因此同時提供兩個值。

---

## 7. Webhook 送達 payload 是通知列的純函數

表 25(`webhook_deliveries`)**沒有 payload 欄位**,而重試會在數分鐘後重新組裝同一個事件。
若各自重新組裝,body 會漂移,而 body 是簽章的一部分——接收端第二次驗簽就會失敗。

**定調**:送達 body 由 `WebhookPayloadPort` 從 `notifications` 那一列**確定性地**產生
(欄位順序寫死、不依賴任何全域 mapper 設定)。重試時以 `eventId` 取回同一列。

`consecutiveFailures`(W3)計的是**事件**不是**嘗試**:若計嘗試次數,
一個用盡五次嘗試的事件就會立刻觸發 W3,W3 便完全等同於 W4,規格不會分成兩條不變量。

---

## 8. 補上的孤兒交付物

| 項目 | 來源 |
|---|---|
| `notification:read` 權限(清單、§10.3 矩陣、`V32` 種子、`RbacMatrix`) | [ADR 0021](0021-phase20-23-spec-resolutions.md) 第 5 節 |
| 前端 webhook 管理頁 `/settings/webhooks` | [ADR 0022](0022-orphan-deliverables.md);`09` 有三個端點與權限,`12 §12.5` 沒有對應頁 |
| `docs/api/webhooks.md`(timestamp 偏差規則) | `13 §13.2` 明文「必須寫入 `docs/api/`」。ADR 0022 原排在 Phase 23,但本 phase 就已經有接收端需要它 |
| `04` 權限清單漏掉的 `threat:manage` | Phase 18 寫進了 §10.3 與 `V31` 種子,但沒有同步 `04`,兩份清單差一項 |

---

## 9. 測試 context 的連線池

Spring 的 test context 是**快取的**:整批測試跑完之前,每個不同組態的 context 都還活著,
而每個都有自己的 HikariCP 池。Phase 20 新增五個 context 之後,
預設的 10 條 × 二十幾個 context 撞上 PostgreSQL 的 `max_connections`(預設 100)。

症狀極具誤導性:`FATAL: remaining connection slots are reserved` 出現在**後面**才載入的
那個 context 上(本次是 `RealtimePushTest` 與 `DistributedRateLimitTest`),
看起來完全像是那兩個測試自己的問題。
`AbstractPostgresIntegrationTest` 因此把池上限固定為 4。

---

## 10. `up.sh` 從來不重建 image(實跑才發現)

Phase 19 為兩個 build target 加上不同的 `image:` tag(§5.8.2 第 5 項),
解決了「兩個 target 共用同一個 image」的 crash-loop。
但 `docker compose up` **只在 image 不存在時才建置**——tag 一旦存在,
之後每一次 `up` 都沿用它,**程式改了也不會重建**。

症狀完全看不出來:staging 起得來、八個服務全 healthy、log 沒有任何異常,
只是跑的是幾小時前的 jar。本 phase 第一次實跑 staging 時六個 Kafka topic 一個都沒建立,
查了 backend 的環境變數(`NOTIFICATION_TRANSPORT=kafka`,正確)、
查了 conditional 裝配,最後是看 `/app/app.jar` 的時間戳才發現。

`up.sh` 第 6 步改為 `up -d --build --remove-orphans`;原始碼沒變時 layer cache 讓它幾乎不花時間。

順帶修掉第二件實跑才看得到的事:**`SseEmitter` 的回應標頭要等到第一次寫入才 flush**,
`curl -N /api/v1/events` 會一直等到逾時,看起來像端點壞掉。
建立連線後立刻送一行 `:keepalive` 註解。

---

## 11. 未實作並回報(規則 17)

- **FCM / APNs adapter**:§13.2 寫「未來的 FCM/APNs adapter」,本 phase 不實作。
  擴充點就是 `RealtimePushPort`——它的實作是登記簿,加一個推播後端不需要改任何呼叫端。
- **多實例的即時推送**:連線登記簿在記憶體(M1–M3 皆為單一實例,與 `08 §8.7` 的排程同一個前提)。
  多實例需要共用的 pub/sub,擴充點同樣是 `RealtimePushPort`。
- **`ctip.notification.events.v1` 以外的五個 topic 目前沒有消費端**:它們是對外的事件串流契約
  (schema 已定版),Phase 21 的稽核消費端會讀 `ctip.audit.events.v1`。
