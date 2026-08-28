# ADR 0020 — Phase 17–19 的規格定調(批 5)

- **狀態**:accepted
- **日期**:2026-08-28
- **範圍**:`02 §2.2/§2.3`、`04` 表 21、`07 §7.8`、`10 §10.7`、`13 §13.7`、
  `phases/phase-14/17/18/19.md`
- **背景**:清障計畫的批 5。同批 4:**不預先實作**,只定調照字面做不出來的部分。

---

## 1. 限流維度 1–3 的歸屬:三處各說各話

| 出處 | 說法 |
|---|---|
| `10 §10.7` | 「維度 1–3 與 `endpointClass` 隨 API key／方案於 **Phase 14/17** 加入」 |
| `phases/phase-17.md` | 「五個限流維度」列為 Phase 17 的交付物 |
| `docs/progress.md` | 「`AuthenticatedIdentity` 帶 `apiKeyId`,**Phase 14** 的限流維度 1–3 直接取用」 |

**定調為 Phase 17**。維度 1–3 需要依方案查表的 **per-key 限額**,而那需要 `RateLimiterPort`
改簽章(ADR 0019 §8)並與 Redis 後端一起做才有意義。Phase 14 只負責 `plans` 表與配額值本身。

> ⚠️ 一併重申一條容易被踩掉的約束:**維度 4(匿名 IP)必須留在認證之前**。
> `RateLimitFilter` 現在排在 `DEFAULT_FILTER_ORDER - 1`,那是 Phase 13 修掉
> 「認證失敗完全繞過限流」的迴歸(ADR 0012 決策 16)。維度 1–3 需要已解析的身分,
> 只能在認證之後——**兩者必須是兩個檢查點,不得把維度 4 一起搬到後面**。

## 2. `endpointClass` 的配額值從未定義

`10 §10.7` 定義了 `read`/`write`/`heavy` 三類,但 `plans` 表只有
`requests_per_minute` / `requests_per_day` **各一組**,04 與 10 都沒說三類各自的上限是多少。

**定調**:維度 5 不另設數值,而是**以方案總配額的比例**表示——
`read` = 100%、`write` = 20%、`heavy` = 5%(取整,至少 1)。
不必為每個方案多開六個欄位,也保證分類上限恆低於總上限。比例值為常數,不進 `plans` 表。

## 3. M2 的索引更新:同步還是經 Kafka

`13 §13.7` 寫「索引更新為非同步(**M2 起經 Kafka**)」,但同一份檔案的 §13.1 標明 Kafka 是
`[Phase 20 · M3]`,而 Phase 19(ES)在 M2。`08 §8.2` 則寫「M2 起在 `pe` 之後插入
`SearchIndexStage`」——即 pipeline 內同步。

**定調以 08 與 phase-19 為準**:M2 是 pipeline 內同步寫入,Phase 20 引入 Kafka 後才改非同步。
「索引失敗不得使 ingestion 失敗」在兩種模式下都必須成立。

## 4. H4 的唯一約束在 `external_id IS NULL` 時完全不生效

`04` 表 21 的 `ux_ter_identity UNIQUE (threat_id, source_name, external_id)`,
而 `external_id` 是 NULLable。**PostgreSQL 的 UNIQUE 不去重 null**——這正是
`06 §6.3.6` 自己列出的地雷,而規格自己踩了。

**定調**:改以 `CREATE UNIQUE INDEX ux_ter_identity_coalesced
ON threat_external_references (threat_id, source_name, COALESCE(external_id, ''))` 強制。

## 5. H6 是跨聚合不變量,與 H5 和 §2.2 互相拉扯

H6「`Threat.tlp` 不得比任一關聯 Indicator 更寬鬆」需要讀 `Indicator.tlp`,但:
H5 禁止 `ThreatIndicatorLink` 持有 Indicator 物件;§2.2 規定跨聚合只能以 ID 參照、
不使用同一交易;DB 也沒有任何對應約束。

**定調**:H6 **降格為應用層一致性規則**,由 application 在建立／變更關聯時強制
(讀關聯 Indicator 的 TLP,以 `Tlp.strictest` 收緊 `Threat.tlp`),不是 domain 不變量。
另因 Indicator 的 TLP 會在多來源合併時收緊,需有事件觸發對應 Threat 的重新收緊——Phase 18 交付。

## 6. `ThreatAlias`:同一件事三種型態

`02 §2.1` 的聚合表把它列為值物件(且**只出現在那一列**,不在 §2.5/§2.6 的值物件清單裡);
`03 §3.2` 的 class 圖寫 `Set<String> aliases`;`04` 表 19 寫 `TEXT[]`。

**定調以 04 的 `TEXT[]` 為準**(唯一有 schema 的一份),alias 就是字串集合,不需要值物件。
`02` 的聚合表已移除 `ThreatAlias`。

## 7. M2 的五種 STIX SDO 沒有任何欄位對照

`07 §7.8.2` 只有 `indicator` 的對照表、§7.8.4 只有 `marking-definition`,
而 §7.8.6 又要求「`content` 內容必須與 7.8.2–7.8.4 一致」——Phase 18 要投影的五種 SDO
**沒有依據可循**。

**定調**:補一張來源對照(`malware` ← `Threat(MALWARE_FAMILY)`、
`attack-pattern` ← `Threat(ATTACK_PATTERN)`、`observed-data` ← `IndicatorSource`、
`identity` ← `Source`),欄位級對照由 Phase 18 依 §7.8.2 的體例補寫進 §7.8。

**`course-of-action` 移除**:該列的「來源 domain 物件」欄原本是空的——平台沒有任何資料能填。
一個永遠產不出實例的投影型別就是規則 16 禁止的 placeholder。

`ThreatType` 的 `CAMPAIGN`/`THREAT_ACTOR`/`PHISHING_KIT` 仍可存於 `threats` 表
(它們是平台的分類),但 M2 不產生 STIX 物件。

## 8. Phase 19 的三個實作阻斷(寫進執行單,不預先實作)

- **`X-Search-Backend` 沒有傳遞通道**:`SearchPort` 回 `CursorPage<Indicator>`,
  而 phase-19 又禁止在 controller 判斷降級。需擴充回傳型別,並加進 CORS `exposedHeaders`。
- **三個 `SearchPort` bean 的歧義**:`PostgresSearchAdapter` 是 `@Component`,
  `IndicatorQueryService` 注入單一 `SearchPort`。
- ⚠️ **ES index mapping 必須重建可見度述詞**:`13 §13.7` 的搜尋欄位清單**不含**
  `ownerTenantId`、`deletedAt`、來源的 `redistributionPolicy`,但那三者是 `TlpSpecifications`
  與 `IndicatorFilterSpecs` 的可見度與側信道防護(ADR 0015)。
  **漏掉任何一個,ES 路徑就會整套繞過過濾。**
