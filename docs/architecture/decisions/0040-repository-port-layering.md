# ADR 0040 — Repository port 分層

- 狀態:accepted(2026-08-30,Phase 23 補記;決策見
  [00 §0.6](../../spec/00-master.md#修正的規格衝突10-項) 衝突 #2,實作自 Phase 4 起)
- 範圍:[01 §1.6](../../spec/01-architecture.md#16-repository-分層強制)、
  [§1.2](../../spec/01-architecture.md#12-分層與依賴方向強制)、
  `ctip-core/application/port/*`、`ctip-app/infrastructure/persistence/*`

## 背景

v1.1 的 §35.1 寫「使用 Spring Data 提供的即可,不要再自己包一層」。
**字面上這句話連 application 層的 port 都禁止了**,而照字面做的結果是
`ctip-core` 必須直接使用 `JpaRepository` 與 JPA entity ——
把 spring-data 與 Hibernate 拖進業務規則所在的模組,違反
[01 §1.2](../../spec/01-architecture.md#12-分層與依賴方向強制) 的依賴方向。

## 決策

**恰好三層,不多不少:**

```text
application/port/IndicatorRepository                     介面,僅使用 domain 型別
        ▲
        │ implements
infrastructure/persistence/IndicatorRepositoryAdapter    @Repository
        │ delegates to
        ▼
infrastructure/persistence/IndicatorJpaRepository        package-private
        extends JpaRepository<IndicatorEntity, UUID>
```

- `IndicatorJpaRepository` 是 **package-private**:只有同 package 的 adapter 看得到它
- adapter 負責 domain model ↔ JPA entity 的映射(MapStruct)
- **禁止**在 Spring Data 之上再疊第二層自訂抽象:
  不得有 generic repository、不得自寫 query DSL、不得有 `AbstractRepository`
- **禁止**讓 controller 或 domain 物件持有任何 repository

## 理由

1. **port 存在的理由不是「未來可能換資料庫」,而是依賴方向**。
   `ctip-core` 不得 import JPA/spring-data([01 §1.2](../../spec/01-architecture.md#12-分層與依賴方向強制));
   port 是讓業務規則能表達「我需要查一個 Indicator」而不認識 Hibernate 的**唯一**方式。
   這一層通過抽象判準([01 §1.7](../../spec/01-architecture.md#17-抽象判準強制)):
   移除它之後,程式不是「只剩一種實作」,而是**編不過**。
2. **第四層才是 v1.1 真正想禁止的東西**。generic repository / 自寫 DSL / `AbstractRepository`
   確實只有一種實作,而且會把 Spring Data 已經解決的問題重做一遍。禁令保留,只是位置改對了。
3. **port 的回傳型別必須是 domain 型別**:因此
   `application` **不得** import `org.springframework.data.domain.*`(含 `Page`、`Pageable`、`Sort`),
   分頁一律用 core 自有的 `CursorPage`(衝突 #3)。
   `Page` 需要 COUNT query,而 cursor 分頁刻意不做總數。
4. **package-private 是有作用的**:它讓「繞過 adapter 直接用 JpaRepository」在編譯期就不可能,
   而不是靠 code review 或 ArchUnit 事後抓。

## 後果

- 每個聚合各有一組 port + adapter + JPA repository;
  兩模型的表([01 §1.5](../../spec/01-architecture.md#15-三模型-vs-兩模型強制分類))不走這條路,
  它們的 JPA entity 可直接映射為 DTO
- ArchUnit 規則([01 §1.9](../../spec/01-architecture.md#19-archunit-規則強制共-11-條))
  同時驗依賴方向與「controller 不得持有 repository」
- 新增查詢時的正確作法是**在既有 port 加一個方法**,不是新增一層抽象
