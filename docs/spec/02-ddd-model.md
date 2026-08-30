# 02 — DDD 領域模型

> **規範等級：強制。** 聚合邊界、不變量、詞彙表為規範性內容。行為方法名稱為規範性建議——可增加方法，但**不得把不變量的執行位置移出聚合**。
>
> 相關檔案：[01-architecture.md](01-architecture.md)（分層）、[04-data-dictionary.md](04-data-dictionary.md)（持久化）、[03-diagrams.md](03-diagrams.md)（聚合圖）

---

## 2.0 為何是單一 Bounded Context

CTIP 是一個 deployable、一套詞彙、一個所有權邊界。依 DDD 的判準，Bounded Context 應跟隨**語言邊界與所有權邊界**——本專案兩者皆只有一組，因此：

- **一個 Bounded Context**，名為 `Threat Intelligence`
- 內部以**模組（Module）** 切分：`indicator`、`threat`、`source`、`tenant`、`identity`、`subscription`、`bloom`、`stix`、`fingerprint`、`notification`、`audit`
- **不建立 Context Map、不建立 ACL、不做 context 間翻譯層**

> 為什麼不切多個 context：切成 Intelligence / Identity / Distribution / Observability 四個 context 需要四套防腐層與四份翻譯模型，但目前沒有任何獨立的部署、團隊或生命週期壓力來正當化它。這會直接違反 [01-architecture.md](01-architecture.md) 的抽象判準（移除抽象後仍只有一種實作就不要加）。
>
> **未來若要切分**，第一條裂縫應在 `identity`（使用者與租戶）與 `intelligence`（情資）之間——這是唯一有可能出現獨立部署需求的接縫。屆時寫成 ADR，不要在本版預先建立。

---

## 2.1 Ubiquitous Language 詞彙表（中英對照）

**這張表是規範性的。** 本規格以繁體中文書寫，程式碼以英文撰寫；沒有這張對照表，不同 AI session 會產生不一致的命名。**程式碼中一律使用「英文術語」欄的字，不得使用「常見誤用」欄的字。**

| 中文 | 英文術語（程式碼用） | 定義 | 常見誤用（禁止） |
|---|---|---|---|
| 指標／情資指標 | `Indicator` | 單一可觀察的惡意跡象，本平台的核心聚合根 | `IOC` 作為類別名、`Ioc`、`Observable` |
| IOC | — | `Indicator` 的通俗簡稱，**僅用於 API 路徑與 UI 文案**（`/api/v1/iocs`），不得作為類別名 | 作為 domain 類別名 |
| 正規化值 | `normalizedValue` | 依型別規則轉換後的 canonical 形式，去重與指紋的唯一輸入 | `canonicalValue`、`cleanValue` |
| 指紋 | `fingerprint` | `SHA-256(normalizedValue)`，索引與 Bloom 成員用。**不是識別鍵** | `hash`（會與 IOC 檔案雜湊混淆） |
| IOC 雜湊型別 | `IocHashType` | 當 IOC 本身是檔案雜湊時的演算法（MD5/SHA1/…）。這是**資料內容** | `HashType`、`Algorithm` |
| 指紋演算法 | `FingerprintAlgorithm` | 計算去重指紋所用的演算法。這是**平台機制** | `HashType`、`HashAlgorithm` |
| 來源 | `Source` | 情資提供方（含 mock 與 `MANUAL`） | `Feed`、`Provider`、`Vendor` |
| 來源記錄 | `IndicatorSource` | 某來源對某 Indicator 的一筆回報，Indicator 聚合的內部實體 | `SourceRecord`、`Observation` |
| 來源信譽 | `reputation` | 0–100，合併時的加權權重 | `trust`、`score`（`score` 專指威脅分數） |
| 威脅分數 | `score` | 0–100，由 `ThreatScorer` 計算的綜合風險值 | `risk`、`rating` |
| 信心度 | `confidence` | 0–100，對「此 IOC 確為惡意」的信心 | `certainty` |
| 嚴重度 | `severity` | `INFO`–`CRITICAL` 的定性分級 | `priority`、`level` |
| 合併 | `merge` | 多來源回報聚合成單一 Indicator 的過程 | `combine`、`aggregate`（`aggregate` 專指 DDD 聚合） |
| 聚合 | `Aggregate` | DDD 的一致性邊界 | 用於「資料聚合」語意 |
| 去重 | `deduplication` | 以 `(type, normalizedValue, ownerTenantId)` 判定同一 Indicator | `dedup` 作為類別名 |
| 攝取 | `ingestion` | 從來源取得資料到落庫的整條流程 | `import`（`import` 專指使用者手動匯入）、`crawl` |
| 攝取階段 | `IngestionStage` | pipeline 中一個可獨立測試的步驟 | `Step`、`Processor`、`Handler` |
| 威脅 | `Threat` | campaign／malware family／actor 等高階實體 | `Campaign`（僅為 `ThreatType` 之一） |
| 租戶 | `Tenant` | 資料隔離的邊界主體 | `Organization`、`Account`、`Workspace` |
| 公開租戶 | public tenant | 固定的系統租戶 `00000000-…-0000`，承載公開情資 | `defaultTenant`、`globalTenant` |
| 方案 | `Plan` | 配額與功能的商業層級定義 | `Tier`、`Subscription`（後者是租戶與方案的關聯） |
| 訂閱 | `Subscription` | 某租戶對某方案的有效關聯 | 用於「事件訂閱」語意（那是 `WebhookFilter`） |
| 再散布政策 | `RedistributionPolicy` | 該資料可否對外提供的法遵限制 | `License`、`Sharing` |
| 交通燈協定 | `Tlp` | TLP 2.0 資料分級 | `Classification`、`Visibility` |
| 過期 | `EXPIRED` | `valid_until` 已過的狀態 | `stale`、`outdated` |
| 撤銷 | `REVOKED` | 高信譽來源明確撤回的狀態 | `deleted`、`removed` |
| 誤判 | `FALSE_POSITIVE` | 確認為非惡意的狀態 | `benign`、`whitelisted` |
| 游標 | `Cursor` | cursor 分頁的不透明位置標記 | `offset`、`page`、`token` |
| 快照 | full snapshot | 完整重建的 Bloom 位元陣列 | `backup`、`dump` |
| 增量 | `delta` | 兩個 Bloom 版本之間新增的位元 | `diff`、`patch`、`incremental` |
| 資料集版本 | `datasetVersion` | full snapshot 的版號，每次重建 +1 | `version`（過於籠統） |
| Bloom 版本 | `bloomVersion` | delta 的版號，日內每次生成 +1 | `deltaVersion` |

