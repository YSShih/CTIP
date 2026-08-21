# Phase 20 — Kafka + 通知（WebSocket / SSE / Webhook）  `[M3]`

## 前置條件
- **`./environment/scripts/dod.sh phase2` 27 項全綠**

## 交付物
- Kafka（KRaft，`apache/kafka:4.2.1`）+ 六個 topic
- `KafkaForwardingListener`（`@TransactionalEventListener(AFTER_COMMIT)`）——**不修改任何發佈端**
- 事件 schema（JSON Schema）存於 `docs/api/events/` + domain event → topic 對應表
- 消費端冪等（`eventId` 去重）
- Flyway `V30`：`webhooks`、`webhook_deliveries`、`notifications`
- `Webhook` 聚合（6 條不變量）+ `WebhookFilter`（伺服器端過濾）
- HMAC-SHA256 簽章（`HMAC(secret, timestamp + "." + body)`）+ 五個送達標頭
- 重試指數退避最多 5 次；連續失敗 5 次 → `DISABLED` + `WebhookDisabled` 事件
- WebSocket（`plans.websocket_enabled` 控制）+ SSE fallback
- 前端：`pages/NotificationCenterPage`、WebSocket 連線狀態指示、自動重連（指數退避）
- 測試：`KafkaEventTest`（L4）、`EventIdempotencyTest`、`KafkaUnavailableTest`、`WebhookDeliveryTest`、`WebhookFilterTest`

## 治理規格
- [13-platform-ops.md §13.1、§13.2](../13-platform-ops.md#131-事件與-kafka-phase-20--m3)
- [02-ddd-model.md §2.4](../02-ddd-model.md#24-domain-event-清單)
- [03-diagrams.md §3.2.9](../03-diagrams.md#329-webhook-聚合-m3)

## 完成判準
```bash
./mvnw -f backend/pom.xml verify -Ptest-all \
  -Dtest='KafkaEventTest,EventIdempotencyTest,KafkaUnavailableTest,WebhookDeliveryTest,WebhookFilterTest'
cd frontend && npx playwright test websocket
```
`KafkaUnavailableTest` 必須驗證 Kafka 停止時業務操作仍成功（只記錄並重試）。
`EventIdempotencyTest` 必須驗證同一 `eventId` 重送不產生重複副作用。

## 不得做的事
- 不得使用 ZooKeeper（Kafka 4.x 已移除）
- 不得把 JPA entity 當 Kafka payload
- **不得修改任何 domain event 發佈端程式碼**（只新增 listener）
- 不得自行實作 listener registry
- 不得把全部事件推給 client 再過濾（`Webhook.matches()` 在伺服器端）
- 不得讓 Kafka 不可用導致業務操作失敗
