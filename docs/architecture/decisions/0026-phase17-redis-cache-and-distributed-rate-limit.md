# ADR 0026 — Phase 17:Redis(快取 + 分散式限流)

- **狀態**:accepted
- **日期**:2026-08-29
- **範圍**:`backend/`(`application/port`、`domain/plan`、`infrastructure/{redis,cache,ratelimit,web,persistence}`、
  `config/{RedisConfig,RateLimitConfig,ForwardedHeadersConfig,SecurityConfig,StartupValidator,CtipProperties}`)、
  `environment/`(compose + 五份樣板)、`docs/deployment/rate-limiting.md`、
  `docs/spec/{01,05,06,10}`、`docs/spec/phases/phase-23.md`
- **背景**:phase-17 執行單。[ADR 0020](0020-phase17-19-spec-resolutions.md) 已定調兩件事——
  限流維度 1–3 歸 Phase 17(不是 Phase 14)、`endpointClass` 以方案總配額的比例表示
  (read 100% / write 20% / heavy 5%);[ADR 0019](0019-phase14-16-spec-resolutions.md) 已把
  `RateLimiterPort` 的簽章改成「限額隨呼叫傳入」。本 phase 是把這些接上真正的分散式後端。

---

## 1. 維度 5 的鍵必須含 subject(**規格偏離**)

`10 §10.7` 的五個鍵格式:

```text
1. ratelimit:key:{apiKeyId}:{window}
2. ratelimit:user:{userId}:{window}
3. ratelimit:tenant:{tenantId}:{window}
4. ratelimit:ip:{normalizedIp}:{window}
5. ratelimit:{scope}:{endpointClass}:{window}      ← 沒有 subject
```

前四個都是「某個主體的桶」,第五個照字面是 **全平台共用一個桶**:
`ratelimit:tenant:read:minute` 不屬於任何租戶,任何一個租戶(或任何一個匿名 IP)
把它打滿,**其他所有人都會被拒絕**——一行 `curl` 迴圈就能讓整個平台的讀取端點回 429。

而 ADR 0020 選擇比例上限的理由是「保證分類上限恆低於**總上限**」,那句話只有在
per-subject 的前提下才成立(全域桶跟某個方案的總上限之間沒有可比性)。

**定調**:維度 5 的鍵為 `ratelimit:{scope}:{subject}:{endpointClass}:{window}`,
subject 沿用當下最 specific 的維度(已認證取 apiKey/user,匿名取 IP)。
依 §0.4 的優先序,這是安全性(避免跨租戶互相癱瘓)勝過字面相容的取捨。
`10 §10.7` 已回寫。

## 2. 維度 4 對已認證請求「先扣後退」

三個約束互相拉扯:

| 約束 | 出處 |
|---|---|
| 限流必須排在**認證之前**,否則無效憑證完全繞過限流 | ADR 0012 決策 16(實測:75 次無效 token 全回 401、零個 429) |
| 維度 4 是「**匿名** IP」;方案配額(60/300/1200/6000)才是已認證者該受的約束 | §10.7、§10.6 |
| 一個請求是不是「已認證」,要等認證跑完才知道 | — |

照 Phase 14 的實作(每個請求都以 ANONYMOUS 的 60/min 扣 IP 桶),
**ENTERPRISE 的 client 實際上被綁死在 60/min**——方案分級完全沒有意義。
但把維度 4 移到認證之後,ADR 0012 修掉的繞過就會回來。

**定調**:維度 4 照舊在認證之前扣;認證**成功**後由 `IdentityRateLimitFilter`
呼叫新增的 `RateLimiterPort.refund` 歸還,並清掉已寫出的 `X-RateLimit-*`
(那組數字是匿名方案的,對已認證者沒有意義),改由維度 1–3／5 重新寫。

副作用是明確的:對已認證流量,維度 4 從「速率上限」變成
「同一 IP **同時進行中**的請求數上限」(歸還發生在 controller 之前)。
認證**失敗**者沒有歸還的機會,暴力破解仍然被擋——`RateLimitTest`
的迴歸鎖(無效憑證一樣消耗配額)原封不動通過。

## 3. 兩個檢查點,不是一個 filter 也不是 Decorator

§10.7 要求「使用 filter 或 `HandlerInterceptor`,禁止 Decorator 堆疊」,
而 ADR 0020 又要求維度 4 與維度 1–3 分屬認證前後。這兩件事不衝突:

- `RateLimitFilter`(security chain 之前):維度 4
- `IdentityRateLimitFilter`(認證 filter 之後,`addFilterAfter`):維度 1–3、5

兩者共用 `RateLimitResponder`(標頭、「最緊維度」的比較、429 的寫出)與
`RateLimitScope`(`/actuator` 與 CORS preflight 的豁免),所以「集中一處、可讀可除錯」
仍然成立——不是兩份各自演化的限流邏輯,是同一份邏輯的兩個呼叫點。

## 4. 端點分類:以 POST 表達的查詢歸 `read`

