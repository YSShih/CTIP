# CTIP API 文件

| 檔案 | 內容 |
|---|---|
| [`openapi.json`](openapi.json) | **對外契約的單一來源**。由建置產生(`OpenApiCompletenessTest`)並進版控;CI 比對 drift 與破壞性變更,**不得手改** |
| [`sync-client-contract.md`](sync-client-contract.md) | Bloom 同步的 client 契約(六條強制規則、位元格式、`addedBits` 編碼、同步流程) |
| [`events/`](events/README.md) | Domain event 的版本化 schema 與 domain event → topic 對照表(Phase 20;13 §13.1 明文要求) |
| [`webhooks.md`](webhooks.md) | Webhook 接收端契約(五個送達標頭、簽章與 5 分鐘時鐘偏差、重試與停用;13 §13.2 明文要求寫入本目錄) |

互動式文件:`/swagger-ui/index.html`(`mvp` / `dev` / `staging` 開啟;`prod` 預設關閉)。
規格本體在 [`docs/spec/09-api.md`](../spec/09-api.md)。

---

## 公開情資的誤判申訴

`POST /api/v1/iocs/{id}/report-false-positive` **只接受呼叫者租戶自己擁有的 Indicator**;
對公開情資(`owner_tenant_id` = public tenant)一律回 `403 FORBIDDEN`。

理由見 [`09-api.md` §9.7](../spec/09-api.md)「誤判回報的作用域」:全平台只有一列 `MANUAL` 來源,
對一筆公開 Indicator 建立誤判列改到的是**共用的公開資料**,而且第二個租戶回報同一筆時會直接
撞上 `UNIQUE (indicator_id, source_id)`。

**對公開情資的誤判請走人工申訴**,不是 API 操作(M1–M3 不提供此流程的端點):

- 收件:平台營運方(部署本平台的組織自行填入聯絡窗口;範例部署為 `security@example.invalid`)
- 請附:IOC 值與型別、`indicator` 的 id 或 STIX id、判定為誤判的理由與證據(例如該端點屬於
  合法 CDN / SaaS 的共用基礎設施)、你的聯絡方式
- 處理:營運方複核後在來源層調整或標記,下一份 full snapshot 起生效
  (Bloom 無法移除成員,見 [`sync-client-contract.md`](sync-client-contract.md) 第 3 條)

自己租戶提交的 IOC 不需走申訴:直接呼叫該端點,最終狀態由
`IndicatorMergePolicy.determineStatus` 決定(不由呼叫端指定)。

---

*上次校對:2026-08-28(Phase 16)。*
