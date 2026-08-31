# CTIP 專案沿革

> [README](../README.md) 說的是「現在是什麼」；本檔說的是「怎麼走到這裡」。
>
> - 逐 phase 的完成判準結果、偏離事項與交接注意事項 → [`progress.md`](progress.md)
> - 每個決策的完整理由與取捨 → [`architecture/decisions/`](architecture/decisions/)
> - 規格本身的修訂索引 → [`spec/00-master.md`](spec/00-master.md) §0.7–§0.32

---

## 1. 規格書怎麼來的

```text
GPT 產生初稿（v1.0）
  → Claude 第一輪調整（v1.1，3,038 行單檔）
  → Claude 逐行審查、24 輪設計決策訪談、對 Maven Central / npm / OASIS 等外部來源查證（v2.0，拆成 16 個主題檔 + 23 份執行單）
```

v1.1 → v2.0 的實質改動：修正 **4 項建置阻斷缺陷**、**3 項版本錯誤**（含兩個已 EOL／已退役的元件）、
**10 項規格內部衝突**（其中 2 項會導致編譯失敗）、補完 **19 張缺失的表定義**、
新增 DDD 章與關聯圖章、把 DoD 從散文改為 90 項可執行檢查。逐項見
[00 §0.6](spec/00-master.md)。

v2.0 之後又有 **二十六輪實作回饋修訂**（§0.7–§0.32）——每一輪都是實作時撞到的規格衝突或缺口，
修正一律**寫回規格正文**，規格因此始終是 single source of truth。

---

## 2. 三個里程碑

### M1 — MVP（Phase 1–12，2026-08-21 ～ 08-26）

可 demo 的最小可用產品：匿名唯讀的公開情資平台。

| 區塊 | 交付 |
|---|---|
| `environment/` | 唯一 compose 檔 + 四 profile、雙 Dockerfile、四環境樣板、9 支腳本（含 90 項 DoD gate 的 `dod.sh`）、CI compose 驗證 |
| `backend/` | 四模組骨架與 Spring Boot 4 啟動、Flyway V1–V7 + 1,020 筆種子、Indicator/Tenant/Source 聚合與最小安全層（tenant + TLP + 再散布**統一過濾**）、SDK + 三個確定性 mock adapter + Resilience4j、10-stage ingestion pipeline（正規化、八種拒絕規則、去重合併、評分、STIX 投影）、排程與記憶體限流、STIX 2.1 匯出、匿名讀取 REST API 全套（cursor 分頁、16 錯誤碼統一結構 + traceId）、OpenAPI/Swagger（committed `openapi.json` + CI drift／破壞性檢查）、PostgreSQL 全欄位搜尋（tags GIN `@>`、pg_trgm 子字串）（**262 tests**） |
| `frontend/` | React 19 + Vite 8 + Tailwind v4、OpenAPI 型別產生鏈、Redux Toolkit + TanStack Query（狀態歸屬依規格：server 資料進 Query、搜尋條件進 URL）、shadcn 風格元件 + 四態 StateViews + TlpBadge + 虛擬化表格、IOC 檢索／詳情與公開統計儀表板、深色模式、MSW 型別驅動測試（**70 tests**） |

**M1 總複查**（Phase 13 前，四個獨立視角，[ADR 0011](architecture/decisions/0011-m1-review-fixes.md)）另修 8 項，
其中三項照原樣上線會出事：同來源 UPSERT 無條件把來源記錄設回 `ACTIVE`（全量重同步會沖掉撤回）、
cursor 截到毫秒使微秒級 `last_seen` 翻頁漏列、`/actuator` 限流豁免可被 `..` 前綴繞過。

---

### M2 — Platform（Phase 13–19，2026-08-27 ～ 08-29）

從「公開唯讀」變成「多租戶、有身分、有配額、可增量同步」的平台。

#### Phase 13 — 認證、RBAC、API Key、租戶隔離

Flyway V20/V21/V24、User／ApiKey 聚合（U1–U7、K1–K7 逐條測試）、JWT HS256 + refresh token 輪替與
重用偵測（family 全撤）、BCrypt cost 12 與登入鎖定、API key（原文僅回一次、前綴定位、scope 不可提權）、
Spring Security filter chain + `@PreAuthorize` + 集中 PermissionEvaluator、跨租戶一律 404。