`POST /iocs/search` 與 `POST /iocs/lookup` 照 HTTP 方法字面屬 write,
那會把前端唯一的搜尋路徑壓到總配額的 20%。§10.7 的分類寫的是
「`read`(GET/**查詢**)」——查詢就是查詢,與方法無關。`heavy` 取 §10.7 明列的三支
(`/sync/bloom`、`/stix/bundle`、`/iocs/import`),且優先於方法判定。

## 5. Redis 的桶鍵多帶一段容量

bucket4j 的 proxy 把 `BucketConfiguration` 一併存進 Redis,**建立後不會因為呼叫端傳了
不同的限額而更新**。方案降級時舊桶會沿用舊的(較寬的)容量直到過期——那是 fail-open。
`withImplicitConfigurationReplacement` 只在版本號**遞增**時替換,對降級無效。

**定調**:Redis 鍵為 `{RateLimitKey.asString()}:{capacity}`,即「限額改變就是換一個桶」,
與 `InMemoryRateLimiter`「限額改變即重建 bucket」同語意;舊鍵由 TTL 自行消失。
代價是鍵格式與 §10.7 的字面不同(僅限 Redis 內部,不影響任何對外契約)。

## 6. Redis 不可用:限流 fail-fast,快取 fail-soft

| 元件 | 行為 | 理由 |
|---|---|---|
| `RedisRateLimiter` | 例外往上冒;連線在啟動時建立,連不上就**啟動失敗** | 限流是安全機制。「後端掛了就悄悄改用記憶體」= 多實例下限流形同虛設,那正是攻擊者要的狀態(§0.4 安全性優先) |
| `RedisCacheAdapter` | 捕捉 `DataAccessException`,記 WARN,退化為每次重新載入 | 快取的契約本來就允許 miss;讓它變成例外等於「Redis 抖一下,整個 API 掛掉」 |

`RedisCacheAdapter` 刻意**不**捕捉其他 `RuntimeException`——那是程式錯誤,不該被靜默。

## 7. `CachePort` 的值型別是 `String`

泛型化的 `get(key, Class<T>)` 會把序列化器的行為(哪些型別可還原、日期怎麼寫)
綁進 port 的契約,§6.5 要求的「Redis → Valkey 只需改 infrastructure 實作」就不再只是換一個 class。
序列化因此留在 infrastructure:`Plan` 走 JSON(`PlanCacheCodec`),
權限碼集合走逗號串接(字元集是 `[a-z:-]`,不需要 JSON,也就少一種「解不開」的失敗模式)。

`PlanCacheCodec.decode` 解不開時回 `empty` 而非丟例外:滾動升級期間新舊版本會同時對著
同一個 Redis 讀寫,讓它變成例外等於「升級到一半整個 API 掛掉」。

新增 **ArchUnit 規則 11**:`com.ctip.application..` 不得依賴
`io.lettuce..` / `redis.clients..` / `org.springframework.data.redis..` / `io.github.bucket4j..`。
規則 1 只擋 domain,而 port 定義在 application——真正會發生洩漏的地方是那裡。

## 8. `CachePort` 的消費者是兩個既有的行程內快取,不是新造的需求

規則 16 禁止 placeholder。`CachePort` 若沒有真實呼叫端就是規格裡的裝飾品,
而本專案已經有兩份自己手寫的 `ConcurrentHashMap` + TTL:

- `PlanRepositoryAdapter`(Phase 14):限流的兩個檢查點**每個請求**都要讀方案限額
- `RolePermissionRepositoryAdapter`(Phase 13,註解明寫「分散式快取為 Phase 17」)

兩者都改走 `CachePort`。差別不只是少一份程式:行程內的 map **無法跨實例失效**——
SYSTEM_ADMIN 在實例 A 調整方案後,實例 B 會繼續用舊配額直到 TTL 到期。
`DistributedRateLimitTest.planChangeOnOneInstanceIsVisibleOnTheOther` 就是在量這件事。

**訂閱仍然不快取**:哪個租戶用哪個方案必須立即生效(降級延遲一分鐘 = 那一分鐘內還是舊配額),
這維持 Phase 14 的決策。代價是每個已認證請求多一次索引查詢。

## 9. `SyncThrottlePort` 收斂成一個實作

Phase 16 的 `InMemorySyncThrottle` 有自己的 TTL 與清掃邏輯,而那正是 `CachePort` 的語意。
改為 `CacheBackedSyncThrottle`(存一筆 TTL = 該方案最小間隔的時間戳),
`memory` 後端行為與原本相同,`redis` 後端就是 Phase 16 交接單寫的 `SETEX`。
`InMemorySyncThrottle` 刪除——同一件事兩份實作是第二個真相來源。

## 10. 信任的代理來源:預設誰都不信

Boot 的 `server.forward-headers-strategy=framework` 註冊的 `ForwardedHeaderFilter`
**無條件採信** `X-Forwarded-*`。應用只要有一條路徑能被直接連到,任何人都可以自稱來自任意 IP,
維度 4 就等於不存在(每個請求換一個假 IP)。

**定調**:以 `TrustedProxyForwardedHeaderFilter`(同型別 bean,Boot 的 `@ConditionalOnMissingFilterBean`
會退讓)取代,只有直連對端落在 `TRUSTED_PROXIES`(新增環境變數,CIDR 清單)之內時才處理轉發標頭。
**預設為空 = fail-closed**:代理後方忘了設定會讓限流過嚴(所有人算成同一個 IP),
而不是被繞過。`ENVIRONMENT != mvp` 而該值為空時啟動記 WARN——§10.7 要求此限制不得被靜默略過;
完整說明在 `docs/deployment/rate-limiting.md`(phase-17「不得做的事」第 4 條)。

判定用 Spring Security 既有的 `IpAddressMatcher`,不新增相依。

## 11. mvp profile 關閉 Redis 健康檢查

`spring-boot-data-redis` 一在 classpath 上,actuator 就會加入 redis 健康檢查;
而 mvp 的 compose **不啟動 redis**(它屬 standard/full profile)。不關掉的話
`/actuator/health` 永遠 DOWN → 容器永遠 unhealthy → `depends_on` 卡死
(與 v1.1 的第二項阻斷缺陷同一型態)。只在 `application-mvp.yml` 關閉,
dev/staging/prod 仍然監測 Redis。

## 12. 依規則 6／17 回報的工具鏈事實

1. **`TestRestTemplate` 在 Boot 4 移出了 `spring-boot-test`**(改在
   `spring-boot-restclient-test`,版本表未列)。`DistributedRateLimitTest` 因此用 JDK 的
   `java.net.http.HttpClient`——不新增任何相依。與 §6.3.6 第 5 條(MockMvc 支援被拆出)同一型態,
   已補入 `06 §6.3.6`。
2. `com.bucket4j:bucket4j-redis` 的 `lettuce-core` 是 **provided(6.1.8)**,不會被帶進來;
   §6.2.2 宣稱「已逐一比對其參照的 class/method 在 7.5.2 皆存在」——本 phase 以位元碼逐項核對
   (`eval(String, ScriptOutputType, K[], V...)`、`del(K...)`、`get(K)`、
   `RedisClient.connect(RedisCodec)`、`RedisFuture.*`)確認屬實,並以真實 Redis 容器跑通。
3. `com.redis:testcontainers-redis` 2.2.4 由 Boot 4.1 BOM 納管,且其
   `AbstractRedisContainer` 繼承的 `org.testcontainers.containers.GenericContainer`
   在 Testcontainers 2.0.5 仍存在——§6.3.6 第 2 條的「Redis 例外」成立。

## 13. 被本 phase 的行為變更打到的既有測試

`AuthHardeningTest` 原本三個測試方法共用一個 client IP,總共送出 13 個 write 類別的請求;
維度 5 讓匿名的 write 上限變成 **12/min**(60 的 20%),第 13 個因此回 429。
改為每個方法一個 IP(本專案既有慣例),**不是**放寬配額——那個 429 是正確行為,
而且它同時證明了維度 5 真的生效。這個數字已寫進 `docs/deployment/rate-limiting.md` 的排錯表。

## 14. 第二個實例的設定必須以命令列參數傳入(判準曾經量錯對象)

`DistributedRateLimitTest` 用 `SpringApplicationBuilder.properties(...)` 給第二個實例設定,
其中包含 `server.port=0`。**那個 map 是 `SpringApplication.setDefaultProperties`,優先序最低**
——排在 `application.yml` 之後,於是 `${SERVER_PORT:8080}` 勝出,第二個實例綁在**固定的 8080**。

單獨跑不會發現:那時 8080 是空的,第二個實例真的起在 8080,測試量的是它。
但 `dod.sh mvp` 的 M1-38 是在 **mvp 容器已經佔用 8080** 的情況下執行 README 的建置指令,
於是 `get(secondPort)` 打到的是**容器裡的另一個 app**(記憶體後端、未被縮小的 ANONYMOUS 方案),
三個案例全紅且訊息完全看不出原因(`expected "3" but was "60"`)。

**處置**:`server.port` / `spring.sql.init.mode` / `spring.devtools.restart.enabled` 改以
`run("--server.port=0", ...)` 的命令列參數傳入(優先序高於 yml);只被 `${...}` 佔位符引用的
環境變數名(`POSTGRES_*`、`REDIS_*`、`RATE_LIMIT_BACKEND` 等)可以留在 defaultProperties。
另在 `@BeforeAll` 加兩道守衛——第二個實例的埠必須不為 0 且與第一個不同、
其 `ctip.rate-limit.backend` 必須真的是 `redis`——讓這一類「量錯對象」直接以看得懂的訊息失敗。

順帶修掉的是同一個成因:`spring.sql.init.mode=never` 一直被 `application-mvp.yml` 的
`mode: always` 蓋掉,第二個實例每次啟動都會重跑一次 `sample_data.sql`。
