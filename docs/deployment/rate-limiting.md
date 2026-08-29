# 限流與 Redis 的部署注意事項

> 對應規格:[10-identity-plans.md §10.7](../spec/10-identity-plans.md#107-限流)、
> [06-tech-stack.md §6.5](../spec/06-tech-stack.md#65-授權注意事項)。
> 實作決策見 [ADR 0026](../architecture/decisions/0026-phase17-redis-cache-and-distributed-rate-limit.md)。

---

## 1. 真實 client IP 的限制(§10.7 明文要求記載)

限流的維度 4 以 **client IP** 為鍵。IP 從 `HttpServletRequest.getRemoteAddr()` 取得,
也就是 **TCP 直連對端**——在反向代理／負載平衡器後面,那是代理的位址,不是使用者的。

應用已設定 `server.forward-headers-strategy=framework`,但**只有直連對端落在 `TRUSTED_PROXIES`
之內時才採信 `X-Forwarded-*` / `Forwarded`**。

| `TRUSTED_PROXIES` | 直接對外 | 在代理後面 |
|---|---|---|
| 空(預設) | ✅ 正確:client IP 就是對端 | ⚠️ **所有使用者被算成同一個 IP**(代理的位址),配額被全體共用 |
| 填了代理的網段 | ⚠️ 任何能直連到應用的人都可以偽造 `X-Forwarded-For`,IP 維度形同不存在 | ✅ 正確 |

因此:

- **要嘛只讓代理連得到應用**(應用不對外開埠),**要嘛把 `TRUSTED_PROXIES` 留空**。
  兩者都不做等於把 IP 維度交給攻擊者。
- 值是逗號分隔的 CIDR 或單一位址,例:`TRUSTED_PROXIES=10.0.0.0/8,192.168.1.10`。
- `ENVIRONMENT != mvp` 而 `TRUSTED_PROXIES` 為空時,啟動會記一則 WARN
  ——這是刻意的,§10.7 要求此限制不得被靜默略過。
- 本專案的 compose **沒有**把反向代理放在 backend 前面(前端容器的 nginx 只服務 SPA,
  瀏覽器是直接呼叫 `VITE_API_URL`),所以四份樣板的預設值都是空的。
  自行在前面加 LB／Ingress 時才需要設定。

IPv6 一律收斂到 **`/64` 前綴**再當鍵(一般使用者手上就有整個 `/64`,不收斂等於沒有限流)。

---

## 2. 兩個檢查點

| 檢查點 | 位置 | 維度 |
|---|---|---|
| `RateLimitFilter` | Spring Security filter chain **之前** | 4(匿名 IP) |
| `IdentityRateLimitFilter` | 認證 filter **之後** | 1(API key)、2(使用者)、3(租戶)、5(端點類別) |

**維度 4 必須留在認證之前**:認證失敗會直接寫出 401 並中止 chain,排在其後的限流器根本不會執行
——只要掛一個亂寫的 `Authorization` 標頭就能無限量發送請求(而每次嘗試都查一次資料庫)。

認證**成功**的請求會把維度 4 已扣的 token 歸還(維度 4 的語意是「匿名」IP),改由維度 1–3 約束。
歸還發生在 controller 之前,因此對已認證流量,維度 4 實際上是
「同一 IP **同時進行中**的請求數」上限,而不是速率上限。

端點類別(維度 5)的上限是方案總配額的比例:`read` 100%、`write` 20%、`heavy` 5%(至少 1)。
`heavy` = Bloom 下載、STIX bundle、IOC 匯入。
**注意匿名的 write 配額只有 12/min**(60 的 20%)——共用出口 IP 的環境若大量註冊／登入會撞到。

---

## 3. 後端選擇與故障行為

`RATE_LIMIT_BACKEND=memory | redis`。

| | `memory` | `redis` |
|---|---|---|
| 適用 | 單一實例(mvp) | 多實例(dev/staging/prod) |
| 多實例語意 | **錯誤**:每個實例各一份桶,實際配額 = 設定值 × 實例數 | 正確:共用同一個桶 |
| Redis 不可用 | 不適用 | **應用啟動失敗** |

Redis 連不上時**不降級為記憶體**:限流是安全機制,「後端掛了就等於沒有限流」正是攻擊者要的狀態。
容器的 restart policy 會在 Redis 就緒後把應用拉起來。

快取(`CachePort`,存方案配額與 RBAC 對應)則相反:讀寫失敗只記 WARN 並退化為「每次都查資料庫」
——快取失效不該讓請求失敗。

`ENVIRONMENT != mvp` 而 `RATE_LIMIT_BACKEND=memory` 時,啟動會記一則 WARN。

### 多實例還需要什麼

限流本身在多實例下正確(`DistributedRateLimitTest` 以**兩個 app 實例**驗證),
但**排程仍假設單一實例**([08 §8.7](../spec/08-ingestion-sdk.md))。
要跑多個實例時,必須先為 `@Scheduled` 任務引入 ShedLock 之類的分散式鎖,
或只讓其中一個實例開 `SCHEDULER_ENABLED=true`。

---

## 4. 換成 Valkey

[§6.5](../spec/06-tech-stack.md#65-授權注意事項):Redis 8.x 是 AGPLv3／RSALv2／SSPLv1 三選一,
對 SaaS 有 copyleft 疑慮;Valkey 同時是授權更寬鬆、支援窗口更長的選擇。

替換**只需要改 image 名稱**:

```yaml
  redis:
    image: valkey/valkey:9-alpine        # 原為 redis:8-alpine
    command: ["valkey-server", "--requirepass", "${REDIS_PASSWORD:?}", "--appendonly", "yes"]
    healthcheck:
      test: ["CMD", "valkey-cli", "--no-auth-warning", "-a", "${REDIS_PASSWORD}", "ping"]
```

程式一行都不用改:`RedisCacheAdapter` 只用 `GET` / `SET key value EX ttl` / `DEL`,
`RedisRateLimiter` 只用 `GET` / `EVAL`(bucket4j 的 CAS 腳本),兩者在 Valkey 上完全相容。
環境變數沿用 `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD`。

---

## 5. 排錯

| 症狀 | 原因 |
|---|---|
| 所有 client 共用同一份配額 | 在代理後面而 `TRUSTED_PROXIES` 為空(見 §1) |
| 配額看起來是設定值的兩倍 | 多實例卻用 `RATE_LIMIT_BACKEND=memory` |
| `X-RateLimit-Limit: unlimited` | ENTERPRISE 的 `requests_per_day` 依合約無上限,不是壞掉 |
| 方案改了但沒生效 | 快取 TTL 60 秒;`redis` 後端會在 `plans` 寫入時主動失效,`memory` 後端只在該實例失效 |
| 匿名大量註冊被 429 | 維度 5 的 write 類別上限是 12/min(見 §2) |
