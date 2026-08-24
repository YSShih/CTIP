# ADR 0002 — Phase 4:可見度過濾形式與 domain 設計決策

- **狀態**:accepted
- **日期**:2026-08-24(Phase 4)

## 決策 1:TLP 過濾採「複合條件」而非字面的單一 maxVisibleTlp

### 背景

`01-architecture.md` §1.11 給出的過濾 SQL 是
`owner_tenant_id IN (:current, PUBLIC) AND tlp <= :maxVisibleTlp`(單一上限),
但 `07-domain-intel.md` §7.7 的可見度表要求 AUTHENTICATED =
「public 的 CLEAR+GREEN **加上自家 tenant 的全部 TLP**」。單一上限無法同時表達兩者:
取 GREEN 會遮掉自家的 AMBER/AMBER_STRICT,取 AMBER_STRICT 則 public 分支只能靠
「public tenant 只存 CLEAR/GREEN」的資料不變量兜住——把安全過濾押在資料寫入紀律上。

### 決策

`TlpSpecifications.visibleTo` 實作為複合條件(仍是唯一一套邏輯、唯一一個實作點):

```text
(owner = viewer)                                        ← 自家全部(匿名時 viewer=public,此分支併入下行)
OR (owner = PUBLIC AND tlp <= maxPublicTlp)             ← 匿名 CLEAR;已認證 CLEAR+GREEN
AND (owner = viewer OR EXISTS 非 INTERNAL_ONLY 來源)     ← I14 / 07 §7.9 規則 3 的作用域
AND deleted_at IS NULL
```

依 00-master §0.4 模糊優先序(安全性第一):複合式**精確**實作可見度表,不依賴資料端不變量。

## 決策 2:事件信封欄位由發佈端補齊

§2.4 要求事件必含 eventId/occurredAt/traceId,但 domain 禁止 `Instant.now()`/`UUID.randomUUID()`
(ArchUnit 規則 9),聚合行為簽章(如 `mergeFrom(report, reputation)`)也無處注入。
故 domain 事件 record 只承載領域內容 + `tenantId`;信封欄位由 `EventPublisherPort`
實作(Phase 6)在發佈時以 ClockPort/IdGeneratorPort/MDC 補齊。

## 決策 3:M1 的 11 個事件 = 10 個標記 M1 者 + `IndicatorFalsePositiveReported`

§2.4 表標 M1 的事件只有 10 個,但 phase-04 執行單寫「M1 的 11 個事件」,且
`Indicator.reportFalsePositive` 是 Phase 4 的聚合行為——其對應事件一併定義(表中標 M2
指的是「誤判回報端點」啟用時點,非事件型別本身)。

## 決策 4:ArchUnit 規則 5 的切片粒度為頂層模組

規格自身的結構在子套件粒度必然成環:`domain/event` 的事件引用 `domain/indicator` 的
`IndicatorId`,而 `domain/indicator` 引用 `domain/event` 的 `DomainEvent`(§1.4 即如此設計)。
故規則 5 以 `com.ctip.(*)..`(domain/application/infrastructure/interfaces/sdk/adapters/config)
為切片;這也與 §2.0「模組」語意一致。

## 決策 5:JPA entity 用 package-private 欄位、無 accessor

無 Lombok 且 Checkstyle 限單檔 300 行,`IndicatorEntity`(22 欄)配 getter/setter 必然超標。
entity 僅在 `infrastructure/persistence` 內由 mapper/adapter 存取,故採 package-private
欄位直接存取(JPA field access)。`IndicatorEntity`/`IndicatorSourceEntity` 類別為 public
(security 套件的 TlpSpecifications 需要型別),欄位仍 package-private。
