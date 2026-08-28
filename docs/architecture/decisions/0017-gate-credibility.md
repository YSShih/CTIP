# ADR 0017 — 讓閘門說真話(批 1)

- **狀態**:accepted
- **日期**:2026-08-28
- **範圍**:`environment/scripts/dod.sh`、23 份 phase 執行單、`15 §15.0`、
  `MigrationIntegrationTest`、`RbacMatrixTest` + `support/RbacMatrix`
- **背景**:清障計畫的批 1。後續每一批都要靠這些工具判定「全綠」,先確認它們量得到東西。

---

## 1. `dod.sh` 會對不存在的測試類回報 PASS

`backend/pom.xml` 的 `failIfNoSpecifiedTests=false`(06 §6.3.6 第 4 條)是**必要的**——
判準的 `-Dtest=<類名>` 會對 reactor 每個 module 執行,沒有該測試類的 module 不得因此失敗。

但它的反面代價從未被記錄:**測試類根本不存在時,surefire 跑 0 個測試,build 仍然成功**。
於是尚未實作的 phase 的 DoD 項目會一路 `[PASS]`。

**實測**:Phase 14/15/16 一行程式都沒有的情況下,`dod.sh phase2` 回報 **27/27 全綠**。
M2-10~M2-20 對應的測試類(`BloomGenerationTest`、`QuotaEnforcementTest` 等)全部不存在。

閘門量不到它該量的東西——而這是本輪清障其餘每一批的驗收依據。

**修法**:`MVNT` 由字串前綴改為 `mvn_test` 函式,先在 `backend/**/src/test/java` 下確認
每個測試類檔案存在,找不到就 FAIL 並說明「若該 phase 尚未實作,這一項本來就應該是 FAIL」。

**驗證**:`dod.sh phase2 M2-10` 由 `[PASS]` 變為
`[FAIL] … 找不到測試類: BloomGenerationTest`;`dod.sh mvp M1-03` 仍 `[PASS]`。

## 2. 判準指令的三項修正

| # | 問題 | 修法 |
|---|---|---|
| 2a | **`./mvnw` 不存在**——wrapper 在 `backend/mvnw`,repo 根沒有。23 份執行單都寫 `./mvnw -f backend/pom.xml`,逐字執行必然 `no such file`(Phase 1–13 是由人轉譯後執行才沒踩到) | 全部改 `./backend/mvnw -f backend/pom.xml` |
| 2b | **過濾式判準用 `verify`**,會綁上 JaCoCo `check`;只跑幾個測試類不可能達到 `ctip-core` 的 PACKAGE 門檻(domain 0.85 / application 0.75)。`dod.sh` 一直用 `test`——兩處不一致。Phase 15 的 `BloomBitLayoutTest` 屬 L1、應在 `ctip-core`,會第一個踩到 | 過濾式一律改 `test`;收尾另跑無過濾 `clean verify`(既有慣例) |
| 2c | 上述兩點在 `15 §15.0` 無記載 | 補進 §15.0 的判準契約 |

## 3. `MigrationIntegrationTest` 的兩個地雷

`containsExactly("1",…,"27")` 與「未來 phase 的表不得存在」的封閉清單,會在
Phase 14/15/18/20/21 各紅一次——**而那些都是正常交付,不是缺陷**。

**修法**:
- 版本清單改由 **classpath 上的 `db/migration`** 推導。刻意讀 classpath 而非 `src/main/resources`:
  Flyway 讀的是 classpath,而 `mvn test`(未 clean)不會刪掉 `target/classes` 裡已從原始碼移除的檔案
  ——讀原始碼目錄會讓兩邊看到不同的 migration 集合。(這一點是實作過程中真的踩到才發現的。)
- 「未來表不得存在」改為 **「每一張表都必須由某支 migration 建立」**。規則 16 想守的是
  「不得預先建立無人使用的表」,這個表述不會隨 phase 推進而失效。

> **一個誠實的限制**:本測試**抓不到 ADR 0014 的 out-of-order 危害**。全新資料庫一律照版本序
> 一次套完,所以「順序遞增」在這裡恆真——即使放進一支版本號低於既有最高版的 migration 也一樣
> (已實測:放 `V22` 進去,測試照樣綠)。那個危害只在**既有**資料庫上出現,repo 層的測試結構上看不到。
> 我原本寫了一條 `isSorted` 斷言,發現它恆真後移除——留著等於假的保護。真正的守門是 §4.7 的編號政策。

**驗證**:新增一支 `V28` migration → 測試自動接受(不必改測試);
建立一張沒有 migration 的表 → `everyTableIsCreatedByAMigration` 轉紅(實作過程中真的發生過)。

## 4. RBAC 矩陣的第三份來源納入自動比對

矩陣有**三份人工同步的來源**:規格 §10.3 的表、`V24`/`V27` 種子、測試常數 `RbacMatrix`。
前兩份已由既有測試互綁,**規格表卻只能靠人眼比對**——而規格表才是契約。

**修法**:`RbacMatrix.parseSpecificationTable()` 直接解析 §10.3 的 Markdown 表
(處理 `` `apikey:create` / `apikey:revoke` `` 這種一列兩碼的格式),
新增 `theSpecificationMatrixMatchesTheSeededMatrix` 斷言三者一致。

順帶消滅寫死的 `hasSize(21)`——每加一個權限就要改一處,而規格表才是那個數字的真正來源。

**驗證**:把 §10.3 表中 `audit:read` 的 ANONYMOUS 欄由 `—` 改成 `✓` → 測試轉紅;還原 → 轉綠。

---

## 沒有動的

`EndpointAuthorizationTest` 的封閉白名單看起來也像地雷,但它已經有一條
`allowlistContainsOnlyEndpointsThatReallyHaveNoAnnotation` 守著,且白名單本身要求「新增項目必須
連同理由一起加」——這是刻意的摩擦,不是維護負擔。維持原樣。
