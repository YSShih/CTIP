# CTIP 事件契約(Kafka)

> 規範來源:[`13-platform-ops.md` §13.1](../../spec/13-platform-ops.md#131-事件與-kafka-phase-20--m3)、
> [`02-ddd-model.md` §2.4](../../spec/02-ddd-model.md#24-domain-event-清單)。
> **本檔與 `KafkaTopics` 由 `KafkaTopicsTest` 綁在一起**——新增 domain event 而沒有更新這裡,測試會轉紅。

Kafka 為 **KRaft 模式**(`apache/kafka:4.2.1`),**不使用 ZooKeeper**(4.x 已移除)。
broker 只屬 `full` profile(staging / prod);mvp 與 dev 以 `NOTIFICATION_TRANSPORT=in-process` 完全不接觸它。

---

## 1. Topic

命名格式 `ctip.<domain>.<event>.v<schema-version>`。分割數 3、副本數 1(單一 broker)。

| Topic | 內容 | Schema |
|---|---|---|
| `ctip.threat.ingest.v1` | 攝取生命週期 | [`envelope.schema.json`](envelope.schema.json) |
| `ctip.threat.normalized.v1` | Threat 聚合的變更 | 同上 |
| `ctip.indicator.updated.v1` | Indicator 聚合的全部事件 | 同上 |
| `ctip.audit.events.v1` | 身分、租戶、API key、訂閱、webhook 停用(Phase 21 的稽核消費端由此讀取) | 同上 |
| `ctip.system.alert.v1` | 來源健康與 Bloom snapshot 就緒 | 同上 |
| `ctip.notification.events.v1` | **通知形狀的投影**;本平台唯一有消費端的 topic | [`notification-event.schema.json`](notification-event.schema.json) |

訊息 key 一律為 `eventId`:同一個事件重送必然落在同一個 partition,消費端的去重(§13.1 規則 5)
因此不必跨 partition 協調。

---

## 2. Domain event → topic 對照表

§2.4 的 21 個事件各進**一個**領域 topic。標「通知」的另外會產生一則 `NotificationEvent`
投影到 `ctip.notification.events.v1`(§2.4 的「消費者」欄含 Notification(M3) 者)。

| Domain event | Topic | 通知型別 |
|---|---|---|
| `IndicatorCreated` | `ctip.indicator.updated.v1` | `NEW_IOC` |
| `IndicatorMerged` | `ctip.indicator.updated.v1` | `NEW_IOC` |
| `IndicatorExpired` | `ctip.indicator.updated.v1` | —— |
| `IndicatorRevoked` | `ctip.indicator.updated.v1` | `IOC_REVOKED` |
| `IndicatorFalsePositiveReported` | `ctip.indicator.updated.v1` | —— |
| `IndicatorTlpTightened` | `ctip.indicator.updated.v1` | —— |
| `ThreatUpdated` | `ctip.threat.normalized.v1` | `THREAT_UPDATED` |
| `IngestionStarted` | `ctip.threat.ingest.v1` | —— |
| `IngestionCompleted` | `ctip.threat.ingest.v1` | —— |
| `IngestionFailed` | `ctip.threat.ingest.v1` | `SOURCE_FAILURE` |
| `SourceDegraded` | `ctip.system.alert.v1` | `SOURCE_FAILURE` |
| `SourceFailed` | `ctip.system.alert.v1` | `SOURCE_FAILURE` |
| `SourceRecovered` | `ctip.system.alert.v1` | `SOURCE_FAILURE` |
| `BloomSnapshotReady` | `ctip.system.alert.v1` | `SYNC_SNAPSHOT_READY` |
| `TenantCreated` | `ctip.audit.events.v1` | —— |
| `UserRegistered` | `ctip.audit.events.v1` | —— |
| `TokenReuseDetected` | `ctip.audit.events.v1` | `SYSTEM_ALERT`(只給當事人) |
| `ApiKeyCreated` | `ctip.audit.events.v1` | —— |
| `ApiKeyRevoked` | `ctip.audit.events.v1` | —— |
| `SubscriptionChanged` | `ctip.audit.events.v1` | `SUBSCRIPTION_CHANGED` |
| `WebhookDisabled` | `ctip.audit.events.v1` | `SYSTEM_ALERT` |

**兩個對應上的取捨**(ADR 0029):

1. **`SourceRecovered` 也映射到 `SOURCE_FAILURE`。** §13.2 的通知型別是封閉的七項,裡面沒有
   「來源恢復」。它是來源健康這個頻道上的一則訊息(severity `INFO`,標題寫「來源已恢復」),
   訂閱來源失敗的人本來就需要知道它恢復了。為了它新增第八種型別會偏離規格明列的清單。
2. **`IndicatorMerged` 映射到 `NEW_IOC`。** 它代表「既有的 IOC 取得新的來源佐證」,
   對訂閱者而言與新增同一個頻道;七項型別裡沒有更貼切的。

---

## 3. 信封欄位(§13.1 規則 4,強制)

每一則訊息都含 `eventId`、`eventType`、`occurredAt`、`tenantId`、`traceId`;
領域內容一律在 `payload` 之下。

```json
{
  "eventId": "6f1d2f52-6f0a-4a6f-9a0f-2f1b6d0a1c33",
  "eventType": "IndicatorCreated",
  "occurredAt": "2026-08-29T09:15:04Z",
  "tenantId": "00000000-0000-0000-0000-000000000000",
  "traceId": "8f2a1c0e2b7d4f10",
  "payload": {
    "indicatorId": "3f4a1c0e-2b7d-4f10-9c11-8a2e5d6b7c90",
    "tenantId": "00000000-0000-0000-0000-000000000000",
    "type": "IPV4",
    "normalizedValue": "198.51.100.7",
    "tlp": "CLEAR"
  }
}
```

規則:

- **不得**把 JPA entity 當作 payload(§13.1 規則 2);payload 是 domain event 的欄位,獨立於持久化模型
- 識別碼值物件在線上是**字串**,不是 `{"value": …}`(`ValueObjectJsonModule`)
- `traceId` 可為 `null`——排程與啟動流程觸發的事件沒有請求可對應
- 消費端必須**冪等**,去重鍵為 `eventId`(§13.1 規則 5)

---

## 4. 相容性

schema 版本在 topic 名稱裡(`v1`)。**新增 optional 欄位**不算破壞性變更,直接沿用 `v1`;
**刪除欄位、改型別、改語意**必須開 `v2` 並讓兩個 topic 並行到所有消費端遷移完成。

---

## 5. Webhook 送達

外送的 webhook 不走 Kafka,格式與簽章規則另見 [`../webhooks.md`](../webhooks.md)。
