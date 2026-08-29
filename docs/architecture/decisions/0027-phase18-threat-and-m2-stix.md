# ADR 0027 — Phase 18:Threat 實體與關聯 + M2 的 STIX 物件

- **狀態**:accepted
- **日期**:2026-08-29
- **範圍**:`02 §2.3/§2.4`、`04` 表 8/§4.7、`07 §7.7/§7.8.6/§7.8.7`、`09 §9.1`、`10 §10.3`
- **背景**:phase-18 執行單 + [ADR 0020](0020-phase17-19-spec-resolutions.md) 第 4–7 節的定調

---

## 1. 平台沒有任何建立 Threat 的管道(最重大的一項)

`09 §9.1` 的 Threat 只有三個 `GET`;ingestion pipeline 不產生 Threat(`RawThreatRecord`
沒有任何威脅欄位);Phase 19–23 的執行單也沒有。照執行單字面實作,結果是:

- `threats` / `threat_indicators` / `threat_external_references` 三張表永遠是空的
- `Threat.linkIndicator` / `unlinkIndicator` / `addExternalReference` / `retire` 永遠不可達
- `07 §7.8.1` 要求的 `malware` / `attack-pattern` / `relationship` 三種投影永遠產不出實例
- `threat:read` 這個權限沒有任何資料可讀

這正是規則 16 禁止的 placeholder,而且是整個 phase 規模的。v2.0 自己有前例:
「v1.1 的三十個端點裡沒有任何寫入 IOC 的方式,導致三個懸空定義」——處置就是補寫入端點。

**決定(使用者裁示):補最小寫入端點。**

```text
POST   /api/v1/threats                             threat:manage
PUT    /api/v1/threats/{id}/indicators/{iocId}     threat:manage
DELETE /api/v1/threats/{id}/indicators/{iocId}     threat:manage
POST   /api/v1/threats/{id}/external-references    threat:manage
PUT    /api/v1/threats/{id}/status                 threat:manage
```

- 新增權限 `threat:manage`(第 23 個),歸屬 `TENANT_ADMIN` / `SYSTEM_ADMIN`:
  把 IOC 歸因到 campaign / malware family 是租戶層級的情資策展,不是一般使用者的自助操作。
- 歸屬與 TLP **完全沿用 §9.7 手動提交的規則**:請求不得指定 `ownerTenantId`;
  預設 `AMBER`(私有);`CLEAR`/`GREEN` 需 `ioc:publish` 且擁有者轉為 public tenant。
  不共用這條規則的話,「owner = 某租戶、tlp = CLEAR」的威脅是一個**誰都看不到**的東西
  ——與 [ADR 0019](0019-phase14-16-spec-resolutions.md) 第 2 節要消滅的缺陷同源。
- 可寫入範圍:自家租戶的 Threat,或 public tenant 的 Threat 但持有 `ioc:publish`。

## 2. `POST /{id}/retire` 會讓 `ThreatStatus.DORMANT` 永遠不可達

`02 §2.3` 的行為清單只有 `retire()`,但 `04 §4.5` 的 `ThreatStatus` 有三個成員。
只做 retire,`DORMANT` 就是一個永不可達的列舉值(同樣是規則 16)。

**決定**:端點為 `PUT /{id}/status`,domain 補 `changeStatus(ThreatStatus)`,
`retire()` 保留為 §2.3 明列的行為,委派到終態。`RETIRED` 是**終態**:退役後不接受任何變更
(要復活就建立新的 Threat),設定成它已經是的狀態一律 409(不假成功)。

順帶:`OpenApiCompletenessTest` 要求每個 `POST` 都有 request schema——沒有 body 的
`POST /{id}/retire` 會直接違反它,`PUT /{id}/status` 天然帶 body。

## 3. `/threats` 三端點的可見度述詞

執行單明列「未定義」。定調為 §7.7 的通則(`owner IN (viewer, public)` + public 分支的 TLP 上限),
與 `TlpSpecifications` 的租戶／TLP 段完全相同,兩者不得各寫一套。兩點差異是資料模型的事實:

- **沒有軟刪除**:表 19 沒有 `deleted_at`,退役以 `status` 表達,清單端點預設排除 `RETIRED`。
- **沒有再散布維度**:§7.9 規則 3 的條件是「所有**來源記錄**皆 `INTERNAL_ONLY`」,
  而 threats 沒有 `indicator_sources` 這種東西。

**`GET /threats/{id}/indicators` 必須對每個關聯 IOC 再走一次 Indicator 的可見度**——
關聯不是可見度的旁路。整合測試以再散布這條軸驗證(TLP 那條軸被 H6 蓋住了)。

## 4. H6 的兩個執行點,以及 AFTER_COMMIT 的交易陷阱