**收尾稽核**（逐端點對照 §10.3 矩陣 + 架構／資安複查，[ADR 0013](architecture/decisions/0013-phase13-audit-fixes.md)）
再補 12 項，其中兩項是實質漏洞：

- `/sources`／`/stats` 五個端點**完全沒有授權宣告**，而 filter chain 是 `permitAll` —— 等於全開。
  新增 `source:read`／`stats:read`（權限 19 → 21、矩陣 95 → 105 格），並以 `EndpointAuthorizationTest` 逐 handler 守門
- **停權與移除成員資格對既有憑證完全無效** —— refresh 輪替與 API key 驗證都不看 `UserStatus`。
  `AccountAccessPolicy` 成為單一判定點，規則統一 fail-closed

另**先行清掉後續 phase 的已知缺口**（[ADR 0015](architecture/decisions/0015-future-phase-hardening.md)）：
`/stats/sources` 補可見度過濾、`sourceId` 查詢參數的來源歸屬 oracle（兩者都要等 Phase 14 手動提交上線才會真正洩漏）、
限流 bucket 逐出、STIX name 截斷不切 surrogate pair、filter 逸出例外也回統一錯誤結構。（**537 tests**）

前端：登入／註冊、API Key 管理（原文一次性顯示）、`RequireAuth`／`RequirePermission`、
401 自動輪替（並行請求共用單次輪替）。（**97 tests**）

#### Phase 14 — Plan、Subscription、配額 + IOC 寫入端點

Flyway V28/V29、`Subscription` 聚合（B1–B5）與 `QuotaService` 單一判定點——§10.6 的
**14 個配額維度全部讀 `plans` 表**，property 版本連同五個環境變數一併移除，避免第二真相來源。
§9.7 的三種超限語意各有出口（429 時間窗／403 能力上限／413 單次尺寸／分頁夾值不報錯）。

**IOC 寫入端點**：`POST /iocs`（走完整 pipeline，預設 TLP:AMBER、歸屬不可指定、
`ioc:publish` = 擁有權轉移且來源記錄轉為可再散布，否則發布沒有任何公開效果）、
`POST /iocs/import`（CSV／STIX bundle，202 + jobId 非同步）、`GET /iocs/import/{jobId}`、
`POST /iocs/{id}/report-false-positive`、`GET /subscription`／`/subscription/usage`。
`StixPatternParser` 是 §7.8.3 六個模板的反向——本平台匯出的 bundle 可再匯入。
（[ADR 0023](architecture/decisions/0023-phase14-plans-and-write-endpoints.md)，**644 tests**）

#### Phase 15 — 兩層 Bloom Filter

public（`TLP:CLEAR`、可再散布）與 per-tenant（`AMBER`／`AMBER_STRICT`，**刻意不含再散布條件**——
否則私有提交固定 `INTERNAL_ONLY` 會使 tenant bloom 恆為空）。

位元格式逐條依 §11.4 自行實作（LSB-first、Kirsch-Mitzenmacher 雙雜湊、`h1 + i*h2` 以 unsigned 64-bit
wraparound 計算、`m` 向上取整至 8 的倍數、k 由公式導出 = 10）。`BloomBitLayoutTest` 以固定 fingerprint
斷言**確切的 byte 陣列**而不是「有沒有命中」——§11.4 存在的理由就是 client 要能產生位元組完全相同的陣列。

`BloomUpdateStage` 只作為「哪個 scope 變了」的訊號，成員真相在資料庫——記憶體緩衝遺失會產生
Bloom **false negative**。`BloomArrayLoader` 在生成 delta 前先跑一次 client 的 §11.6 驗證路徑，
否則損壞的 artifact 會讓每個 client 套用後都失敗，而伺服器端毫無徵兆。

定調三處規格自相矛盾之處（皆採安全優先），見
[ADR 0024](architecture/decisions/0024-phase15-bloom-decisions.md)。（**705 tests**）

#### Phase 16 — 增量同步 API 與 client 契約

