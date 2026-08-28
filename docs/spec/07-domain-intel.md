# 07 — 情資領域規則（IOC · TLP · STIX · 去重 · 合併 · 評分）

> **規範等級：強制。** 正規化規則、拒絕規則、合併規則、TLP 可見度、STIX 映射皆為規範性內容，且皆須有單元測試。
>
> 相關檔案：[02-ddd-model.md](02-ddd-model.md)（不變量）、[04-data-dictionary.md](04-data-dictionary.md)（持久化）

---

## 7.1 IOC 型別

```text
IocType: IPV4 | IPV6 | DOMAIN | URL | FILE_HASH | EMAIL
```

`FILE_HASH` 搭配 `IocHashType`（`MD5`/`SHA1`/`SHA256`/`SHA512`）說明演算法。

> v1.0 把 MD5/SHA1/SHA256/SHA512 直接列為 IOC type。改為 `FILE_HASH` + `hashType` 更貼近 STIX，也避免與去重指紋混淆。

### 命名釐清（強制）

| 概念 | 型別 | 是什麼 | 用在 |
|---|---|---|---|
| **IOC 雜湊型別** | `IocHashType` | **資料內容**：這個 IOC 本身是某檔案的雜湊 | `indicators.hash_type` |
| **指紋演算法** | `FingerprintAlgorithm` | **平台機制**：對 IOC 值算指紋以去重 | `hash_records.algorithm`、`BloomParameters` |

v1.0 把兩件不同的事都叫「Hash」，Coding LLM 幾乎必然混淆。**這兩個型別名稱不得合併、不得改名。**

---

## 7.2 正規化規則（強制）

**指紋一律針對 `normalizedValue` 計算，絕不針對 `value`。** `value` 保留來源原始樣貌，僅供顯示與稽核。

| 型別 | 規則 |
|---|---|
| **共通** | 去除前後空白；移除零寬字元（U+200B–U+200D、U+FEFF）；移除控制字元 |
| `IPV4` | 驗證後轉為標準點分十進位（去除前導零，`010.1.1.1` → `10.1.1.1`） |
| `IPV6` | 依 **RFC 5952** 壓縮（最長零段以 `::` 取代、小寫十六進位、去除前導零） |
| `DOMAIN` | 小寫；去除尾端點；IDN 轉 punycode（IDNA2008）；去除 `www.` 前綴**需可設定**（預設**不**去除） |
| `URL` | scheme 與 host 小寫；移除預設 port（http:80、https:443）；路徑百分比編碼正規化（大寫十六進位、解碼非保留字元）；query 參數依 key 排序；移除 fragment |
| `FILE_HASH` | 小寫十六進位；依長度驗證（MD5=32、SHA1=40、SHA256=64、SHA512=128） |
| `EMAIL` | domain 部分小寫；local part **保留原大小寫**（RFC 5321 規定 local part 大小寫敏感） |

> `www.` 預設不去除的理由：`www.example.com` 與 `example.com` 在 DNS 上是不同記錄，可能有不同的惡意狀態。去除是一個會遺失資訊的決定，因此設為 opt-in（`ctip.normalization.strip-www`）。

實作位置：`ctip-core/domain/indicator/normalization/`，每個型別一個 `IocNormalizer` 實作，由 `IocType` 分派。**單元測試必須覆蓋上表每一列，含髒資料案例。**

> **實作回饋修訂（2026-08-25，Phase 6；ADR 0004）**：DOMAIN 的 IDN 轉換以 JDK `java.net.IDN`
> 實作，其為 **IDNA2003**（RFC 3490）而非本表點名的 IDNA2008——版本表（[06 §6.2](06-tech-stack.md)）
> 沒有 ICU4J，規則 6 禁止自行加依賴。兩者差異僅在極少數字元（ß、ZWJ 等）。
> 若需嚴格 IDNA2008，須先把 ICU4J 納入版本表再改實作。

---