---

## 2.2 聚合清單

九個聚合根。**跨聚合只能以 ID 參照，不得持有物件參照。** 跨聚合的一致性以 domain event 達成，不使用同一交易。

| 聚合根 | 內部實體 | 值物件 | Phase |
|---|---|---|---|
| `Tenant` | — | `TenantSlug` | M1 |
| `Source` | — | `SourceHealth`、`SourceMetadata`（SDK）、`Reputation` | M1 |
| `Indicator` | `IndicatorSource`、`HashRecord` | `IocValue`、`Fingerprint`、`Confidence`、`ValidityPeriod`、`Tlp`、`Severity` | M1 |
| `User` | `RefreshToken` | `EmailAddress`、`PasswordHash` | M2 |
| `ApiKey` | — | `KeyPrefix`、`KeyHash`、`ScopeSet` | M2 |
| `Subscription` | — | `BillingPeriod` | M2 |
| `Threat` | `ThreatIndicatorLink` | `ExternalReference` | M2 |¹
| `BloomVersion` | `BloomArtifact` | `BloomParameters`（k, m, n, fpr）、`Checksum` | M2 |
| `Webhook` | — | `WebhookFilter`、`HmacSecret` | M3 |

> ¹ **`ThreatAlias` 已移除（2026-08-28；[ADR 0020](../architecture/decisions/0020-phase17-19-spec-resolutions.md)）**：
> 它原本只出現在本列,不在 §2.5/§2.6 的值物件清單裡;而
> [03 §3.2](03-diagrams.md) 的 class 圖寫 `Set<String> aliases`、
> [04 表 19](04-data-dictionary.md) 寫 `TEXT[]`——**同一件事三種型態**。
> 以 04 的 `TEXT[]` 為準(它是唯一有 schema 的一份),alias 就是字串集合,不需要值物件。


**非聚合**（無不變量、無狀態機 → 兩模型，無 domain model）：
`source_sync`、`ingestion_rejections`、`stix_objects`、`stix_relationships`、`roles`、`permissions`、`role_permissions`、`tenant_users`、`plans`、`webhook_deliveries`、`notifications`、`audit_logs`

---

## 2.3 聚合不變量