`GET /sync/manifest`（`coverage` 與 `notCovered` 為必填：client 開發者必須在 manifest 就看到
「public 只覆蓋 `TLP:CLEAR`、`TLP:GREEN` 完全無覆蓋」）、`GET /sync/bloom?scope=`、
`GET /sync/delta?base=&scope=`（LEB128 varint + base64url，含 `409 SNAPSHOT_REQUIRED`）。

三處規格陷阱定調：manifest 的 `checksum` 照字面取「最新版本 artifact 的 checksum」時，
最新版本是 delta 就會算成 varint payload 的雜湊，**client 拿它驗自己的陣列永遠不會相符**；
§11.6 第 4 步沒說版本要更新成哪個數字，照 manifest 記會產生 Bloom **false negative**；
`409` **不消耗**同步間隔，否則 client 轉去下載 full 時立刻撞 `429`，整條復原路徑走不完。

並修掉 **M2-15 的假綠**（判準原本跑生成端的測試，而 `409` 的 HTTP 行為當時根本不存在）。
（[ADR 0025](architecture/decisions/0025-phase16-sync-api-decisions.md)，**727 tests**）

前端：Bloom 同步說明頁 `/sync`（明文說明**命中不代表確定惡意、未命中不代表安全**）、
**Playwright E2E 骨架**（webServer 跑 `build && preview`，測的是使用者實際拿到的 bundle）。（**121 tests + 3 E2E**）

#### Phase 17 — Redis 快取 + 分散式限流

`CachePort`／`RedisCacheAdapter`（只用 `GET`／`SET EX`／`DEL`，換 Valkey 只需改 image 名稱）與
`RedisRateLimiter`（Bucket4j）。**五個限流維度**分成**兩個檢查點**：維度 4（匿名 IP）在認證**之前**
（否則無效憑證完全繞過限流），維度 1–3／5 在認證**之後**。

四處照字面實作會出事的地方：維度 5 的鍵在 §10.7 **沒有主體**（照字面是全平台共用一個桶，
任一租戶打滿它所有人都被 429）、維度 4 對已認證請求**先扣後退**（不歸還的話 ENTERPRISE 的 client
會被匿名方案的 60/min 綁死）、bucket4j 建立後不隨限額更新（方案**降級**時 fail-open）、
Boot 的 `forward-headers-strategy=framework` **無條件採信** `X-Forwarded-*`（改為只信任 `TRUSTED_PROXIES`）。

Redis 不可用時限流 **fail-fast**（不得降級記憶體——「後端掛了就等於沒有限流」正是攻擊者要的狀態），
快取則 fail-soft。`DistributedRateLimitTest` 真的起**兩個 Spring context**驗證跨實例耗盡。
（[ADR 0026](architecture/decisions/0026-phase17-redis-cache-and-distributed-rate-limit.md)、
[`deployment/rate-limiting.md`](deployment/rate-limiting.md)，**757 tests**）

#### Phase 18 — Threat 實體與 M2 的 STIX 物件

Flyway `V31`、`Threat` 聚合（H1–H5 在聚合與 DB 約束強制；**H6 由 application 層強制**，
並由新的 `IndicatorTlpTightened` 事件維持事後一致性）、五種 M2 STIX 投影。

**本 phase 補上 Threat 的建立管道**：§9.1 原本只有三個 `GET`，而 ingestion 不產生 Threat——
照原樣實作，三張表與聚合的四個行為在正式環境永遠不可達（規則 16 的 placeholder）。
新增五個寫入端點與 `threat:manage` 權限；`POST /{id}/retire` 改為 `PUT /{id}/status`，
否則 `ThreatStatus.DORMANT` 同樣永不可達。

實測抓到：**AFTER_COMMIT 的事件消費端用預設傳播行為寫資料庫，寫入不落庫也不報錯** → 一律 `REQUIRES_NEW`；
`GET /{id}/indicators` 必須對每個關聯 IOC **再走一次**可見度（關聯不是可見度的旁路）。
（[ADR 0027](architecture/decisions/0027-phase18-threat-and-m2-stix.md)，**815 tests**）

前端：威脅情報頁與詳情頁。關聯清單比 `indicatorCount` 短時**明說差額**——
那是 TLP 或再散布政策擋掉的，靜默留白會讓使用者以為情資不見了。（**131 tests + 3 E2E**）