## 7.3 拒絕規則（強制）

必須**拒絕並記錄**至 `ingestion_rejections`，不得靜默接受、不得靜默丟棄。

| `reason` | 條件 |
|---|---|
| `MALFORMED_VALUE` | 正規化失敗或格式驗證不通過 |
| `PRIVATE_OR_RESERVED_IP` | `10/8`、`172.16/12`、`192.168/16`、`127/8`、`169.254/16`、`100.64/10`、`0/8`、`224/4`、`::1`、`fc00::/7`、`fe80::/10`——**除非**來源 metadata 明示允許 |
| `ALLOWLISTED_DOMAIN` | 命中可設定的良性網域 allowlist，見下方警告 |
| `LENGTH_EXCEEDED` | URL > 2048、domain > 253、email > 320 |
| `HASH_LENGTH_MISMATCH` | 雜湊長度與宣告的 `IocHashType` 不符 |
| `UNKNOWN_TYPE` | 無法推斷型別且來源未宣告 |
| `DUPLICATE_IN_BATCH` | 同一批次內重複（第二次起） |
| `QUOTA_EXCEEDED` | 手動提交／匯入超出方案配額 |

> ⚠️ **`ALLOWLISTED_DOMAIN` 必須只做 exact match，不得做後綴比對。**
> v1.1 寫「常見的良性大型服務網域（例如 google.com、microsoft.com）」。若實作為後綴比對，`docs.google.com/malicious-doc` 這類**極常見的實際釣魚 URL 會被當成良性丟棄**。
> 規則：allowlist 只比對 `DOMAIN` 型別的完整正規化值；`URL` 型別**不套用** allowlist。
> 預設 allowlist 為空，由 `ctip.data-quality.domain-allowlist` 設定。