### <a id="tenant"></a>Tenant

| # | 不變量 |
|---|---|
| T1 | `slug` 全域唯一，符合 `^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$` |
| T2 | public tenant（`00000000-…-0000`）的 `type` 恆為 `SYSTEM`，且**不可刪除、不可更名、不可變更 type** |
| T3 | public tenant **不得有任何 `User`、`ApiKey`、`Webhook`、`Subscription`** |
| T4 | `type = SYSTEM` 的 tenant 只能有一個（即 public tenant） |

行為：`Tenant.rename(String)`（T2 拒絕）、`Tenant.suspend()`、`Tenant.isPublic()`

---

### <a id="source"></a>Source

| # | 不變量 |
|---|---|
| S1 | `reputation` ∈ [0, 100] |
| S2 | 狀態機：連續失敗 3 次 → `DEGRADED`；連續失敗 10 次 → `FAILED` 並發出 `SourceFailed` 事件；任一次成功 → `ACTIVE` 且 `consecutiveFailures = 0` |
| S3 | `DISABLED` **只能**由管理員手動設定，不可由失敗計數自動進入，也不可由一次成功自動離開 |
| S4 | `syncable = false` 的來源（如 `MANUAL`）**不參與**狀態機轉換，恆為 `ACTIVE` |
| S5 | `last_error_message` 不得包含憑證。寫入前必須經過遮罩 |
| S6 | `config` 中不存憑證原文，只存環境變數名稱 |

行為：`Source.recordSuccess(int recordCount, Duration latency)`、`Source.recordFailure(String maskedReason)`、`Source.disable()`、`Source.enable()`、`Source.isDueForSync(Instant now)`

---

### <a id="indicator"></a>Indicator

**這是核心聚合。** 以下不變量必須在 `Indicator` 內部強制，不得散落於 service 或 controller。