#### Phase 19 — Elasticsearch 搜尋 + 降級 + 對帳（M2 收官）

`ElasticsearchSearchAdapter`（`dynamic: strict` mapping、`wildcard` 子字串與模糊查詢）、
`FallbackSearchAdapter`（circuit breaker，ES 不可用時回 **200** + `X-Search-Backend: postgres`）、
`SearchIndexStage`、每日對帳排程。

**兩處實測缺陷**：

1. §13.7 的搜尋欄位清單**不含** `ownerTenantId`、`deletedAt` 與再散布政策，而那三者是可見度與
   側信道防護的全部依據——照字面實作，ES 路徑會整套繞過過濾。索引因此另帶三個欄位，
   **且回傳的 Indicator 一律以 `findVisibleByIds` 從 PostgreSQL 取回**；兩層防護各以測試反向驗證
2. **`spring-boot-elasticsearch` 一在 classpath 上就會加 actuator 的 ES 健康檢查**，
   而 ES 只屬 `full` profile——mvp 與 dev 不關掉的話容器永遠 unhealthy

另修掉 **M2-22 的假綠**：它是 DoD 全表唯一用 `verify` 的過濾式判準，因此繞過 `dod.sh` 的
測試類存在性守衛，測試不存在時 build 成功、該項照樣 `[PASS]`。
（[ADR 0028](architecture/decisions/0028-phase19-elasticsearch-search.md)，**831 tests**）

---

### M3 — Production（Phase 20–23，2026-08-29 ～ 08-30）

#### Phase 20 — Kafka + 通知（WebSocket／SSE／Webhook）

Kafka（KRaft）與六個 topic、`KafkaEventForwarder`（**不修改任何發佈端**，只是又一個消費端）、
事件的版本化 JSON Schema（[`api/events/`](api/events/README.md)）、Flyway `V32`、`Webhook` 聚合（W1–W6）、
HMAC-SHA256 送達簽章、指數退避重試與連續五次後停用、原生 WebSocket 與 SSE fallback。

**四處照字面實作會出事的地方**：`WebhookFilter` 要的 severity／tags／sourceIds **不在 domain event 上**
（它們是多來源合併之後才定的）→ 新增 `NotificationEvent` 投影；`KafkaTemplate.send()` 取不到 metadata 時
**同步阻塞 60 秒**——回 200 卻等一分鐘與失敗沒有差別；`KafkaAdmin` **看不見 `List<NewTopic>` 型別的 bean**，
關閉 auto-create 的正式環境會直接沒有 topic；SSE fallback 原本**沒有方案閘門**，
任何 client 改連 `/events` 就繞過 `websocket_enabled`。
（[ADR 0029](architecture/decisions/0029-phase20-kafka-and-notifications.md)，**931 tests**）

前端：通知中心與 webhook 管理頁。即時推送**指數退避 + 抖動**自動重連（沒有抖動的話伺服器重啟時
所有 client 會在同一毫秒一起重連），連線狀態指示器**誠實**說出「連線中斷，重試中」；
推播只是「有新東西了」的訊號，清單仍以 Query 為真相來源，漏掉的推播會在下一次 refetch 補上。

#### Phase 21 — 稽核軌跡 + 資料保留

Flyway `V33`（`REVOKE UPDATE, DELETE` 使**應用角色連 DB 層都刪不掉稽核**）、
非同步有界佇列的 `AuditWriter`（稽核寫入失敗不得使業務操作失敗——結構上不可能：
業務服務根本不知道稽核存在）、**26 種稽核行為**由兩個橫切消費端承接、讀取取樣、
`GET /audit-logs`、**六項保留清理**、`POST /auth/change-password`、`/admin/**` 七支管理端點。

**四處照字面實作會出事的地方**：§13.5 規則 2 說清理角色「無 SELECT 業務表之權限」，
而 **PostgreSQL 對 `DELETE/UPDATE … WHERE` 仍要求 WHERE 欄位的 SELECT 權限**——
照字面授權六項清理全部 `permission denied`；`SUBSCRIPTION_CHANGED` 是強制的 26 種行為之一，
但沒有任何端點呼叫 `Subscription.changePlan`／`cancel`；§13.4 的資料主體刪除與 §13.5 的
append-only 直接衝突（→ 刪除涵蓋可識別欄位，稽核以 180 天保留期收斂，法律基礎寫入
[`deployment/privacy.md`](deployment/privacy.md)）；表 27 沒有 `action` 的 CHECK。