> **實作回饋修訂（2026-08-25，Phase 6；ADR 0004）**：
> 1. `QUOTA_EXCEEDED` 在 M1 沒有 feed 觸發路徑（配額屬手動提交／匯入，Phase 14）。
>    規則以 `BatchState.remainingQuota`（null = 無配額；feed 一律 null）承載擴充點，
>    `RejectionRuleTest` 以配額歸零的批次覆蓋此分支——八種 reason 的測試契約不變。
> 2. pipeline 中非預期的單筆錯誤映射為 `MALFORMED_VALUE` 並將例外訊息寫入 `detail`
>    （本表八值受 DB CHECK 約束，不另設 INTERNAL_ERROR）。
> 3. 判定點：需要正規化值的規則（本表 2、3 與正規化失敗）於 Normalize stage canonical 化後
>    緊接執行，見 [08 §8.2](08-ingestion-sdk.md#82-攝取管線) 註記。

---

## 7.4 去重與指紋

### 識別鍵（唯一）

```text
(type, normalized_value, owner_tenant_id)
```

`fingerprint = SHA-256(normalized_value)` **僅作為索引與 Bloom 成員，不是識別鍵**——避免雜湊碰撞造成兩個不同 IOC 被錯誤合併。

### 指紋策略

```java
public interface FingerprintStrategy {
    FingerprintAlgorithm algorithm();
    Fingerprint fingerprint(String canonicalValue);
}
```

**M1 只實作 `Sha256FingerprintStrategy`。** 介面保留是為了未來可換演算法（Bloom 需要多個獨立雜湊時），不是為了展示 Strategy Pattern。

指紋輸入為 `normalizedValue` 的 UTF-8 位元組，輸出為 64 字元小寫十六進位。

---

## 7.5 多來源合併（`IndicatorMergePolicy`）

集中於 `com.ctip.domain.indicator.IndicatorMergePolicy`，無狀態純函式，**必須有完整單元測試**。

| 欄位 | 聚合規則 |
|---|---|
| `firstSeen` | `MIN(sourceFirstSeen)` |
| `lastSeen` | `MAX(sourceLastSeen)` |
| `validUntil` | `MAX(effectiveValidUntil)`，見 [04](04-data-dictionary.md#46-過期與-ttl-規則強制跨表)。**不是** v1.1 的「任一為 null 則 null」 |
| `confidence` | 依 `source.reputation` 加權平均；獨立 `ACTIVE` 來源數 ≥ 3 時 `+10`，上限 100 |
| `severity` | `MAX`（`INFO < LOW < MEDIUM < HIGH < CRITICAL`） |
| `tlp` | **最嚴格**者（`CLEAR < GREEN < AMBER < AMBER_STRICT < RED`） |
| `tags` | 聯集 |
| `status` | 見下方判定順序 |
| `sourceCount` | 獨立 `ACTIVE` 來源數 |

### `confidence` 加權公式

```text
weighted = Σ(sourceConfidence_i × reputation_i) / Σ(reputation_i)
           （只計 status = ACTIVE 且 sourceConfidence 非 null 的來源）

bonus    = (獨立 ACTIVE 來源數 >= 3) ? 10 : 0
result   = min(100, round(weighted) + bonus)
```

若所有 ACTIVE 來源皆未提供 `sourceConfidence`，則 `weighted = 50`（中性值）。

### `status` 判定順序（強制，短路求值）

```text
1. 任一來源 status = RETRACTED 且該來源 reputation >= 80   → REVOKED
2. 任一來源 status = FALSE_POSITIVE 且無其他 ACTIVE 來源    → FALSE_POSITIVE
3. 所有來源 status = EXPIRED                              → EXPIRED
4. 其他                                                   → ACTIVE
```

### 同來源再次回報(UPSERT)的 status 規則

> **實作回饋修訂(2026-08-27,M1 總複查;ADR 0011)**:同來源 UPSERT 的來源記錄 status——
> 新回報為 `RETRACTED` 一律生效;既有 `RETRACTED` 與 `FALSE_POSITIVE` **不因後續 `ACTIVE`
> 回報復活**(前者對齊 STIX 2.1 `revoked` 的單向性,後者為使用者斷言);`EXPIRED` 因新觀測
> 回到 `ACTIVE`。原實作無條件將 UPSERT 後的記錄設回 `ACTIVE`,任何一次全量重同步或失敗重試
> 都會沖掉撤回,使上方規則 1 的 `REVOKED` 判定失效。

### 來源信譽

`sources.reputation`（0–100，預設 50）由管理員設定。M1 使用種子值即可。
`reputation >= 80` 為「可信任撤回」門檻（`Reputation.isTrustedForRetraction()`）。

> ⚠️ **重建後必須補入全部涉及來源的信譽（2026-08-25 Phase 6 實測補入；ADR 0004）**：
> `Indicator` 聚合自持久化重建後，其 reputations 對照為**空**，缺席的來源在加權公式中以
> 中性值 50 計——直接合併會算出錯誤的 `confidence`（實測 55 vs 正確 60）。
> 任何合併路徑（pipeline 的 MergeStage、未來 Phase 14 的手動提交）都必須先查出既有來源
> 記錄的 `sources.reputation` 一併傳入（`Indicator.mergeFrom(report, reputation, known)` 多載）。

---

## 7.6 威脅評分

```java
public interface ThreatScorer {
    int score(Indicator indicator, List<IndicatorSource> sources, Map<SourceId, Reputation> reputations);
}
```

M1 實作 `RuleBasedThreatScorer`：

| 輸入 | 權重 | 計算 |
|---|---|---|
| 來源信譽加權後的 `confidence` | 40% | `confidence / 100 × 40` |
| `severity` | 25% | `INFO=0, LOW=25, MEDIUM=50, HIGH=75, CRITICAL=100` → `× 0.25` |
| 獨立 ACTIVE 來源數 | 20% | `min(1, log(1+n) / log(6)) × 20`（上限 5 個來源） |
| Recency（`lastSeen` 指數衰減） | 15% | `0.5^(daysSince / 30) × 15`（半衰期 30 天） |

結果四捨五入為 0–100 的整數。

保持在抽象之後，未來可替換為 ML 模型。**M1 不得實作任何 ML。**

> **注意重複計算**：`confidence` 本身已含「來源數 ≥ 3 時 +10」的加成，而評分又獨立計入來源數 20%。這是刻意的——`confidence` 反映「是否為惡意」的信心，`score` 反映「應優先處理的程度」，多來源同時提高兩者是合理的。**但兩處的來源數定義必須一致**（皆為獨立 `ACTIVE` 來源數），且評分測試必須包含一個驗證此一致性的案例。

---

## 7.7 TLP 2.0

```text
Tlp: CLEAR | GREEN | AMBER | AMBER_STRICT | RED
嚴格程度: CLEAR < GREEN < AMBER < AMBER_STRICT < RED
```

### <a id="tlp-可見度"></a>可見度（強制，與方案完全解耦）

**TLP 是資料分級（資料本身與其歸屬的屬性）；方案是商業建構。兩者不得耦合。**

| AuthState | 可見範圍 |
|---|---|
| `ANONYMOUS` | public tenant 的 `CLEAR` |
| `AUTHENTICATED` | public tenant 的 `CLEAR` + `GREEN`，**加上**自家 tenant 的全部 TLP |

過濾條件（唯一一套邏輯，見 [01-architecture.md](01-architecture.md#111-m1-最小安全層強制phase-4)）：

```sql
owner_tenant_id IN (:currentTenantId, '00000000-0000-0000-0000-000000000000')
AND tlp <= :maxVisibleTlp
```

**相對 v1.1 的三項修正**

1. **移除方案維度。** v1.1 的矩陣中 Free 與 Premium 完全相同（都是 `CLEAR`+`GREEN`），Premium 在資料層買不到任何東西；而「Tenant 成員」是獨立一列，但每個登入者都是某個 tenant 的成員，兩列語意重疊。把方案接進 TLP 過濾還會迫使**每一次查詢都先載入使用者的方案**——把商業邏輯放進安全關鍵的 query filter。方案的價值留在 [10-identity-plans.md](10-identity-plans.md) 的配額與功能。
2. **過濾條件改為 `IN (current, public)`。** v1.1 §25.1 寫「自動附加 `tenant_id` 條件」（單數），如此登入者**看不到公開情資**。§24.2 聲稱消除的特例分支其實還在。
3. **public tenant 同時持有 `CLEAR` 與 `GREEN`。** v1.1 §24.2 規定公開情資「`tlp = CLEAR`」，與可見度表夾起來會使 `GREEN` **沒有任何棲息地**，整個等級是死的。TLP 2.0 對 `GREEN` 的定義是「限於社群範圍，排除公開可存取通道」——正好對應「需登入才看得到的公開租戶資料」。

### `RED` 的處理

`RED` **不進入平台**。ingestion 階段遇到來源標記 `RED` 的資料一律拒絕（`reason = MALFORMED_VALUE`，`detail = "TLP:RED not accepted"`）。

`Tlp` enum 仍保留 `RED` 成員，理由有二：合併規則需要完整的嚴格程度序列；未來若要支援 `RED`（需明確授權紀錄與獨立稽核）不必改 enum。**但 M1–M3 皆不得有任何 `RED` 資料落庫**，且必須有一條測試驗證此點。

### 合併

一個 IOC 有多個來源時，取**最嚴格**的 TLP。

### Bloom 覆蓋範圍（重要）

public Bloom **只含 `CLEAR`**。`GREEN` 及以上**沒有 Bloom 覆蓋**——public Bloom 設計為可放 CDN、無租戶隔離，而 TLP 2.0 明確排除把 `GREEN` 放上公開可存取通道。

**這個限制必須寫入 client 契約**（見 [11-sync-bloom.md](11-sync-bloom.md)）：Bloom miss **不代表**該值不在情資集合中，只代表不在**公開**集合中。

---

## 7.8 STIX 2.1 映射

### 7.8.1 支援範圍與里程碑

| STIX 物件 | M1 | M2 | 來源 domain 物件 |
|---|---|---|---|
| `indicator` | ✅ | ✅ | `Indicator` |
| `marking-definition` | ✅ | ✅ | `Tlp`（固定五個） |
| `bundle` | ✅ | ✅ | 匯出容器 |
| `observed-data` | — | ✅ | `IndicatorSource` |
| `malware` | — | ✅ | `Threat`（type=`MALWARE_FAMILY`） |
| `attack-pattern` | — | ✅ | `Threat`（type=`ATTACK_PATTERN`） |
| `identity` | — | ✅ | `Source` / `Tenant` |
| `relationship` | — | ✅ | `ThreatIndicatorLink` |
| `course-of-action` | — | ✅ | — |

> **M2 的 SDO 映射（2026-08-28 定調；[ADR 0020](../architecture/decisions/0020-phase17-19-spec-resolutions.md)）**
>
> §7.8.2 只有 `indicator` 的欄位對照表、§7.8.4 只有 `marking-definition`，而 §7.8.6 又要求
> 「`content` JSONB 內容必須與 **7.8.2–7.8.4** 的規則一致」——Phase 18 要投影的五種 SDO
> **沒有任何欄位對照可依**。定調:
>
> | STIX 型別 | 來源 domain 物件 | 對照表 |
> |---|---|---|
> | `malware` | `Threat`（`type = MALWARE_FAMILY`） | Phase 18 依 §7.8.2 的體例補寫入本節 |
> | `attack-pattern` | `Threat`（`type = ATTACK_PATTERN`） | 同上 |
> | `observed-data` | `IndicatorSource`（單一來源的一次觀測） | 同上 |
> | `identity` | `Source`（情資提供方） | 同上；`stix_id` 規則為 `identity--{sourceId}` |
> | ~~`course-of-action`~~ | **無** | **本版移除** |
>
> **`course-of-action` 移除的理由**:本表原本它的「來源 domain 物件」欄是空的——
> 平台沒有任何資料能填。一個永遠產不出實例的投影型別就是規則 16 禁止的 placeholder。
> 未來若引入緩解措施(mitigation)類的 domain 概念,再重新納入。
>
> `ThreatType` 的 `CAMPAIGN` / `THREAT_ACTOR` / `PHISHING_KIT` 在 STIX 2.1 分別對應
> `campaign` / `threat-actor` / 無標準型別。**M2 只投影 `MALWARE_FAMILY` 與 `ATTACK_PATTERN`**;
> 其餘三型仍可存於 `threats` 表(它們是平台的分類),只是不產生 STIX 物件。

> **相對 v1.1 的範圍修正**：v1.1 的 §16.1 要求 M1 支援 `malware` 與 `attack-pattern`，但 §18.3 明確說「M1 不實作 Threat」——**M1 沒有任何資料能填進這兩種物件**。本版把 M1 收斂到「只有 `Indicator` 能產出的物件」。

### 7.8.2 `indicator` 映射（強制對照表）

STIX id 使用 domain 物件自己的 UUID，保證穩定且可逆：`indicator--{indicators.id}`

| STIX 屬性 | 必填 | 來源 | 規則 |
|---|---|---|---|
| `type` | ✅ | — | 固定 `"indicator"` |
| `spec_version` | ✅ | — | 固定 `"2.1"` |
| `id` | ✅ | `indicators.id` | `indicator--{uuid}` |
| `created` | ✅ | `indicators.created_at` | ISO-8601，毫秒精度，`Z` 結尾 |
| `modified` | ✅ | `indicators.updated_at` | 同上 |
| `pattern` | ✅ | `type` + `normalized_value` | 見 7.8.3 |
| `pattern_type` | ✅ | — | 固定 `"stix"` |
| `pattern_version` | — | — | 固定 `"2.1"` |
| `valid_from` | ✅ | `indicators.valid_from` | |
| `valid_until` | — | `indicators.valid_until` | null 則省略此屬性 |
| `name` | — | 產生 | `"{type}: {value}"`，截斷至 255 |
| `description` | — | 產生 | 含來源數與 score |
| `indicator_types` | — | 產生 | `["malicious-activity"]`；`status=FALSE_POSITIVE` 時為 `["benign"]` |
| `confidence` | — | `indicators.confidence` | STIX 的 `confidence` 亦為 0–100，直接對應 |
| `labels` | — | 產生 | `["severity:{severity}", "score:{score}"]` ＋ `indicators.tags` |
| `revoked` | — | `indicators.status` | `status = REVOKED` 時為 `true`，否則省略 |
| `object_marking_refs` | ✅ | `indicators.tlp` | 見 7.8.4 |
| `external_references` | — | `indicator_sources` | 僅當 `redistribution_policy = ATTRIBUTION_REQUIRED` 或 `PUBLIC_REDISTRIBUTABLE` 時附上來源標註 |

`created_by_ref` 於 M1 省略（`identity` 物件為 M2）。

> **實作回饋修訂（2026-08-26，Phase 8；ADR 0005）**：
> 1. `created`/`modified` 對照的 `created_at`/`updated_at` 由 DB 於 persist 時產生，但投影建構於
>    Persist **之前**（stage 8）——實作以「既有投影的 `stix_created`（UPSERT 保留）、當下時間為
>    modified」近似，語意等價（created ≈ 首次投影、modified ≈ 本次合併時間）。
> 2. `external_references` 除 `source_name` 外必須附 `description`/`url`/`external_id` 之一——
>    OASIS JSON Schema 對 external-reference 的 oneOf 會拒絕只有 source_name 的物件。
>    M1 的 `Source` 無 homepage 欄位，故附固定 `description`；M2 補 homepage 後改附 `url`。

### 7.8.3 STIX Patterning 模板（強制）

`pattern` 必須是合法的 STIX Patterning 語言。**六個型別各有固定模板**：

| `IocType` | pattern 模板 | 範例 |
|---|---|---|
| `IPV4` | `[ipv4-addr:value = '{v}']` | `[ipv4-addr:value = '203.0.113.1']` |
| `IPV6` | `[ipv6-addr:value = '{v}']` | `[ipv6-addr:value = '2001:db8::1']` |
| `DOMAIN` | `[domain-name:value = '{v}']` | `[domain-name:value = 'evil.example.com']` |
| `URL` | `[url:value = '{v}']` | `[url:value = 'https://evil.example.com/a']` |
| `EMAIL` | `[email-addr:value = '{v}']` | `[email-addr:value = 'a@evil.example.com']` |
| `FILE_HASH` | `[file:hashes.'{alg}' = '{v}']` | `[file:hashes.'SHA-256' = 'abc…']` |

**`IocHashType` → STIX hashing-algorithm-ov 對應（注意連字號）**

| `IocHashType` | STIX 雜湊鍵 |
|---|---|
| `MD5` | `MD5` |
| `SHA1` | **`SHA-1`** |
| `SHA256` | **`SHA-256`** |
| `SHA512` | **`SHA-512`** |

> 直接把 enum 名稱塞進 pattern（`SHA256`）會產出不符合 `hashing-algorithm-ov` 的 pattern，多數 STIX 驗證器會拒絕。**必須經過此對應表。**

**跳脫規則**：`{v}` 為 `normalized_value`。若其中含單引號 `'` 或反斜線 `\`，必須以反斜線跳脫（`\'`、`\\`）。實作必須提供 `StixPatternEscaper` 並有針對含引號 URL 的測試。

### 7.8.4 TLP 2.0 marking-definition（固定值，**不得自行產生**）

TLP 2.0 在 STIX 2.1 中是**擴充定義（extension-definition）**，不是核心物件。以下 UUID 由 OASIS 定義，**必須原樣使用**：

```text
extension-definition--60a3c5c5-0d10-413e-aab3-9e08dde9e88d   ← TLP 2.0 擴充定義 ID
```

| `Tlp` | `marking-definition` id | `name` | `tlp_2_0` |
|---|---|---|---|
| `CLEAR` | `marking-definition--94868c89-83c2-464b-929b-a1a8aa3c8487` | `TLP:CLEAR` | `clear` |
| `GREEN` | `marking-definition--bab4a63c-aed9-4cf5-a766-dfca5abac2bb` | `TLP:GREEN` | `green` |
| `AMBER` | `marking-definition--55d920b0-5e8b-4f79-9ee9-91f868d9b421` | `TLP:AMBER` | `amber` |
| `AMBER_STRICT` | `marking-definition--939a9414-2ddd-4d32-a0cd-375ea402b003` | `TLP:AMBER+STRICT` | `amber+strict` |
| `RED` | `marking-definition--e828b379-4e03-4974-9ac4-e53a884c97c1` | `TLP:RED` | `red` |

所有 marking-definition 的 `created` 固定為 `2022-10-01T00:00:00.000Z`。

**marking-definition 物件格式（原樣輸出）**

```json
{
  "type": "marking-definition",
  "spec_version": "2.1",
  "id": "marking-definition--94868c89-83c2-464b-929b-a1a8aa3c8487",
  "created": "2022-10-01T00:00:00.000Z",
  "name": "TLP:CLEAR",
  "extensions": {
    "extension-definition--60a3c5c5-0d10-413e-aab3-9e08dde9e88d": {
      "extension_type": "property-extension",
      "tlp_2_0": "clear"
    }
  }
}
```

> ⚠️ 上表五個 UUID 皆為 **OASIS TLP 2.0 擴充的官方值**,與核心 STIX 2.1 規格內建的 TLP 1.0
> marking(`marking_ref` 靜態定義,如 TLP:GREEN 的 `34098fce-…`)**是不同的 UUID**;
> TLP 2.0 版本另帶 `extensions` 區塊。本專案一律輸出 TLP 2.0 形式。
> (2026-08-27 M1 總複查修正:本註記原誤稱 GREEN/AMBER/RED 與 TLP 1.0 相同——說法不實,
> 但表格內的 UUID 本身正確,程式碼與表格逐字一致,無實害;ADR 0011。)
>
> 實作為 `StixTlpMarkings`（`ctip-core/domain/stix/`）中的五個 `static final` 常數。**任何以 `UUID.randomUUID()` 產生 marking-definition 的程式碼皆為違規**，且必須有一條測試驗證這五個字串完全相符。
>
> 已於 2026-08-21 對照 OASIS `cti-stix-common-objects` repo 的 `extension-definition-specifications/tlp-2.0/examples/` 查證。

### 7.8.5 `bundle` 匯出

```json
{
  "type": "bundle",
  "id": "bundle--<uuid v4>",
  "objects": [ /* marking-definition 先，其餘物件後 */ ]
}
```

規則：
- `bundle` 的 `id` 每次匯出重新產生（bundle 不是可持久化物件，STIX 2.1 明確規定 bundle 無 `created`/`modified`）
- bundle 中**只包含實際被引用的 marking-definition**，不要全部五個都塞進去
- 物件數上限依方案（`plans.stix_export_max_objects`）；超過回 `403 PLAN_LIMIT_EXCEEDED`
- 匯出前必須經過 `RedistributionFilter`（7.9）
- 匯出前必須經過 TLP 過濾（7.7）

### 7.8.6 `stix_objects` 表的定位

`stix_objects` 與 `stix_relationships` 是**衍生投影**，domain model 才是 source of truth。因此：

- 可隨時由 domain 完整重建（必須提供 `POST /api/v1/admin/stix/rebuild`，M3）
- 投影失敗**不得**使 ingestion 失敗，只記錄並排入重試
- `content` JSONB 內容必須與 7.8.2–7.8.4 的規則一致；CI 需有一條測試以 STIX 2.1 JSON Schema 驗證產出

---

## 7.9 再散布政策（法遵，強制）

⚠️ **多數商業情資來源的服務條款禁止再散布原始資料。** 本平台提供對外 API，因此必須在資料層追蹤此限制。

```text
RedistributionPolicy:
  PUBLIC_REDISTRIBUTABLE   可原樣對外提供
  ATTRIBUTION_REQUIRED     可提供，但回應中必須附上來源標註
  DERIVED_ONLY             只能提供衍生結果（可回答「此 IP 有風險」，不得回傳原始記錄與來源明細）
  INTERNAL_ONLY            不得對外輸出，僅供內部比對
```

### 規則

1. `indicator_sources` 在 ingestion 當下**快照**該來源的政策（來源條款會變，歷史資料須依當時政策處理）
2. 輸出層依政策過濾或遮罩，邏輯**必須集中在一個 `RedistributionFilter`**，不得散落各 controller
3. **若某 IOC 的所有來源皆為 `INTERNAL_ONLY`，該 IOC 不得出現在「非擁有租戶」的任何回應與任何 Bloom 中**
4. `ATTRIBUTION_REQUIRED` 的資料，API 回應必須含 `attribution` 欄位（來源顯示名稱與 homepage）
5. `DERIVED_ONLY` 的資料：回應可含 `score`、`severity`、`status`，但**不得**含 `indicator_sources` 明細、`raw_payload`、來源名稱

### 第 3 條的作用域修正（重要）

v1.1 寫「不得出現在任何對外 API 或 Bloom filter 中」。照字面執行，**租戶會看不到自己剛提交的資料**——因為租戶查自己的資料走的也是同一個對外 API，而手動提交的預設政策是 `INTERNAL_ONLY`。功能直接失效。

**修正後的作用域**：`RedistributionFilter` 只作用於**跨租戶與公開輸出**。

```text
if (viewerTenantId == indicator.ownerTenantId
    && !indicator.ownerTenantId.isPublic())   → 不套用再散布過濾（租戶對自己的資料看得到全貌）
else                                          → 套用完整過濾
```

> **實作回饋修訂（2026-08-26，Phase 9；ADR 0006）——豁免必須排除 public tenant**：
> 本節原偽碼只寫 `viewerTenantId == ownerTenantId`。但匿名身分綁定的 viewerTenantId **就是**
> public tenant（[01 §1.11](01-architecture.md#111-m1-最小安全層強制phase-4)），而 feed 攝取的
> 情資 owner 也是 public tenant——照原字面實作，匿名對全部公開情資都算「擁有租戶」，
> 規則 3/4/5 對公開輸出**完全失效**，這正是本節要防的法遵場景。
> public tenant 無成員，對 public 資料的任何存取都是「公開輸出」；豁免僅適用非 public 租戶
> 看自家資料（原修正的本意——手動提交歸屬提交者租戶，不受影響）。
> 同一規則落實於三處：domain `Indicator.canBeRedistributedTo`（I14）、
> query 層 `TlpSpecifications`、輸出層 `RedistributionFilter`。

Bloom 一律套用（Bloom 沒有 viewer 概念）。

---

## 7.10 IOC 過期

型別預設 TTL 與 `valid_until` 三步計算規則見 [04-data-dictionary.md](04-data-dictionary.md#46-過期與-ttl-規則強制跨表)。

TTL 從 `lastSeen` 起算。每次有新來源回報同一 IOC，`lastSeen` 更新並順延 `validUntil`。

排程：每日 03:00（見 [08-ingestion-sdk.md](08-ingestion-sdk.md#排程)）。

---

*檔案結束。上次校對：2026-08-21（TLP 2.0 marking UUID 已對照 OASIS 官方 repo 查證）。*
