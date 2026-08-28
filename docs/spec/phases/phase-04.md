# Phase 4 — Domain：Indicator / Tenant / Source / TLP ＋最小安全層  `[M1]`

## 前置條件
- Phase 3 完成判準全綠

## 交付物

### `ctip-sdk`（Shared Kernel）
- `IocType`、`IocHashType`、`FingerprintAlgorithm`、`Tlp`、`Severity`、`Confidence`、`RedistributionPolicy`、`SourceType`
- `Tlp.strictest(a, b)`、`Severity.max(a, b)`

### `ctip-core/domain`
- `Indicator` 聚合（14 條不變量）+ `IndicatorSource` + `HashRecord` + `IndicatorMergePolicy` 骨架
- `Tenant` 聚合（4 條）、`Source` 聚合（6 條）
- 值物件：`IocValue`、`Fingerprint`、`ValidityPeriod`、`Reputation`、`TenantSlug`、`Cursor`、`CursorPage`
- `ClockPort`、`IdGeneratorPort`
- `DomainEvent` 型別 + M1 的 11 個事件

### `ctip-core/application/port`
- `IndicatorRepository`、`SourceRepository`、`TenantRepository`、`EventPublisherPort`、`SearchPort`、`RateLimiterPort`

### `ctip-app/infrastructure`
- JPA entity（9 張表）+ `*RepositoryAdapter` + package-private `*JpaRepository` + MapStruct mapper
- **最小安全層**（[01 §1.11](../01-architecture.md#111-m1-最小安全層強制phase-4)）：`TenantContext`、`AnonymousTenantFilter`、`TlpSpecifications`、`AuthState`

### 測試
- 每一條聚合不變量各一個 L1 測試
- `SecurityTest`（安全測試 1、2、3、9）

## 治理規格
- [02-ddd-model.md](../02-ddd-model.md)（聚合與不變量、詞彙表）
- [03-diagrams.md §3.2](../03-diagrams.md#32-聚合圖)（聚合圖）
- [07-domain-intel.md §7.1、§7.7、§7.9](../07-domain-intel.md)（型別、TLP、再散布）
- [10-identity-plans.md §10.1](../10-identity-plans.md#101-多租戶)（租戶隔離）
- [01-architecture.md §1.5、§1.6、§1.11](../01-architecture.md)

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml verify -Ptest-integration
./backend/mvnw -f backend/pom.xml test -Dtest=ArchitectureTest     # 9 條規則
./backend/mvnw -f backend/pom.xml test -Dtest=SecurityTest
# domain 覆蓋率 >= 85%（JaCoCo check 已綁在 verify）
```

## 不得做的事
- domain 不得 import Spring / JPA / Jackson（ArchUnit 規則 1 會擋）
- domain 不得呼叫 `Instant.now()` / `UUID.randomUUID()`（規則 9）
- `ctip-sdk` 不得出現 Spring（規則 2）
- `application` 不得 import `org.springframework.data.domain`（規則 8）
- 不得把 `tenant_id` 過濾寫成單一 tenant（必須 `IN (current, public)`）
- 不得實作 Threat、User、Bloom（M2）