`AuditCompletenessTest` 真的把 26 條路徑各走一遍再問資料庫留下了哪些 `action`——
比對程式碼裡出現過哪些列舉值，對「有程式碼但永遠不會被呼叫」完全無感。
（[ADR 0031](architecture/decisions/0031-phase21-audit-and-retention.md)，**1,055 tests**）

前端：稽核軌跡頁（**只讀**——軌跡是 append-only 的）與平台管理頁；
資料主體刪除的回應**明說仍保留幾列稽核紀錄**，否則操作者會以為「刪除」把一切都刪了。

#### Phase 22 — 監控／日誌／追蹤

Actuator + Micrometer + Prometheus registry（含**每個 ingestion stage 一支計時器**；
六個 `ctip.*` 指標在啟動時就註冊，因為 Prometheus 的「序列不存在」與「值為 0」在告警規則上是兩件事）、
Grafana dashboard 九張圖、結構化 JSON 日誌與**兩道憑證防線**、OpenTelemetry 追蹤、
`/actuator/prometheus` 的來源 IP 白名單。

**四處照字面實作會失敗的地方**：關掉 `management.tracing.export.enabled` **會連「接收傳入的
`traceparent`」一起關掉**（改為只關 `…export.otlp.enabled`）；追蹤切面以整個套件當切入點時，
套件內的 `final` 類別會使 CGLIB 建不出代理、**整個 context 起不來**；
Prometheus 的 **exemplar** 會在記錄指標的執行緒上向 bean factory 要 `Tracer`，
而 Lettuce 的命令延遲是在 netty event loop 上記錄的——`RATE_LIMIT_BACKEND=redis` 的環境
**卡在啟動且沒有任何錯誤訊息**（是 thread dump 才看出來的）；
`logback-spring.xml` 若讀必填佔位符 `ctip.environment`，日誌系統在 environment-prepared 階段就初始化。
（[ADR 0032](architecture/decisions/0032-phase22-observability.md)）

#### Phase 23 — CI/CD 完整化、安全掃描、文件

11 支 workflow、`dependabot.yml`、Gitleaks／Trivy（**釘 commit SHA**）、兩份 SBOM、
`ctip-sdk` 的可編譯範例 adapter（放**測試原始碼**並沿用既有 `SourceType`——
為一份範例在列舉新增成員會留下永不可達的值）、STIX Viewer（Cytoscape.js，唯一 code-split 的路由）、
12 份必要文件。

其中**六支標 M1/M2 的 workflow 逾期了十個 phase**——`dod.sh` 當時沒有任何一項檢查 workflow
檔案是否存在，M3-19 只看最後一次 run 的結論，「只有兩支且都綠」照樣通過
（[ADR 0022](architecture/decisions/0022-orphan-deliverables.md)）。M3-19 已就地擴充為
「11 支檔案存在 → `deploy-prod` 綁定 protected environment → CI 全綠」。

**Phase 23 補件**：兩項標 `[M2]` 卻不在任何 phase 交付物清單裡、因此連續三次只被回報的遺漏
（[ADR 0042](architecture/decisions/0042-m2-gaps-token-cleanup-and-settings.md)）——
`TOKEN_CLEANUP_CRON` 的過期 token 清理（**標記不刪除**：刪列等於偷偷新增第七項保留政策），
與 `/settings` 頁（`POST /auth/change-password` 在 Phase 21 就交付了，卻**沒有任何前端入口**）。

---

## 3. 三個 DoD 閘門

| 閘門 | 項數 | 結果 |
|---|---|---|
| `dod.sh mvp` | 38 | ✅ 38/38 |
| `dod.sh phase2` | 27 | ✅ 27/27 |
| `dod.sh full` | 25 | ✅ **25/25**（2026-08-31 完整實跑）。首次實跑為 23/25，失敗的 M3-01 與 M3-19 其後皆已修復並重新驗證 |

