# Plugin SDK — 寫一個 Threat Source Adapter

> 治理規格:[08 §8.1](../spec/08-ingestion-sdk.md#81-plugin-sdk-契約ctip-sdk)(SDK 契約)、
> [§8.4](../spec/08-ingestion-sdk.md#84-未來的真實-adapter)(真實來源的規則)、
> [§8.5](../spec/08-ingestion-sdk.md#85-韌性resilience4j)(韌性)、
> [02 §2.5](../spec/02-ddd-model.md#25-shared-kernelctip-sdk)(Shared Kernel)。
>
> **完整可編譯的範例**:
> [`backend/ctip-sdk/src/test/java/com/ctip/sdk/example/ExampleThreatSourceAdapter.java`](../../backend/ctip-sdk/src/test/java/com/ctip/sdk/example/ExampleThreatSourceAdapter.java)
> 與它的測試 `ExampleAdapterTest`。兩者在 CI 實際編譯並執行(DoD **M3-22**),
> 下面每一節都對應到那份範例的一段。

---

## 0. 你只需要一個 jar

```xml
<dependency>
  <groupId>com.ctip</groupId>
  <artifactId>ctip-sdk</artifactId>
  <version>0.1.0</version>
</dependency>
```

`ctip-sdk` 是 **Shared Kernel**:零 Spring、零 JPA、只依賴 JDK 與 `jakarta.validation-api`,
可獨立打包(DoD M3-21)。你**不需要**、也**不應該**依賴 `ctip-core` 或 `ctip-app`——
內建的 adapter 也只看得到這個 jar([ADR 0039](../architecture/decisions/0039-ctip-sdk-shared-kernel.md))。

---

## 1. 實作 adapter

介面只有三個方法:

```java
public interface ThreatSourceAdapter {
    SourceType sourceType();          // 註冊鍵,對應 sources.source_type
    SourceMetadata metadata();        // 來源自我描述
    FetchResult fetch(FetchContext context);   // 一次抓取
}
```

規則:

- **每個 `SourceType` 只能有一個實作**。重複註冊會在啟動時
  `IllegalStateException`——這是預期行為,不要改成「後者覆蓋前者」
- 真實的新來源需要在 `SourceType` **新增一個成員**(SDK 的 minor 變更),
  並在 `sources` 表有對應的一列。所有 `switch` 必須 exhaustive
- adapter 應該是**無狀態**的:同一個 `FetchContext` 必須回傳 `equals` 的結果
  (確定性是 `MockAdapterDeterminismTest` 這類測試的前提)

---

## 2. 宣告 metadata(含 TLP 與再散布政策)

```java
new SourceMetadata(
        "Example Feed",
        "TAB 分隔的 URL / Domain / IPv4 清單",
        "https://feed.example.invalid",
        Set.of(IocType.URL, IocType.DOMAIN, IocType.IPV4),
        Tlp.CLEAR,                                   // 這個來源的預設 TLP
        RedistributionPolicy.ATTRIBUTION_REQUIRED,   // 這個來源的授權條款
        Duration.ofHours(6),                         // 建議的抓取間隔
        true);                                       // 需要憑證嗎
```

⚠️ **`defaultTlp` 與 `redistributionPolicy` 是法遵輸入,不是註解**
([07 §7.9](../spec/07-domain-intel.md#79-再散布政策法遵強制))。
它們會被 ingestion 快照進 `indicator_sources`,並在**每一次對外輸出**時決定:

| RedistributionPolicy | 對外輸出時 |
|---|---|
| `PUBLIC_REDISTRIBUTABLE` | 可原樣對外提供 |
| `ATTRIBUTION_REQUIRED` | 可提供,但必須附上來源標註(前端 `attribution` 欄、STIX `external_references`) |
| `DERIVED_ONLY` | 只能提供衍生結果——可回答「此 IP 有風險」,**不得回傳原始記錄與來源明細** |
| `INTERNAL_ONLY` | **不得對外輸出**,僅供內部比對 |

上線任何真實來源前,**必須先確認並記錄它的 ToS 再散布限制**
([08 §8.4](../spec/08-ingestion-sdk.md#84-未來的真實-adapter))。填錯這一欄是法律問題,不是設定問題。

`recommendedInterval` 決定這個來源多久算「到期」;
排程的 `SOURCE_SYNC_CRON` 只是掃描節奏,實際是否抓取由這個值決定。

---

## 3. 解析來源資料

`fetch()` 拿到 `FetchContext`,回傳 `FetchResult`:

```java
public record FetchContext(Instant since, String cursor, Map<String, String> config, int maxRecords) {}
public record FetchResult(List<RawThreatRecord> records, String nextCursor, boolean hasMore) {}
```

| 欄位 | 意義 |
|---|---|
| `since` | 上次**成功**同步的時間,首次為 `null`。用來只抓新的 |
| `cursor` | 你自己定義的續抓游標,首次為 `null`。範例用的是資料集 offset |
| `maxRecords` | 本次上限,**必須遵守** |
| `hasMore` / `nextCursor` | `true` 時呼叫端會帶著 `nextCursor` 再呼叫一次 |

**解析失敗要大聲失敗**。範例在欄位數不符時直接拋例外中止整批,而不是靜默跳過那一行:
靜默跳過會讓「來源改格式」表現成「資料量慢慢變少」,那是最難察覺的一類故障。
單筆資料本身的品質問題(值不合法、型別推不出來)**不歸你管**——
交給平台的 Validate stage,它會連同拒絕原因寫進 `ingestion_rejections`。

STIX 風格的來源以 `rawPayload["revoked"] == true` 表達撤回
([08 §8.3](../spec/08-ingestion-sdk.md#83-必要的-mock-adapter-phase-5--m1))。

---

## 4. 「正規化輸出」= 忠實轉錄,不要自己正規化

這一節最容易做錯:**adapter 不做正規化**。

```text
adapter  →  1 Parse  →  2 Validate  →  3 Normalize  →  4 Fingerprint  → …
   ↑                                        ↑
你在這裡                          平台在這裡做小寫化、去空白、
                                  IDNA、去零寬字元、URL 正規化…
```

`RawThreatRecord.rawValue` 應該是**來源給你的樣子**:保留大小寫、保留尾端的點、
保留奇怪的空白。理由是 `value` 欄位要保留來源原始樣貌供顯示與稽核,
而指紋一律針對 `normalizedValue` 計算([07 §7.2](../spec/07-domain-intel.md#72-正規化規則強制))。
你若先正規化一次,平台再正規化一次,兩邊規則只要有一點不同,去重就會失效。

你**該**做的是把來源格式對應到欄位:

| `RawThreatRecord` 欄位 | 說明 |
|---|---|
| `rawValue` | 原始值(必填) |
| `declaredType` | 來源宣告的型別;推不出來就給 `null`,平台會推斷 |
| `declaredHashType` | 只有 `FILE_HASH` 有意義 |
| `observedAt` | 來源觀測到的時間 |
| `sourceConfidence` / `sourceSeverity` | 來源自己的評分;沒有就 `null`,**不要填一個中性值** |
| `validUntil` | **只在來源明示時**非 null——它是「來源說永不過期」與「來源沒說」的唯一區別 |
| `tags` / `rawPayload` | 來源附帶的標籤與原始 payload |

「沒有」與「是 0/50/預設值」在合併與評分裡是不同的輸入
([07 §7.5](../spec/07-domain-intel.md#75-多來源合併indicatormergepolicy))。填假值會污染跨來源合併的結果。

---

## 5. 註冊 adapter

平台以 Spring 注入集合完成註冊,**不寫 Factory**:

```java
public AdapterRegistry(List<ThreatSourceAdapter> all) {
    this.adapters = all.stream().collect(toUnmodifiableMap(ThreatSourceAdapter::sourceType, identity()));
}
```

但 **`ctip-adapters` 模組零 Spring 相依**:adapter 自己**不掛 `@Component`**,
bean 由 `ctip-app` 的 `AdaptersConfig` 宣告,並在註冊前統一套上韌性:

```java
@Bean
ThreatSourceAdapter exampleFeedAdapter(FetchResilience resilience) {
    return resilience.decorate(new ExampleFeedAdapter(httpFeedClient));
}
```

`FetchResilience` 提供 [§8.5](../spec/08-ingestion-sdk.md#85-韌性resilience4j) 的三件事,
以 `sourceType` 為 key 各自獨立:

| 機制 | 預設 |
|---|---|
| Retry | 3 次重試(總嘗試 4 次),指數退避 1s / 2s / 4s + jitter |
| Circuit breaker | 失敗率 50%(滑動視窗 20 次)→ 開啟 60s |
| Bulkhead | 每個來源最多 2 個並行抓取 |

**你不需要自己加任何韌性註解**,也不該自己重試——那會和外層的重試相乘。
Timeout 屬 HTTP 層(connect 5s / read 30s),由 `HttpFeedClients` 提供。

最後在 `sources` 表新增一列(migration),`enabled` **預設 `false`**:
真實外部來源一律先關著,確認 ToS 與憑證後才開。

---

## 6. 設定憑證

```java
String apiKey = context.config().get("exampleFeedApiKey");
if (apiKey == null || apiKey.isBlank()) {
    throw new IllegalStateException("缺少憑證設定:exampleFeedApiKey");
}
```

⚠️ **憑證一律來自環境設定,絕不進 `sources.config`**——
那一欄只存**環境變數的名稱**,值在解析後才進到 `FetchContext.config()`
([08 §8.4](../spec/08-ingestion-sdk.md#84-未來的真實-adapter))。

因此:

- 不要把 key 寫進 migration、種子資料或程式碼
- 缺憑證要**立刻失敗**,不要退化成「抓 0 筆」——後者會表現成來源健康度慢慢變差,查不出原因
- 憑證不得出現在日誌:平台的日誌遮罩會處理已知欄位名,但你自己 `log` 出去的字串它救不了

---

## 7. 測試 adapter

範例把「怎麼拿到 feed 內容」抽成一個函式介面:

```java
@FunctionalInterface
public interface FeedClient {
    String fetchFeed(String apiKey);
}
```

於是測試不需要 HTTP、不需要 Spring、不需要容器:

```java
var adapter = new ExampleThreatSourceAdapter(apiKey -> FIXTURE);
var result = adapter.fetch(new FetchContext(null, null, Map.of(API_KEY, "test-key"), 100));

assertThat(result.records()).hasSize(3);
```

至少要涵蓋這七件事(`ExampleAdapterTest` 各有一個案例):

1. metadata 宣告了正確的 TLP 與再散布政策
2. 每一行都轉成 `RawThreatRecord`,欄位對應正確
3. 來源沒宣告的欄位維持 `null`,**不是**預設值
4. `since` 只留下更新的記錄
5. `maxRecords` 有被遵守,`nextCursor` 能續抓且不重不漏
6. 同一個 `FetchContext` 回傳 `equals` 的結果(確定性)
7. 缺憑證、格式錯誤、`maxRecords <= 0` 都大聲失敗

跑起來:

```sh
./backend/mvnw -f backend/pom.xml -pl ctip-sdk test -Dtest=ExampleAdapterTest
```

---

## 8. 上線前檢查

- [ ] `SourceType` 新增成員,所有 `switch` 仍 exhaustive
- [ ] `sources` 表有對應的一列,`enabled = false`
- [ ] ToS 的再散布限制已確認,寫入 `redistribution_policy`
- [ ] 憑證只透過環境變數,`sources.config` 只存變數名稱
- [ ] bean 在 `AdaptersConfig` 宣告,並經 `FetchResilience.decorate(...)`
- [ ] 測試涵蓋上一節的七件事
- [ ] `./backend/mvnw -f backend/pom.xml -pl ctip-sdk package` 仍可獨立打包
