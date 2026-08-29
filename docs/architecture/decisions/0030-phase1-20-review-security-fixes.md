# ADR 0030 — Phase 1–20 總複查:五項缺陷的處置

- **狀態**:已接受
- **日期**:2026-08-29
- **脈絡**:使用者指派的跨 phase 複查(Phase 1–20),要求「邏輯、規格與實作一致性、資安、程式弱點」
  四個面向,並要求修復後回寫規格。
- **相關**:[00-master §0.27](../../spec/00-master.md)

複查對象是 M1–M3 已完成的 20 個 phase(backend 主碼約 31.5k 行、833 個 Java 檔、frontend 138 檔)。
以下五項是**被判定為真實缺陷並已修復**的項目;其餘檢查過但未發現問題的區域列在最後。

---

## 1. CORS `allowedMethods` 漏了 PUT 與 PATCH(功能性缺陷)

`WebCorsConfig` 的清單停在 `GET/POST/DELETE`,而 Phase 18 加了兩支 `PUT`、Phase 20 加了一支
`PATCH`。前端是獨立來源的 SPA(nginx 只發靜態資產,不 proxy `/api`),因此這三支端點的
preflight 一律 403 ——**在瀏覽器端完全打不通**。

`MockMvc` 直接呼叫 handler,不走 preflight;`CorsPreflightTest` 又剛好只參數化了那三個方法,
所以整個測試套件全綠。

**處置**:清單補齊為 `GET, POST, PUT, PATCH, DELETE`;`CorsPreflightTest` 改為對
`PATCH /notifications/{id}/read` 實際發 preflight。規格([05 §5.7](../../spec/05-environment.md))
加註「新增端點時同步這份清單是硬性步驟」。

**為什麼不是「preflight 全放行」**:`allowedMethods("*")` 會讓這份清單失去作為契約的作用,
而這正是它存在的理由。

---

## 2. Webhook 送達是一個未設防的 SSRF 入口(安全性)

不變量 W1 只要求 `https://`。送達路徑是「伺服器主動對**租戶指定**的 URL 發 POST」,因此任何持
`webhook:manage` 的租戶都可以存進 `https://169.254.169.254/latest/meta-data/`、
`https://localhost:9200/_cluster/health` 或 `https://10.0.0.5:8080/admin`。回應本文雖被丟棄,
`webhook_deliveries` 仍記下狀態碼與延遲——那是一台可用的內網掃描器與雲端 metadata 取用管道。

**處置**:兩道防線,判定範圍共用同一組程式碼(避免兩邊漂移):

| # | 位置 | 判定對象 | 擋掉的是 |
|---|---|---|---|
| 1 | `WebhookTarget`(domain,純字串運算) | URL 字串 | 字面內網 IP、`localhost` / `*.internal`、URL 內嵌帳密、非 https |
| 2 | `WebhookTargetGuard`(送達前,會做 DNS) | `InetAddress` 解析結果 | 主機名解析到內網、**DNS rebinding** |

**決策 2a — 為什麼不能只做其中一道**:防線 1 擋不掉「`evil.example` 的 A 記錄就是 169.254.169.254」;
防線 2 在建立時做不了(建立當下解析通過,之後改 DNS 即可)。兩者擋的是不同的東西。

**決策 2b — 不用 `InetAddress` 自帶的述詞**:`isSiteLocalAddress()` 對 IPv6 只認已廢止的
`fec0::/10`,不認實際在用的 ULA `fc00::/7`;IPv4 也不含 CGNAT `100.64/10`。自己列範圍。

**決策 2c — `reconstitute` 只驗 scheme,不做完整檢查**:規則收緊之後,一列舊資料若讓聚合重建失敗,
整個租戶的送達扇出會一起停擺(`findAllActive` 是一次映射全部)。既存的違規目標由防線 2 擋下,
放寬重建不留缺口。安全性優先不等於「換一個更大的停擺面」。

**未做**:沒有為「已被防線 2 擋下」另立 `DeliveryStatus`;它記為一次普通失敗,理由字串
`target_not_publicly_routable`,因此 W3 的連續失敗計數會照常把這種 webhook 停用——那是想要的結果。

---

## 3. 登入鎖定期滿後計數不歸零 → 永久鎖定(安全性 / 可用性)

