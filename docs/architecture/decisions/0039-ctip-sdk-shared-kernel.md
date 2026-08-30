# ADR 0039 — `ctip-sdk` 作為 Shared Kernel

- 狀態:accepted(2026-08-30,Phase 23 補記;決策見
  [00 §0.6](../../spec/00-master.md#修正的規格衝突10-項) 衝突 #1,實作於 Phase 1/5)
- 範圍:[02 §2.5](../../spec/02-ddd-model.md#25-shared-kernelctip-sdk)、
  [01 §1.3](../../spec/01-architecture.md#13-maven-multi-module)、
  [08 §8.1](../../spec/08-ingestion-sdk.md#81-plugin-sdk-契約ctip-sdk)、`backend/ctip-sdk/`

## 背景

v1.1 把 `IocType` / `Tlp` / `Severity` 放在 `ctip-core`,同時要求 SDK 的
`ThreatSourceAdapter` 簽章使用這些型別。依賴方向是 `core → sdk`,
所以 SDK 看不到它們——**照字面實作編譯不過**。

## 決策

`ctip-sdk` 是 **Shared Kernel**,不只是「介面集」。
`IocType`、`IocHashType`、`FingerprintAlgorithm`、`Tlp`、`Severity`、`Confidence`、
`RedistributionPolicy` 全部**由 core 下移到 sdk**,由 `ctip-core`(平台核心)與
`ctip-adapters`(含第三方 adapter)共同依賴、共同演化。

**約束(ArchUnit 驗證)**:零 `org.springframework.*`、零 JPA/Hibernate 型別、
僅依賴 JDK + `jakarta.validation-api`、必須可獨立發布至 Maven Central(DoD **M3-21**)。

## 理由

1. **另一個選項更糟**:讓 SDK 自建一套 wire 型別再轉換,會產生成員完全相同的重複列舉,
   違反抽象判準([01 §1.7](../../spec/01-architecture.md#17-抽象判準強制)),
   而且兩套列舉的偏移會是靜默的語意錯誤(某個 TLP 值對不上)。
2. **Shared Kernel 是有代價的模式,這裡的代價是可接受的**:它要求兩邊接受共同演化。
   本專案只有一個 Bounded Context([02 §2.0](../../spec/02-ddd-model.md#20-為何是單一-bounded-context)),
   兩邊的「兩方」其實是同一個團隊 + 第三方 adapter 作者,而後者要的正是這些型別的穩定定義。
3. **`ctip-adapters` 不依賴 `ctip-core` 是刻意的**:adapter 只認識 SDK 契約,
   這保證第三方 adapter 與內建 adapter 走同一條路。若 adapter 能看到 core,
   內建 adapter 會不知不覺用上 core 的型別,而第三方複製不出來——SDK 的賣點就沒了。

## 演化規則(強制)

- **破壞性變更**(移除列舉成員、變更 record 欄位)= major 版本變更,**必須寫 ADR**
- **新增列舉成員** = minor 變更,但所有 `switch` 必須 exhaustive
  (Java 25 的 pattern matching 在編譯期強制)
- SDK 內不得出現 Spring:`AdapterRegistry` 因此留在 `ctip-app`,
  core 端以 `AdapterRegistryPort` 反轉依賴([ADR 0003](0003-phase5-sdk-adapter-decisions.md))

## 後果

- 第三方只需 `implements ThreatSourceAdapter` 並依賴一個 jar;
  可編譯的範例見 `backend/ctip-sdk/src/test/java/com/ctip/sdk/example/`
  與 [`docs/development/plugin-sdk.md`](../../development/plugin-sdk.md)(DoD **M3-22**)
- SDK 的公開介面等於對外承諾,任何調整都要先問「第三方的 adapter 會不會編不過」