| # | 不變量 |
|---|---|
| I1 | `(type, normalizedValue, ownerTenantId)` 唯一——這是**唯一**的識別鍵。`fingerprint` 不是識別鍵（避免碰撞導致錯誤合併） |
| I2 | `fingerprint = SHA-256(normalizedValue)`，且**必須**對 `normalizedValue` 計算，絕不對 `value` 計算 |
| I3 | `hashType` 非 null ⟺ `type = FILE_HASH` |
| I4 | `lastSeen >= firstSeen` |
| I5 | `firstSeen = MIN(sources.sourceFirstSeen)`；`lastSeen = MAX(sources.sourceLastSeen)` |
| I6 | `validUntil = MAX(effectiveValidUntil)`，見 [04-data-dictionary.md](04-data-dictionary.md#46-過期與-ttl-規則強制跨表)。null 僅當所有來源皆無有效期 |
| I7 | `tlp` = 所有來源 `sourceTlp` 中**最嚴格**者（`CLEAR < GREEN < AMBER < AMBER_STRICT < RED`） |
| I8 | `severity` = 所有來源 `sourceSeverity` 的 `MAX` |
| I9 | `tags` = 所有來源 tags 的聯集 |
| I10 | `confidence` = 依 `source.reputation` 加權平均；獨立 `ACTIVE` 來源數 ≥ 3 時 `+10`，上限 100 |
| I11 | `status` 依固定順序判定（見下） |
| I12 | `confidence`、`score` ∈ [0, 100] |
| I13 | 至少有一筆 `IndicatorSource`。零來源的 Indicator 不合法 |
| I14 | 若所有來源的快照 `redistributionPolicy` 皆為 `INTERNAL_ONLY`，則此 Indicator 不得出現在**非擁有租戶**的任何回應與任何 Bloom 中 |

**`status` 判定順序（強制，短路求值）**

```text
1. 任一來源 status = RETRACTED 且該來源 reputation >= 80   → REVOKED
2. 任一來源 status = FALSE_POSITIVE 且無其他 ACTIVE 來源    → FALSE_POSITIVE
3. 所有來源 status = EXPIRED                              → EXPIRED
4. 其他                                                   → ACTIVE
```

行為：
```java
Indicator.mergeFrom(IndicatorSource report, Reputation sourceReputation)
Indicator.markExpired(Instant now)          // I6 為前提，否則拒絕
Indicator.revoke(SourceId by, Reputation)   // 執行規則 1
Indicator.reportFalsePositive(SourceId by)
Indicator.isVisibleTo(Tlp maxTlp, TenantId viewer)
Indicator.canBeRedistributedTo(TenantId viewer)   // 執行 I14
Indicator.eligibleForBloom()                      // status=ACTIVE 且 tlp=CLEAR
```

> **`IndicatorMergePolicy` 的位置**：I5–I11 的聚合規則集中於 `com.ctip.domain.indicator.IndicatorMergePolicy`（無狀態、純函式），由 `Indicator.mergeFrom` 呼叫。它需要 `source.reputation`，因此以參數傳入 `Reputation`——**不得**讓 domain 物件持有 repository。

---

### <a id="user"></a>User

| # | 不變量 |
|---|---|
| U1 | `email` 全域唯一且以小寫儲存 |
| U2 | `primaryTenantId` **不得**為 public tenant |
| U3 | `passwordHash` 為 BCrypt(cost≥12) 或 Argon2id。**絕不儲存原文** |
| U4 | `RefreshToken` 一枚只能使用一次；使用後 `usedAt` 非 null |
| U5 | 已使用（`usedAt` 非 null）的 refresh token 再次出現 → **撤銷該 `familyId` 的所有 token**，記錄 `TOKEN_REUSE_DETECTED` 稽核事件 |
| U6 | 同一 `familyId` 內最多一枚 token 處於「未使用且未撤銷且未過期」狀態 |
| U7 | `failedLoginCount` 達門檻時設定 `lockedUntil`；`lockedUntil > now()` 期間拒絕登入 |

行為：`User.rotateRefreshToken(RefreshToken presented)`（執行 U4–U6）、`User.recordFailedLogin()`、`User.recordSuccessfulLogin()`、`User.changePassword(PasswordHash)`

---

### <a id="apikey"></a>ApiKey

| # | 不變量 |
|---|---|
| K1 | `keyHash = SHA-256(fullKey)`。**原文只在建立當下回傳一次**，之後永不可查 |
| K2 | `keyPrefix` 為 `fullKey` 的前 8 碼，全域唯一 |
| K3 | `scopes` ⊆ 系統定義的 `permissions.code` 集合 |
| K4 | `scopes` ⊆ 建立者在該 tenant 的角色所擁有的權限（不得提權） |
| K5 | `tenantId` 不得為 public tenant |
| K6 | `revokedAt` 一旦設定即不可清除 |
| K7 | 有效性 = `revokedAt IS NULL AND (expiresAt IS NULL OR expiresAt > now())` |

行為：`ApiKey.issue(...)`（回傳 `IssuedApiKey(ApiKey, String plaintext)`）、`ApiKey.revoke()`、`ApiKey.isUsable(Instant)`、`ApiKey.hasScope(String)`

---

### <a id="subscription"></a>Subscription

| # | 不變量 |
|---|---|
| B1 | 一個 tenant 同時最多一份 `status = ACTIVE` 的訂閱 |
| B2 | `currentPeriodEnd` 為 null 或 `> currentPeriodStart` |
| B3 | `CANCELLED` 之後不可回到 `ACTIVE`（需建立新訂閱） |
| B4 | 沒有訂閱的已登入 tenant 視為 `FREE`；未登入視為 `ANONYMOUS` |
| B5 | `provider = NONE` 時 `externalSubscriptionId` 必須為 null |

行為：`Subscription.changePlan(PlanId)`、`Subscription.cancel()`、`Subscription.effectivePlanCode()`

---

### <a id="threat"></a>Threat

| # | 不變量 |
|---|---|
| H1 | `(ownerTenantId, type, name)` 唯一 |
| H2 | `lastSeen >= firstSeen` |
| H3 | 每個 `ExternalReference` 至少有 `externalId` 或 `url` 之一 |
| H4 | `(sourceName, externalId)` 在同一 Threat 內唯一 |
| H5 | 與 `Indicator` 的關聯以 ID 參照（`ThreatIndicatorLink` 只存 `indicatorId`），**不持有 `Indicator` 物件** |
| H6 | `tlp` 不得比其任一關聯 Indicator 更寬鬆 |

> **H6 的執行點（2026-08-28 定調；[ADR 0020](../architecture/decisions/0020-phase17-19-spec-resolutions.md)）**：
> H6「`Threat.tlp` 不得比任一關聯 Indicator 更寬鬆」是**跨聚合**不變量,與 H5
> (`ThreatIndicatorLink` 只存 `IndicatorId`)及 §2.2「跨聚合只能以 ID 參照、不使用同一交易」
> 互相拉扯——Threat 聚合內部拿不到 Indicator 的 TLP,DB 也沒有對應約束。
>
> **定調**:H6 由 **application 層在建立／變更關聯時**強制(讀取關聯 Indicator 的 TLP,
> 以 `Tlp.strictest` 收緊 `Threat.tlp`),**不是 domain 不變量**。
> 另因 Indicator 的 TLP 會在多來源合併時收緊,`IndicatorTlpTightened` 事件必須觸發
> 對應 Threat 的重新收緊——這是 Phase 18 的交付物。
> H6 因此從「聚合不變量」降格為「應用層一致性規則」,並在本表註記。

行為：`Threat.linkIndicator(IndicatorId, IndicatorRole)`、`Threat.unlinkIndicator(IndicatorId)`、`Threat.addExternalReference(ExternalReference)`、`Threat.changeStatus(ThreatStatus)`（`retire()` 即轉為終態 `RETIRED`）、`Threat.tightenTlpTo(Tlp)`

> **`changeStatus` 與 `tightenTlpTo` 為本版新增（2026-08-29，Phase 18;[ADR 0027](../architecture/decisions/0027-phase18-threat-and-m2-stix.md)）**：
> 原本只有 `retire()`,`ThreatStatus.DORMANT` 因此永遠不可達(規則 16 禁止的永不可達列舉值);
> `tightenTlpTo` 是 H6 降格為應用層規則後的執行點(收緊是單向的,永不放寬)。
> `RETIRED` 為終態:退役後不接受任何變更,要復活就建立新的 Threat。

---

### <a id="bloomversion"></a>BloomVersion

| # | 不變量 |
|---|---|
| L1 | `scope = PUBLIC` ⟹ `tenantId` = public tenant |
| L2 | `isFullSnapshot` ⟺ `baseBloomVersion IS NULL` |
| L3 | delta 的 `baseBloomVersion` 必須指向同一 `(scope, tenantId, datasetVersion)` 內既存的版本 |
| L4 | `bitSize`、`hashFunctionCount`、`fingerprintAlgorithm` 任一改變 → **必須**新起一個 `datasetVersion`（舊 client 的本地 Bloom 立即作廢） |
| L5 | `BloomArtifact.checksum` 為**未壓縮**位元陣列的 SHA-256 |
| L6 | delta 的 `resultingChecksum` 非 null；full 的為 null |
| L7 | public Bloom **只含 `tlp = CLEAR` 且 `status = ACTIVE` 且可再散布**的 Indicator。`GREEN` 及以上絕不進入 public Bloom |
| L8 | Bloom 命中**永不**代表確定惡意。任何把命中解釋為確定的程式碼皆為違規 |

行為：`BloomVersion.nextDelta(...)`、`BloomVersion.isCompatibleWith(BloomParameters clientParams)`、`BloomVersion.requiresFullSnapshot(int chainLength, long cumulativeDeltaBytes)`

> **實作回饋修訂（2026-08-28；[ADR 0024](../architecture/decisions/0024-phase15-bloom-decisions.md)）**
>
> - `requiresFullSnapshot` 實際多接一個 `BloomChainPolicy` 值物件：門檻之一
>   （`BLOOM_MAX_DELTA_CHAIN`）是設定值，domain 不得讀設定，也不該把 24 寫死（30% 仍由規格固定）。
>   該方法只允許對 **full snapshot** 版本呼叫——比例的分母是完整位元陣列的大小。
> - L3（delta 的 base 必須指向既存版本）跨聚合實例，無法在單一聚合內判定：
>   由 `nextDelta` 保證「新 delta 一定接在既有版本之後」，生成服務再以 repository 查得的最新版本
>   作為呼叫對象。
> - L7 的兩條成員述詞收在 `domain/bloom/BloomMembership`，不放 `Indicator`
>   （該檔已達 297 行、checkstyle 上限 300）；**tenant 層不含再散布條件**，見 [11 §11.2](11-sync-bloom.md#112-兩層架構)。
> - L8 不是資料條件，而是**禁止存在把命中解讀為確定的程式碼**：本聚合因此不提供任何
>   `isMalicious` 之類的查詢。

> **L7 的後果必須寫入 client 契約**：`TLP:GREEN` 的情資**沒有 Bloom 覆蓋**。client 端離線比對只涵蓋 `CLEAR`；Bloom miss **不代表**該值不在情資集合中，只代表不在公開集合中。見 [11-sync-bloom.md](11-sync-bloom.md)。

---

### <a id="webhook"></a>Webhook

| # | 不變量 |
|---|---|
| W1 | `targetUrl` 必須為 `https://`，且**必須指向平台外部** |
| W2 | `secretHash` 為 HMAC 密鑰的 SHA-256；原文只在建立當下回傳一次 |

> **W2 與 `Webhook.sign()` 在數學上互斥（2026-08-28 定調；[ADR 0021](../architecture/decisions/0021-phase20-23-spec-resolutions.md)）**：
> W2 與 [04 表 24](04-data-dictionary.md) 都寫「只存 secret 的 `SHA-256`，原文僅建立時回傳一次」，
> 但本節的行為清單有 `Webhook.sign(byte[] payload)`，而 [13 §13.2](13-platform-ops.md) 要求
> 每次送達都以 `HMAC-SHA256(secret, …)` 簽章。**伺服器手上只有摘要，重建不出 secret**——
> 照字面實作,`WebhookDeliveryTest`(M3-06)不可能通過。
>
> **定調**:secret **以 AES-GCM 加密後儲存**(欄位改名 `secret_encrypted`),
> 金鑰來自新環境變數 `WEBHOOK_SECRET_KEK`(prod 必須來自 secret manager,啟動守衛比照 `JWT_SECRET`)。
> 「原文僅建立時回傳一次」的對外契約不變——UI 與 API 都不再吐出 secret,只有送達路徑會解密使用。
> 這與 refresh token／API key 只存雜湊的作法不同,因為那兩者是**驗證**(比對即可),
> 而 webhook 簽章是**產生**(必須持有原文)。
| W3 | `consecutiveFailures` 達 5 → `DISABLED`，並發出 `WebhookDisabled` 事件（2026-08-28 更正，原寫 `SystemAlert`——該事件不在 §2.4 清單中；[ADR 0021](../architecture/decisions/0021-phase20-23-spec-resolutions.md)） |
| W4 | 送達重試最多 5 次，指數退避 |

> **W3 與 W4 的計數對象(2026-08-29,Phase 20;[ADR 0029](../architecture/decisions/0029-phase20-kafka-and-notifications.md) 第 7 節)**:
> `consecutiveFailures` 計的是**事件**,不是**嘗試**——連續五個事件各自用盡 W4 的五次嘗試才停用。
> 若計嘗試次數,單一個用盡重試的事件就會立刻觸發 W3,W3 便完全等同於 W4,
> 規格不會把它們分成兩條不變量。
>
> **`matches()` / `accepts()` 的輸入型別**改為 `NotificationEvent`(ADR 0029 第 1 節):
> §2.4 的 domain event 身上沒有 `WebhookFilter` 要的 severity / tags / sourceIds,
> 而 §13.1 禁止修改發佈端。過濾仍完全在伺服器端執行(W5 不變)。
| W5 | 訂閱過濾**必須在伺服器端執行**，不得把全部事件推給 client 再過濾 |
| W6 | 每租戶數量上限由 `plans.maxWebhooks` 於建立時檢查 |

行為：`Webhook.recordDelivery(DeliveryStatus)`、`Webhook.matches(DomainEvent)`、`Webhook.sign(byte[] payload)`

---

## 2.4 Domain Event 清單

**單一 `DomainEvent` 型別，M1–M2 走 `ApplicationEventPublisher`，M3 額外加一個轉發到 Kafka 的 listener。發佈端程式碼永不修改。**

所有事件必含：`eventId`（UUID，冪等鍵）、`eventType`、`occurredAt`、`tenantId`、`traceId`。

| 事件 | 發佈者（聚合） | Phase | 消費者 |
|---|---|---|---|
| `IndicatorCreated` | Indicator | M1 | Bloom(M2)、Search(M2)、Notification(M3) |
| `IndicatorMerged` | Indicator | M1 | Search(M2)、Notification(M3) |
| `IndicatorExpired` | Indicator | M1 | Bloom(M2)、Search(M2) |
| `IndicatorRevoked` | Indicator | M1 | Bloom(M2)、Search(M2)、Notification(M3) |
| `IndicatorFalsePositiveReported` | Indicator | M2 | Search(M2)、Audit(M3) |
| `IndicatorTlpTightened` | Indicator | M2 | Threat 的 H6 一致性(M2)¹ |
| `IngestionStarted` | — (application) | M1 | Audit(M3)、Metrics(M3) |
| `IngestionCompleted` | — (application) | M1 | Audit(M3)、Metrics(M3) |
| `IngestionFailed` | — (application) | M1 | Audit(M3)、Notification(M3) |
| `SourceDegraded` | Source | M1 | Notification(M3) |
| `SourceFailed` | Source | M1 | Notification(M3)、Audit(M3) |
| `SourceRecovered` | Source | M1 | Notification(M3) |
| `TenantCreated` | Tenant | M2 | Audit(M3) |
| `UserRegistered` | User | M2 | Audit(M3) |
| `TokenReuseDetected` | User | M2 | Audit(M3)、Notification(M3) |
| `ApiKeyCreated` / `ApiKeyRevoked` | ApiKey | M2 | Audit(M3) |
| `SubscriptionChanged` | Subscription | M2 | Audit(M3)、Notification(M3) |
| `ThreatUpdated` | Threat | M2 | Search(M2)、STIX 投影(M2)²、Notification(M3) |
| `BloomSnapshotReady` | BloomVersion | M2 | Notification(M3) |
| `WebhookDisabled` | Webhook | M3 | Notification(M3)、Audit(M3) |

> ¹ **`IndicatorTlpTightened` 為本版新增（2026-08-29，Phase 18；[ADR 0027](../architecture/decisions/0027-phase18-threat-and-m2-stix.md)）**：
> [ADR 0020](../architecture/decisions/0020-phase17-19-spec-resolutions.md) 第 5 節把 H6 定調為
> 應用層一致性規則，並要求「Indicator 的 TLP 在多來源合併時收緊 → 觸發對應 Threat 的重新收緊」。
> 沒有這個事件，H6 只在建立關聯的那一刻成立；本表原本沒有任何事件承載它。
> 欄位:`indicatorId`、`tenantId`、`previousTlp`、`currentTlp`。
>
> ² `ThreatUpdated` 以 `ThreatChange`(`CREATED`／`INDICATOR_LINKED`／`INDICATOR_UNLINKED`／
> `EXTERNAL_REFERENCE_ADDED`／`TLP_TIGHTENED`／`STATUS_CHANGED`)區分變更種類——本表對 Threat
> 只列一個事件,與其為每種變更新增規格沒有的事件型別,不如讓消費端以同一個事件重新載入聚合。

規則：
- **不得**把 JPA entity 當作事件 payload
- **AFTER_COMMIT 的消費端若要寫入資料庫,必須 `REQUIRES_NEW`**——那個回呼仍在已提交交易的
  synchronization 範圍內,預設的 `REQUIRED` 會去參與一個已經結束的交易,寫入沒有自己的提交邊界
  (ADR 0027)
  > **修訂(2026-08-29,Phase 20;[ADR 0029](../architecture/decisions/0029-phase20-kafka-and-notifications.md) 第 3 節)**:
  > 原文寫「寫入不落庫也不報錯」。實測把 `NotificationAdapter.recordIfAbsent` 改回 `REQUIRED`,
  > `EventIdempotencyTest` **仍然通過**——afterCommit 回呼期間連線尚未歸還,歸還(還原 autoCommit)
  > 時會一併提交。規則照留(`REQUIRES_NEW` 讓寫入有明確的提交邊界),但那個症狀在本專案的
  > JPA + PostgreSQL 組合下沒有重現,不應被當成既成事實引用。
  >
  > **反過來,有一件事必須在 `REQUIRES_NEW` 交易「內」做**:聚合因此發出的事件
  > (例如 `WebhookDisabled`)必須在那個交易內 `publish`。`EventPublisherPort` 會把它掛在
  > **當前交易**的 AFTER_COMMIT 上;在交易外呼叫,它會掛到一個已經走完 afterCommit 階段的交易上,
  > **永遠不會被觸發**。
- 事件欄位獨立於持久化模型；schema 版本化，存於 `docs/api/events/`
- 消費端必須冪等（以 `eventId` 去重）
- 事件在**交易提交後**發佈（`@TransactionalEventListener(phase = AFTER_COMMIT)`）

Kafka topic 對應（M3）見 [13-platform-ops.md](13-platform-ops.md)。

---

## 2.5 Shared Kernel：`ctip-sdk`

`ctip-sdk` 是 **Shared Kernel**，不只是「介面集」。它同時被 `ctip-core`（平台核心）與 `ctip-adapters`（含第三方實作的 adapter）依賴，雙方接受共同演化。

**理由**：`ThreatSourceAdapter` 的簽章不可避免地涉及 `IocType`、`Tlp`、`Severity`、`RedistributionPolicy`。若這些型別留在 `ctip-core`，SDK 看不到它們（依賴方向為 `core → sdk`）；若 SDK 另建一套 wire 型別再轉換，會產生成員完全相同的重複列舉，違反抽象判準。

**`ctip-sdk` 內容**

```text
com.ctip.sdk
├── ThreatSourceAdapter.java        介面
├── SourceMetadata.java             record
├── FetchContext.java               record
├── FetchResult.java                record
├── RawThreatRecord.java            record
├── SourceType.java                 enum
├── IocType.java                    enum      ← 由 core 下移
├── IocHashType.java                enum      ← 由 core 下移
├── FingerprintAlgorithm.java       enum      ← 由 core 下移
├── Tlp.java                        enum      ← 由 core 下移（含 strictness 比較）
├── Severity.java                   enum      ← 由 core 下移（含 ordinal 比較）
├── Confidence.java                 record    ← 由 core 下移（0-100 驗證）
└── RedistributionPolicy.java       enum
```

**約束（ArchUnit 驗證）**
- 零 `org.springframework.*` import
- 零 JPA／Hibernate 型別
- 僅依賴 JDK + `jakarta.validation-api`
- 必須可獨立發布至 Maven Central

**演化規則**：`ctip-sdk` 的任何破壞性變更（移除列舉成員、變更 record 欄位）視為 major 版本變更，必須寫 ADR。新增列舉成員為 minor 變更，但**所有 `switch` 必須為 exhaustive**（Java 25 的 pattern matching 會在編譯期強制）。

---

## 2.6 值物件清單

一律 `record`，immutable，在建構子驗證不變量。

| 值物件 | 位置 | 驗證 |
|---|---|---|
| `Confidence(int value)` | sdk | 0–100 |
| `Tlp` | sdk | enum，提供 `strictest(Tlp a, Tlp b)` |
| `Severity` | sdk | enum，提供 `max(Severity a, Severity b)` |
| `Fingerprint(String hex)` | core/fingerprint | `^[0-9a-f]{64}$` |
| `IocValue(IocType type, String raw, String normalized)` | core/indicator | normalized 非空、長度上限依型別 |
| `ValidityPeriod(Instant from, Instant until)` | core/indicator | `until` 為 null 或 `> from` |
| `Reputation(int value)` | core/source | 0–100 |
| `TenantSlug(String value)` | core/tenant | slug 格式 |
| `EmailAddress(String value)` | core/user | 小寫、RFC 基本格式 |
| `PasswordHash(String value)` | core/user | 非空、不得為明碼樣式 |
| `KeyPrefix(String value)` | core/identity | 長度 8 |
| `ScopeSet(Set<String> values)` | core/identity | 每項符合 `^[a-z]+:[a-z-]+$` |
| `BloomParameters(FingerprintAlgorithm algo, int k, long m, long n, double fpr)` | core/bloom | k>0、m>0、0<fpr<1 |
| `Checksum(String hex)` | core/bloom | `^[0-9a-f]{64}$` |
| `Cursor(Instant lastSeen, UUID id)` | core/shared | 兩者皆非 null |
| `CursorPage<T>(List<T> items, String nextCursor, boolean hasMore)` | core/shared | items 非 null |
| `ExternalReference(String sourceName, String externalId, String url, String description)` | core/threat | H3 |
| `WebhookFilter(Set<IocType>, Severity, Set<String>, Set<UUID>)` | core/notification | — |

> `CursorPage` **取代** Spring Data 的 `Page`。理由：`Page` 帶 `getTotalElements()`，需要 COUNT query——正是 cursor 分頁要避免的；且 `Page` 屬 spring-data-commons，不在 `ctip-core` 允許的依賴內（見 [01-architecture.md](01-architecture.md)）。

---

*檔案結束。九個聚合、21 個 domain event（§2.4 共 20 列，`ApiKeyCreated` / `ApiKeyRevoked` 同列）、18 個值物件。上次校對：2026-08-30（全專案複查；計數原寫 19，未計入 Phase 18 新增的 `IndicatorTlpTightened` 與同列的第二個 ApiKey 事件——§2.4 的表本身與程式碼一致，見 [ADR 0045](../architecture/decisions/0045-full-project-review-doc-sync.md)）。*