ADR 0020 第 5 節把 H6 降格為應用層一致性規則,並要求 `IndicatorTlpTightened` 事件觸發
Threat 的重新收緊。§2.4 原本沒有這個事件,本 phase 補上(欄位:indicatorId、tenantId、
previousTlp、currentTlp),由 `Indicator.recompute()` 在 TLP 真的變嚴格時發佈。

**實測抓到的缺陷**:事件由 `SpringEventPublisherAdapter` 於 AFTER_COMMIT 發佈,
而那個回呼**仍在已提交交易的 synchronization 範圍內**——EntityManager 還綁在執行緒上,
但交易已經結束。消費端若用預設的 `REQUIRED` 傳播行為寫資料庫,寫入會**不落庫也不報錯**
(第一次實作就是這樣:`malware` 與 `relationship` 一列都沒有,連例外都沒有)。
`ThreatStixProjectionService.project` 與 `ThreatService.retightenForIndicator` 因此都必須是
`REQUIRES_NEW`。這條寫進 `02 §2.4` 的規則清單——它對 M3 的 Kafka 轉發 listener 同樣成立。

## 5. M2 的五種 STIX 物件:欄位對照補進 §7.8.7

ADR 0020 第 7 節指定「欄位級對照由 Phase 18 依 §7.8.2 的體例補寫進 §7.8」。本 phase 補上
`malware`、`attack-pattern`、`observed-data`、`identity`、`relationship` 五張對照表。
實作時撞到的三件事:

1. `malware` 的 `aliases` 有 `minItems: 1`——**空集合必須整個省略**,給空陣列會驗證失敗。
2. `attack-pattern` 的 schema **沒有** `is_family` / `first_seen` / `last_seen`,不得硬塞。
3. `observed-data` 的 schema 要求 `objects` 或 `object_refs` 至少有一個。平台不獨立持久化 SCO,
   給 `object_refs` 會指向不存在的物件,因此內嵌 `objects`(SCO 的型別對照同 §7.8.3)。
   `sourceConfidence` 是 nullable,為 null 時省略 `confidence`。

`observed-data` 與 `relationship` 沒有對應的 domain UUID(它們的識別是一組欄位),
以**決定性的名稱型 UUID**產生 id——重投影是 UPSERT,不是每次新增一列。
`UUID.nameUUIDFromBytes` 產生 v3,符合 STIX identifier 的正規表示式(v1–v5 + variant 位元);
測試用的 `00000000-…-0000a1` 這種常數**不符合**,fixture 因此改用真實形狀的 v4。

## 6. `stix_relationships` 沒有 `content` 欄

表 9 只有三元組與信封欄位。對外的 JSON 於**讀取時**由同一組投影規則重建(角色來自
`threat_indicators.role`),不存第二份。`GET /stix/{stixId}` 因此擴充為服務全部 M2 物件,
可見度依**來源 domain 物件**判定(indicator / threat / 無來源),不看 STIX 型別;
`relationship` 另要求兩端都可見——它會洩漏「某個私有 IOC 屬於某個公開威脅」。

為此 `StixObjectPort` 新增 `findOrigin`:`observed-data` 的 id 是決定性雜湊、`identity` 來自
Source,兩者都無法從 stix_id 反推該檢查誰的可見度,只能問那一列自己記的來源。

## 7. `V31` 一併種入 `threat:manage`

`04 §4.7` 已把 `V32` / `V33` 指派給 Phase 20 / 21。Phase 18 若另開 `V34` 放權限種子,
Phase 20 之後補上的 `V32` 在既有資料庫上就是 out-of-order → `FlywayValidateException`
——正是 [ADR 0014](0014-flyway-monotonic-versions.md) 廢除區段預留所要修的坑。
種子因此與建表同在 `V31`(冪等)。同一個 migration 另補 `ix_so_threat`:
`fk_so_threat` 帶 `ON DELETE CASCADE` 卻沒有索引,刪一個 threat 就是一次全表掃描。

## 8. 其他

- `ThreatIndicatorLink` 實作為不可變 record(03 §3.2.7 標為 Entity,識別是 `indicatorId`):
  改角色以新值取代同一項,聚合快照因此不會外洩可變物件。H5 仍然成立——只存 id。
- `threats.aliases` 與 `tags` 的 `@>` 查詢一律經 `ctip_tags_contain_all`
  (`cast(? as text[])`;13 §13.7 的地雷),不得直接綁 `String[]`。
- Specification 查詢無法帶 `@EntityGraph`:清單頁改以 id 重新載入 entity graph,
  避免每個關聯各發一次 lazy 查詢(N+1)。
- 前端只交付兩個**唯讀**頁面(執行單的交付物就是這兩個);策展寫入目前只有 API,
  UI 的策展流程留待後續 phase(已在 `docs/progress.md` 註記)。