`User.recordFailedLogin` 只遞增,不在鎖定過期時歸零。因此 `failedLoginCount` 一旦到 10 就永遠是 10,
鎖定一過期,**任何一次**失敗都會立刻再鎖 15 分鐘。攻擊者每 15 分鐘送一個錯密碼即可讓受害帳號
永久登不進來,成本近乎為零。

U7 的規格文字是「**連續**失敗 10 次」,原實作等於「一生失敗 10 次」。

**處置**:記錄本次失敗之前,先檢查上一段鎖定是否已過期;過期即歸零重新起算。
規格 [10 §10.4](../../spec/10-identity-plans.md) 補明這一條。

---

## 4. 匯入端點的請求本文沒有容器層上限(阻斷服務)

`POST /api/v1/iocs/import` 以 `@RequestBody byte[]` 收檔:Spring 會先把**整包**讀進記憶體,
controller 的 64 MB 檢查在那之後才跑。而 Tomcat 對非表單本文沒有預設上限
(`max-http-form-post-size` 只管 `application/x-www-form-urlencoded`)。一個持 `ioc:import`
的帳號送一份數 GB 的本文就能把堆積吃光。

**處置**:新增 `RequestBodySizeLimitFilter`,排在 security chain 之前:

- 宣告了 `Content-Length` 的,看標頭直接回 413
- **沒有** `Content-Length` 的(chunked)由包裝過的 `ServletInputStream` 在讀滿上限的
  下一個位元組時中止 —— 只檢查標頭等於沒擋,chunked 才是攻擊者會用的那一種

**決策 4a — 不改端點簽章**:改成 `HttpServletRequest` 或 `InputStream` 會讓 springdoc 失去
requestBody schema,而 `api:check` 是前端型別的來源。filter 是唯一不動對外契約的位置。

**決策 4b — 端點層的檢查保留**:filter 未註冊時仍要有上限。兩處共用
`RequestBodySizeLimits.MAX_IMPORT_BYTES`,不各寫一份數字。

---

## 5. 限流的端點分類可被路徑編碼繞過(安全性,低)

`EndpointClassifier` 拿 `getRequestURI()` 的**原文**比對,而 Spring 的 `PathPattern` 是拿
*解碼後、去除路徑參數*的段落 routing 的。`/api/v1/iocs/%69mport` 與 `/api/v1/iocs/import;v=1`
會照樣打到 import handler,分類卻落到 `write` —— 上限從 `heavy` 的 5% 變成 20%,而那正是
最貴的三支端點。

**處置**:比對前正規化(逐段去路徑參數、逐段百分比解碼、去尾斜線)。
**解碼出來的 `/` 不得成為段落分隔符**(與 `RequestPath` 一致),否則 `%2F` 就能拼出任意分類;
壞掉的百分比序列原樣回傳而非拋例外——那條路徑未認證即可觸發,拋例外就成了 500。

---

## 檢查過但未發現問題的區域

逐檔讀過、未修改:JWT 簽發與驗證(無 alg confusion:`MACVerifier` 只接受 HS\*,`none` 在
`SignedJWT.parse` 就失敗)、API key 雜湊比對(常數時間)、refresh token 輪替與重用偵測、
RBAC 權限矩陣與端點授權宣告(24 個 authority 全部有對應種子,無端點漏標 `@PreAuthorize`)、
TLP/再散布可見度的兩份實作(`TlpSpecifications` 與 `SearchVisibilityQuery`)、
SQL 全部參數化(無字串拼接)、`LIKE` 與 ES wildcard 的跳脫、Bloom 位元序與 delta 編碼、
cursor 分頁精度、Kafka 轉發的非阻塞與有界佇列、前端(無 `dangerouslySetInnerHTML`,
token 只在記憶體)。

一個**排除掉的疑似缺陷**記在這裡,免得下一輪複查再查一次:`UserTest` 的外層 `@Test` 方法在
surefire 報表上顯示 `Tests run: 0`,看起來像從未執行。實測(故意讓其中一個失敗)證明它們**有**執行,
只是被 surefire 歸到第一個 `@Nested` 類別的報表裡。這是報表歸屬問題,不是覆蓋率缺口。