**M3-01**（巢狀 gate 回歸）—— `dod.sh mvp` 37/38，掛在 M1-37（後端 reload）。成因是 Phase 22
換 plain log pattern 時掉了 `%thread`，判準要找的 `restartedMain` 是**執行緒名**、永遠對不到。
（[ADR 0043](architecture/decisions/0043-gate-run-findings.md)）

**M3-19**（CI 全綠）—— 兩個獨立問題：

1. `openapi-check` 自上線起 **29 次 run 0 次成功**：用了 `verify -Dtest=<類名>`，而 `verify` 綁 JaCoCo
   `check`，只跑一個測試類時覆蓋率 0.18 < 門檻 0.60。改用 `test` 後在 CI 實測轉綠
2. `security` **沒有壞**，是真的掃到四組 HIGH。三個 CVE 全落在 Boot BOM 納管的傳遞相依上，
   由 `spring-boot-starter-parent` 4.1.0 → 4.1.1 一次解掉；剩兩組在基底映像
   （`eclipse-temurin` 的 Go stdlib、`nginx:1.30-alpine` 的 OpenSSL），**本 repo 無動作可做**，
   上游重建映像即消失。（[ADR 0044](architecture/decisions/0044-security-findings-remediation.md)）

**全專案複查**（2026-08-30，[ADR 0045](architecture/decisions/0045-full-project-review-doc-sync.md)）：
程式端無偏離，問題全部集中在「規格宣告了自動化，而那個自動化不存在」——
§3.3 的 ERD 標著「規範·自動驗證」，但那個比對從來沒有，
於是 Phase 14 新增的 `import_jobs` 漏登了九個 phase 沒被發現。已補 `DataDictionaryConsistencyTest`。

---

## 4. 值得記住的幾個坑

這些是實測撞出來、且**照規格字面實作一定會踩**的，完整清單見
[00 §0.7–§0.32](spec/00-master.md) 與各 ADR。

| 坑 | 症狀 |
|---|---|
| Flyway 依**版本號**排序套用，而 §4.7 原本依「表的分組」預留區段 | Phase 14/15/18 在既有資料庫上直接 `FlywayValidateException` 啟動失敗（[ADR 0014](architecture/decisions/0014-flyway-monotonic-versions.md)） |
| `@Transactional` 方法內「寫入失敗紀錄 → 丟例外」 | 該寫入隨交易 rollback ——登入失敗計數與 token 重用偵測的 family 全撤**完全失效**（[ADR 0012](architecture/decisions/0012-phase13-auth-rbac-decisions.md)） |
| JaCoCo 的 `jacoco.exec` 預設 **append** | 本機不 `clean` 就重現不出 CI 的覆蓋率失敗，會得到一個看起來像「修好了」的假綠 |
| `docker compose up` 只在 image 不存在時才建置 | tag 一旦存在，程式改了也不會重建；六個 Kafka topic 一個都沒建立，而所有 healthcheck 都是綠的 |
| compose **刻意不把 profile 停用的服務視為 orphan** | 先跑 `staging` 再跑 `mvp` 會留下五個容器，M1-14「只有三個容器」因此不可能通過 |
| DevTools「classpath 變更即重啟」+ host/container 共享 `target/classes` | host 建置使容器 app 死於半寫入的 classpath（[ADR 0010](architecture/decisions/0010-devtools-trigger-file.md)） |
| 本機 `docker run aquasec/trivy fs` 會連 Maven Central 解析 pom | 撞 429（Retry-After: 1800，且會封鎖該 IP）；要在本機重驗必須把 `~/.m2` 掛進容器 |

---

## 5. 這個專案是怎麼做的

23 個 phase、一次一個 session、一個 phase 一個 commit。每個 session 的開場協議、硬性規則與
收尾程序見 [`CLAUDE.md`](../CLAUDE.md)；每一輪的判準結果與交接事項見 [`progress.md`](progress.md)。

規格與實作的關係是雙向的：實作撞到的每一個規格衝突都**寫回規格正文**（二十六輪），
而不是在程式裡繞過去——所以規格到最後仍然是那份「照著做就能重建這個系統」的文件。
