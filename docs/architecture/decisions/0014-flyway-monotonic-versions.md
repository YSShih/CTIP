# ADR 0014 — Flyway 版本號改為依實作順序遞增,廢除區段預留

- **狀態**:accepted
- **日期**:2026-08-28
- **範圍**:`04 §4.7`、`05 §5.9`、phase-14/15/18/20/21 的 Flyway 版本號;`migrate.sh` 與 parent pom
- **背景**:Phase 13 收尾稽核([ADR 0013](0013-phase13-audit-fixes.md))記錄了一個留給 Phase 14 的地雷。
  使用者指示現在先處理掉。

---

## 問題

`04 §4.7` 原本依「表的分組」預留版本號區段:`V1–V19` = M1、`V20–V29` = M2、`V30+` = M3,
並在區段內依**表的歸屬**指派號碼(plans = V22/V23、threats = V25、bloom = V26)。

但 Flyway 是**依版本號排序套用**,而 phase 的實作順序與表的分組無關:

| 實作順序 | Phase | 原指派版本號 |
|---|---|---|
| 已完成 | 13 | `V20`、`V21`、`V24`、`V27` |
| 下一個 | 14 | `V22`、`V23` ← **比已套用的低** |
| 之後 | 15 | `V26` ← **比已套用的低** |
| 之後 | 18 | `V25` ← **比已套用的低** |

預留區段與 Flyway 的排序語意天生衝突。這在 Phase 13 用掉 `V24` 時就已經產生,
`V27` 只是讓它更明顯。

### 實測的失敗模式

以 Testcontainers 建立乾淨資料庫,套用 `V20`/`V24`/`V27` 後再加入一個 `V22`:

```
PROBE first migrate applied=3
PROBE RESULT: FlywayValidateException: Validate failed: Migrations have failed validation
PROBE applied versions = [20, 24, 27]          ← V22 沒有被套用
```

Spring Boot 預設 `validateOnMigrate=true`、`outOfOrder=false`,因此這是**應用啟動直接失敗**,
不是靜默漏套 —— 損害有限但完全阻斷。

同一探針驗證 `outOfOrder(true)` 可讓它套用,但順序變成 `[20, 24, 27, 22]`:

```
PROBE outOfOrder migrate applied=1
PROBE final versions = [20, 24, 27, 22]
```

**影響範圍**:只有本機開發用的持久化資料庫。整合測試走 Testcontainers,每次都是全新資料庫、
一次套用完畢,永遠不會踩到;staging/prod 是 M3 交付物,尚不存在。

## 決策

**廢除區段預留,版本號一律遞增、依實作順序指派。** 未寫的 migration 重新編號:

| Phase | 原 | 新 |
|---|---|---|
| 14 | `V22__create_plans` / `V23__seed_plans` | **`V28`** / **`V29`** |
| 15 | `V26__create_bloom` | **`V30`** |
| 18 | `V25__create_threats` | **`V31`** |
| 20 | `V30__create_notifications` | **`V32`** |
| 21 | `V31__create_audit_logs` | **`V33`** |

**已套用的 `V1`–`V7`、`V20`、`V21`、`V24`、`V27` 一律不動** —— 改動已套用的 migration 會使
checksum 失效(`05 §5.9` 明文)。副作用是 `V7__create_stix.sql` 的註解仍寫「`V25__create_threats.sql`」、
`V20__create_users_and_rbac.sql` 的註解仍寫「§4.7 版本區段 V20–V29 = M2」:
**這兩處刻意保持過時**,以 `04 §4.7` 為準。`V8`–`V19`、`V22`、`V23`、`V25`、`V26` 這些號碼
永遠不會有檔案,是舊區段設計的殘留。

### 驗證

在一個已套用 `V1`–`V27` 的資料庫上放入 `V28`,不開任何 flag:

```
1,2,3,4,5,6,7,20,21,24,27,28
```

乾淨套用。全新資料庫與既有資料庫的套用順序自此永遠一致。

### 為什麼不用 `spring.flyway.out-of-order=true`

一行設定就能解決,而且對目前規劃的 migration 功能上是安全的(彼此無依賴)。但它:

1. 讓**全新資料庫 `[20,22,24,27]` 與既有資料庫 `[20,24,27,22]` 的套用順序永久不一致** ——
   任何順序敏感的 migration 都會在兩種環境產生不同結果,而這正是
   「絕不修改已套用的 migration」這條規則想守住的東西。
2. 永久關掉一個安全網:以後真正寫錯順序的 migration 也不會被擋下來。

重新編號的成本此刻是**純文件修改**(那些 migration 一個都還沒寫),往後只會更貴。

### 為什麼不「Phase 14 時重建本機資料庫」

種子資料可重現、成本確實低,但同一個坑會在 Phase 14、15、18 各踩一次,
而 `FlywayValidateException` 的訊息不會告訴你要重建資料庫。

## 順帶修正的兩項既存缺陷

### 1. `04` 內文與 §4.7 自相矛盾

表 17 的內文寫「種子資料(`V22__seed_plans.sql`)」,但 §4.7 的表寫 `V23__seed_plans.sql`。
兩處統一為 **`V29__seed_plans.sql`**。

### 2. `migrate.sh` 從來沒有真的能跑

`05 §5.10` 的 `migrate.sh` 呼叫 `mvnw -pl ctip-app flyway:migrate`,但專案**從未加入
flyway-maven-plugin**(Phase 2 的交接註記就寫了「需要 Phase 3 加」,一直沒補),
實際執行會以「No plugin found for prefix 'flyway'」失敗。

**修正**:plugin 宣告在 **parent** pom 的 `<build><plugins>`(無 `<executions>`,不綁任何
lifecycle phase,只有明確呼叫 `flyway:*` 才執行),`locations` 直指
`ctip-app/src/main/resources/db/migration`;`migrate.sh` 改用 `mvnw -N`(非遞迴)。

這樣繞開了兩個問題:單獨 `-pl ctip-app` 會因 sibling SNAPSHOT 未安裝而無法解析相依;
而 `-pl ctip-app -am` 會讓 `flyway:migrate` 在 reactor 的**每個** module 上執行(含沒有設定的 parent)。

版本沿用 `spring-boot-starter-parent` 納管的 `${flyway.version}`(12.4.0)與
`${postgresql.version}`(42.7.11),**不新增任何版本 property**,故不觸犯規則 6。
plugin 的 `<dependencies>` 不吃 `dependencyManagement`,必須明寫版本,故以那兩個既有 property 表達。

> **依規則 17 回報**:`06 §6.2` 版本表沒有列 `flyway-maven-plugin`。本次未新增版本 property,
> 建議版本表補列一列「Flyway Maven Plugin — 隨 Spring Boot BOM」。

**驗證**:`./environment/scripts/migrate.sh mvp` 對真實 mvp 資料庫執行成功(已是最新,為 no-op);
對全新資料庫執行則套用 `V1`–`V27` 全部 11 支。
