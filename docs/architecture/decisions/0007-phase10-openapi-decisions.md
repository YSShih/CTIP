# ADR 0007 — Phase 10 OpenAPI / Swagger 實作決策

- 狀態:accepted(2026-08-26,Phase 10)

## 1. `docs/api/openapi.json` 由 `OpenApiCompletenessTest` 產生

§9.6 要求產出 commit 進 repo 且不得手改。版本表(06 §6.2)沒有 springdoc-openapi-maven-plugin
(規則 6 不得自行加版本表外的建置相依),故由判準測試本身在驗證完 `/v3/api-docs` 後寫出
canonical 形式(鍵排序 + pretty print + 結尾換行),跨執行位元一致。CI 以
`git diff --exit-code` 擋 drift(漏 commit 或手改皆會 fail)。

## 2. 破壞性變更比對用自寫 `openapi-breaking-check.py`

版本表無 oasdiff 等工具;§9.6 點名的三類破壞(移除端點、移除必填欄位、變更型別)以
標準庫 python 腳本實作(`environment/scripts/openapi-breaking-check.py`,dod.sh 已依賴 python3)。
比對基準:PR 用 base branch 的 committed 版本、push 用 HEAD~1。

## 3. OpenAPI 註解集中於文件介面(controller implements)

每端點的 summary/description/schemas/錯誤/範例註解量大,直接放 controller 會超過
checkstyle 300 行限制且淹沒業務邏輯。springdoc 支援從實作介面繼承註解,故建立
`interfaces/rest/openapi/{System,Ioc,Stats,Source,Stix}Api` 文件介面;MVC 註解仍在 controller。

## 4. 認證需求的表達(M1 無 security scheme)

M1 只有匿名身分,無可宣告的 OpenAPI securityScheme。雙軌表達:每個 operation 標
`@SecurityRequirements`(空,顯式「無需認證」),且 description 一律含「認證:…」字句;
`OpenApiCompletenessTest` 以後者為機器可驗證的檢查點。Phase 13 加入 JWT/API key scheme 後
改為實際 security requirement。

## 5. 429/500 錯誤文件由 OperationCustomizer 統一掛上

所有端點皆受匿名限流且可能 500;逐端點手寫必然漏。`OpenApiConfig.globalErrorResponses()`
對每個 operation 自動加 429/500(引用 ErrorResponse schema),個別端點只標自己特有的
400/403/404/413/415。

## 6. `up.sh` 預熱守衛修正(環境缺陷,已回寫 05 §5.10)

原守衛只認 maven-cache volume「為空」,偵測不到 pom 相依漂移——Phase 10 判準的
`up.sh mvp` 實測失敗(Phase 5 起新增的 resilience4j/springdoc 全數缺件,離線容器直接退出)。
改為離線 `dependency:go-offline` 探測:失敗(首次或相依變更)即以
`dependency:go-offline package` 重新預熱(go-offline 同時快取 dependency plugin 自身,
使後續探測可離線快速通過)。已端到端實測:偵測 → 重預熱 → 三容器 healthy → swagger 判準過。
