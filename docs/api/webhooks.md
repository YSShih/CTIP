# Webhook 接收端契約

> 規範來源:[`13-platform-ops.md` §13.2](../spec/13-platform-ops.md#132-通知-phase-20--m3)、
> [`02-ddd-model.md` §2.3 的 W1–W6](../spec/02-ddd-model.md#webhook)。
> §13.2 明文要求「timestamp 偏差規則必須寫入 `docs/api/`」——本檔即是。

CTIP 以 `POST` 把通知送到你登記的 `targetUrl`。本文件是**接收端**要實作的東西。

---

## 1. 請求

```http
POST /your/endpoint HTTP/1.1
Content-Type: application/json; charset=utf-8
X-CTIP-Signature: sha256=9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
X-CTIP-Event-Id: 6f1d2f52-6f0a-4a6f-9a0f-2f1b6d0a1c33
X-CTIP-Event-Type: NEW_IOC
X-CTIP-Delivery-Attempt: 1
X-CTIP-Timestamp: 1755763200
```

```json
{
  "eventId": "6f1d2f52-6f0a-4a6f-9a0f-2f1b6d0a1c33",
  "eventType": "NEW_IOC",
  "occurredAt": "2026-08-29T09:15:04Z",
  "tenantId": "00000000-0000-0000-0000-000000000000",
  "title": "新增 IOC:198.51.100.7",
  "body": "型別 IPV4,TLP CLEAR",
  "severity": "MEDIUM",
  "resourceType": "indicator",
  "resourceId": "3f4a1c0e-2b7d-4f10-9c11-8a2e5d6b7c90"
}
```

`eventType` 為七種之一:`NEW_IOC`、`THREAT_UPDATED`、`IOC_REVOKED`、`SOURCE_FAILURE`、
`SUBSCRIPTION_CHANGED`、`SYNC_SNAPSHOT_READY`、`SYSTEM_ALERT`。

---

## 2. 驗簽(三個步驟,缺一不可)

1. 讀 `X-CTIP-Timestamp`(epoch 秒)。**與自己的時鐘相差超過 5 分鐘就直接拒絕**
   ——不要先驗簽再看時間:簽章對重放的訊息一樣有效,timestamp 才是防重放的那一半。
2. 以**原始位元組**組出 `timestamp + "." + body`。先解析 JSON 再重新序列化會改變位元組,簽章必然對不上。
3. 用你在建立 webhook 時拿到的密鑰算 `HMAC-SHA256`,以**常數時間比較**(`hmac.compare_digest`
   之類)比對 `X-CTIP-Signature` 去掉 `sha256=` 之後的小寫 hex。

```python
import hmac, hashlib, time

MAX_SKEW_SECONDS = 300  # 5 分鐘

def verify(headers, raw_body: bytes, secret: str) -> bool:
    timestamp = int(headers["X-CTIP-Timestamp"])
    if abs(time.time() - timestamp) > MAX_SKEW_SECONDS:
        return False
    expected = hmac.new(
        secret.encode(), f"{timestamp}.".encode() + raw_body, hashlib.sha256
    ).hexdigest()
    provided = headers["X-CTIP-Signature"].removeprefix("sha256=")
    return hmac.compare_digest(expected, provided)
```

> 簽章對象是 `timestamp + "." + body`,**不是**單純的 body。§13.2 同一節內曾有兩種寫法,
> [ADR 0021](../architecture/decisions/0021-phase20-23-spec-resolutions.md) 第 1 節定調取前者。

---

## 3. 冪等

`X-CTIP-Event-Id` 是**同一個事件的固定識別碼**——重試會用同一個值。
接收端必須以它去重:CTIP 保證的是 **at-least-once**,不是 exactly-once。

`X-CTIP-Delivery-Attempt` 是第幾次嘗試(1–5),僅供你診斷,**不要**拿它當去重鍵。

---

## 4. 回應與重試

| 你的回應 | CTIP 的行為 |
|---|---|
| `2xx` | 視為成功,失敗計數歸零 |
| 其他狀態碼、連線失敗、逾時 | 視為失敗,依退避重試 |

- 逾時:連線 + 讀取共 10 秒。**請立即回應**,把處理丟到你自己的佇列;
  在 handler 裡同步處理會讓你被判定為失敗。
- 重試最多 **5 次**(首次 + 4 次重試),退避 1、2、4、8 分鐘(不變量 W4)。
  實際觸發由每 5 分鐘的掃描承擔,所以前兩段會被排程粒度吸收。
- **連續 5 個事件用盡重試** → 該 webhook 轉為 `DISABLED`(不變量 W3),不再送達任何東西,
  同時在通知中心產生一則 `SYSTEM_ALERT`。恢復方式是重新建立一個 webhook。
- **不跟隨轉址**:`3xx` 一律視為失敗。跟隨轉址等於讓你把一個帶有效簽章的請求導去任意主機。

---

## 5. 建立與密鑰

`POST /api/v1/webhooks`(權限 `webhook:manage`)。回應**只此一次**帶出簽章密鑰原文:

```json
{ "secret": "…只此一次…", "webhook": { "id": "…", "status": "ACTIVE", "…": "…" } }
```

- `targetUrl` 必須是 `https://`(不變量 W1)
- 之後任何端點都不再回傳密鑰;遺失只能刪掉重建
- 每租戶數量上限由方案的 `max_webhooks` 決定(不變量 W6);超限回 `403 PLAN_LIMIT_EXCEEDED`

---

## 6. 訂閱過濾

過濾**在伺服器端執行**(不變量 W5)——不符條件的事件根本不會送到你這裡,你不需要自己篩。
四個維度各自「空 = 不限」,非空時取交集:

| 欄位 | 語意 |
|---|---|
| `filterIocTypes` | 事件涉及的 IOC 型別。**指定之後,與 IOC 型別無關的事件(來源失敗、方案異動)不會送達** |
| `filterMinSeverity` | 門檻,含此級別(`INFO` < `LOW` < `MEDIUM` < `HIGH` < `CRITICAL`) |
| `filterTags` | 與事件標籤取交集 |
| `filterSourceIds` | 與事件涉及的來源取交集 |

租戶範圍也是伺服器端決定的:你只會收到自己租戶的事件,以及平台範圍的公開事件。
