# ADR 0021 — Phase 20–23 的規格定調 + 稽核角色模型(批 6)

- **狀態**:accepted
- **日期**:2026-08-28
- **範圍**:`02 §2.3`、`04` 表 24、`05 §5.4.4/§5.5`、`08 §8.7`、`09`(新增即時推送節)、
  `13 §13.2`;`docker-compose.yml`、`config/postgres/01-app-roles.sh`、`application.yml`、
  `AbstractPostgresIntegrationTest`、`IngestionEndToEndTest`
- **背景**:清障計畫的批 6。這是唯一含實作的一批——稽核角色模型使用者已定調「現在改」。

---

## 實作:應用不得以 superuser 連線

### 問題(實測)

`POSTGRES_USER=ctip` 是 postgres image 的**初始 superuser**:

```
select rolsuper, rolbypassrls from pg_roles where rolname = current_user
→ t|t
```

而 superuser **繞過所有 GRANT/REVOKE**。實測:

```sql
REVOKE UPDATE, DELETE ON revoke_probe FROM ctip;
DELETE FROM revoke_probe;   -- 竟然成功
```

Phase 21 的 `REVOKE UPDATE, DELETE ON audit_logs` 因此完全無效,而
**M3-09 明文要求「應用角色的 UPDATE/DELETE 必須被 DB 拒絕(不是被應用碼拒絕)」**
——以單一 superuser 連線的模型,那一項永遠不可能通過。測試基底同樣以容器的 owner 連線,
所以連測試都量不到。

### 修法

三個角色:

| 角色 | 用途 | 權限 |
|---|---|---|
| `ctip`(`POSTGRES_USER`,superuser) | **只給 Flyway 跑 migration** | DDL + 擴充 |
| `ctip_app` | 應用執行期連線 | 只有 DML(SELECT/INSERT/UPDATE/DELETE) |
| `ctip_retention` | 保留清理任務(Phase 21 起) | 建立角色與連線權限;**實際授權由 V33 逐表給** |

- `environment/config/postgres/01-app-roles.sh`(initdb)建立角色。
  關鍵是 **`ALTER DEFAULT PRIVILEGES FOR ROLE ctip`**——Flyway 之後才建表,
  沒有 default privileges 的話新表對 `ctip_app` 完全不可存取。
- `application.yml` 拆成兩組憑證:`spring.datasource.*` 用 `ctip_app`、
  `spring.flyway.user/password` 用 owner。
- `AbstractPostgresIntegrationTest` 建立同樣的角色並注入 `POSTGRES_APP_*`。
  **測試必須跟正式環境用同一組權限**,否則授權斷言量不到東西。

### 驗證

新增 `MigrationIntegrationTest.applicationConnectsAsANonSuperuserRole`:
斷言 `current_user = 'ctip_app'` 且 `rolsuper = false`。

**權限模型真的生效的證據**:切換後 `IngestionEndToEndTest` 立刻以
`ERROR: permission denied for schema public` 失敗——它用
`CREATE TABLE e2e_indicator_snapshot AS …` 建暫存表,而 `ctip_app` 沒有 schema 的 CREATE 權限。

**我選擇改測試而不是放寬權限**:prod 的應用角色本來就不該有 DDL,
在測試放寬就失去「測試與正式環境同權限」的意義。快照改存在 Java 端,SQL 以 `NOT IN (:ids)` 展開。

> ⚠️ initdb 腳本只在**資料目錄為空**時執行一次。既有的開發資料庫需重建 volume
> (`down.sh mvp -v` 後 `up.sh mvp`),否則 `ctip_app` 不存在,應用會連不上。

---

## 規格定調

### 1. Webhook 簽章對象:同一節內兩句互斥

`13 §13.2` 先寫「簽章對象為原始 request body」,後寫
「`HMAC-SHA256(secret, timestamp + "." + body)`——含 timestamp 以防重放」。

**以 `timestamp + "." + body` 為準**:`phase-20.md` 與 M3-06 都採這一種,而且只有它防得了重放。

### 2. W3 的事件名:`SystemAlert` vs `WebhookDisabled`

`02 §2.3` 的 W3 寫 `SystemAlert`,但**同一份 02 的 §2.4 事件清單裡沒有這個事件**,
而 §2.4、`13 §13.2`、`phase-20.md` 都寫 `WebhookDisabled`。**以 `WebhookDisabled` 為準**。

### 3. Webhook secret:只存雜湊卻要算 HMAC(數學上不可能)

W2 與 `04` 表 24 都寫「只存 secret 的 SHA-256」,但 `02` 的行為清單有 `Webhook.sign(byte[])`,
`13 §13.2` 要求每次送達都以 `HMAC-SHA256(secret, …)` 簽章。**伺服器手上只有摘要,重建不出 secret**。

**定調**:secret 以 **AES-GCM 加密**儲存(欄位改 `secret_encrypted BYTEA`),
金鑰來自新環境變數 `WEBHOOK_SECRET_KEK`。對外契約不變(原文僅建立時回傳一次)。

> 這與 refresh token／API key 只存雜湊不同,因為那兩者是**驗證**(比對即可),
> 而 webhook 簽章是**產生**(必須持有原文)。

### 4. WebSocket / SSE 在 `09` 完全沒有定義

`phase-20.md` 要交付、`12` 有 `VITE_WS_URL`、`01` 有 `interfaces/websocket/`,
**M3-05 還要用 Playwright 測它**——但 `09` 沒有任何路徑、協定或認證方式,測試無從得知要連哪裡。

**定調**(新增 `09` 的「即時推送」節):原生 WebSocket(不用 STOMP/SockJS)、
`GET /api/v1/ws` 與 SSE fallback `GET /api/v1/events`、
認證以 `Sec-WebSocket-Protocol: ctip.auth.<jwt>` 攜帶(瀏覽器 WS API 無法設自訂標頭;
**不接受 query string 傳 token**,那會進 access log)、連線綁 tenant 並沿用 §7.9 輸出過濾。

### 5. `/notifications` 兩個端點沒有權限碼

同 `/subscription`(ADR 0019 §9)。**定調 Phase 20 新增 `notification:read`**,
三處同步;不現在加進矩陣,以免 `RbacMatrixTest` 立刻轉紅。

### 6. 保留清理任務:13 說六個,08 只定義四個

缺 `webhook_deliveries` 與 `EXPIRED` indicator 兩項的排程列。
那兩項的保留天數變數(`DELIVERY_RETENTION_DAYS`、`INDICATOR_RETENTION_DAYS`)
**早就存在於 compose 與 `application.yml`,只是沒有任何任務會去讀**。
補 `DELIVERY_CLEANUP_CRON` 與 `INDICATOR_CLEANUP_CRON`。

### 7. `ACTUATOR_EXPOSED_ENDPOINTS`

`13 §13.6` 要求 prod 必須暴露 `prometheus`(否則 Prometheus 的 `ctip-backend` job 一直 404),
但四份樣板一律 `health,info`,`§5.5` 差異表也沒有這一列。
**prod 改為 `health,info,prometheus`**,並補進差異表。

同時 `phase-22.md` 的判準用 `up.sh dev` 驗 `/actuator/prometheus`——但 dev 是 `health,info`
且 `COMPOSE_PROFILES=standard`(不啟 Prometheus)。**判準改用 `staging`**。

> §13.6 另要求「`prometheus` 需限制來源 IP」,而 `SecurityConfig` 是 `anyRequest().permitAll()`
> ——**該限制目前沒有任何實作位置**,已列入 `phase-22.md` 的交付物。
