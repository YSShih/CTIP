# ADR 0045 — 全專案複查:規格計數同步與 ERD 自動驗證補實

- 狀態:accepted(2026-08-30,使用者指示「逐一 review 整個專案程式碼、確認程式與規格一致、規格或 README 有需要更正的就更正」)
- 範圍:`docs/spec/{00,02,03,04,05,09,README}.md`、`README.md`、新增 `DataDictionaryConsistencyTest`
- 前一則:[ADR 0044](0044-security-findings-remediation.md)

---

## 背景與方法

Phase 23 收尾、M3 閘門實跑與弱點處置之後,對**全部產出**做一次不分 phase 的橫向複查。
複查分三軸:

1. **程式是否與規格一致** —— 機械比對能對的全對:Flyway 建的表 vs 04、`docs/api/openapi.json`
   vs 09 §9.1、RBAC 種子 vs 10 §10.3、`AuditAction` vs 13 §13.5、TLP 2.0 marking UUID vs 07 §7.8.4、
   Bloom 位元布局 vs 11 §11.4、ArchUnit 規則數 vs 01 §1.9、DoD 項數 vs 15。
2. **建置實測** —— 後端 `clean verify -Ptest-integration`(Spotless／Checkstyle／ArchUnit／JaCoCo 門檻皆綁在 `verify`)、
   前端 `lint` / `build` / `test` / `api:check`。
3. **規格與 README 本身是否還說得對**。

**軸 1、軸 2 全數通過**:後端 1,128 tests 全綠、前端 186 tests 全綠、lint 0 warning、
OpenAPI 型別無漂移;程式沒有多做、也沒有少做規格要求的東西。
09 §9.1 列的 54 個端點與 `openapi.json` 的 53 個 HTTP operation **完全對得上**
(差的那一個是 `GET /api/v1/ws`,WebSocket 升級不是 OpenAPI operation)。

**問題全部集中在軸 3**,而且是同一個形狀:**規格正文正確,人工維護的「計數」與「清單」沒跟上。**

---

## 1. 根因:一張標了「自動驗證」但驗證不存在的圖

[03 §3.3](../../spec/03-diagrams.md#33-erd) 的 ERD 標為 **🔴 規範·自動驗證**,內文寫
「由 CI 比對 Flyway migration 產生的實際 schema 與 04-data-dictionary.md」。

**那個比對從來不存在。**`dod.sh` 與 11 支 workflow 都沒有任何一步做這件事。

後果具體可見:Phase 14 新增的 `import_jobs`([ADR 0019](0019-phase14-16-spec-resolutions.md))
只進了 04 §4.3 的欄位定義(編號 18b)與 §4.7 的 `V28` 對應,而

- 04 §4.1 的**表清單**沒有它(仍是 27 列)
- 04 §4.1 的註記與檔尾計數仍寫「27 張表」
- 03 §3.3 的 **ERD 沒畫它**,底下還寫著「全部 27 張表皆已畫入」
- 00 §0.2／§0.3、`docs/spec/README.md` 也仍是 27

**連續九個 phase(14→23)沒有任何檢查變紅。** 這與
[ADR 0016](0016-phase1-13-spec-backfill.md) 第 3 項(`15 §15.5` 寫了「必須實作」卻沒人實作)
是同一類缺口:**規格宣告了自動化,自動化卻不存在**——而宣告本身讓人以為有守門,反而比明說「這裡沒有守門」更危險。

### 處置:把那個宣告變成真的

新增 `backend/ctip-app/src/test/java/com/ctip/DataDictionaryConsistencyTest.java`(L3,`@Tag("integration")`),
三項斷言,全部**雙向**比對(少一張=規格漏登、多一張=建了表沒寫規格,兩者都紅):

| 斷言 | 比對 |
|---|---|
| `dataDictionaryTableListMatchesActualSchema` | 04 §4.1 表清單 ↔ `pg_tables` |
| `erdCoversEveryTable` | 03 §3.3 ERD 實體 ↔ `pg_tables` |
| `declaredTableCountMatchesActualSchema` | 04 檔尾「表數：n」↔ `pg_tables` 的張數 |

`MigrationIntegrationTest` 既有的斷言是 `contains(...)`——只驗「表在不在」,
驗不到「規格是不是也知道」。這正是它抓不到本次缺口的原因。

**否定驗證**(依 ADR 0016 第 1 項的前例,綠的測試必須先證明它會紅):
把 `import_jobs` 從 §4.1 與 ERD 移除、檔尾改回 27 —— **三項全部轉紅**;還原後全綠。
CI 端由 `backend-test.yml` 的 `clean verify -Ptest-integration` 涵蓋。

**不新增 DoD 項目**:`15` 的「90 項」是契約(同 [ADR 0041](0041-phase23-cicd-security-docs.md) §6
對 M3-19 的處理——併入既有項而不新增第 26 項)。本測試隨 `verify` 執行,已被 M3-01 的巢狀 gate 涵蓋。

---

## 2. 規格內的計數同步(正文皆正確,只有計數過時)

| 位置 | 原 | 現 | 為什麼會漂 |
|---|---|---|---|
| 04 §4.1 清單 / 註記 / 檔尾、00 §0.2、00 §0.3、`spec/README`、`README` | 27 張表 | **28** | 見第 1 節 |
| 03 §3.3 ERD | 缺 `IMPORT_JOBS` | 已補三條關聯 | 同上 |
| 09 檔尾 | 端點數 43 | **54**(53 HTTP + `/ws`) | 校對停在 Phase 16;Phase 18 的 Threat 寫入五支、Phase 20 的通知／SSE／WebSocket、Phase 21 的稽核與 GDPR 端點都進了 §9.1 清單,只有這一行沒改 |
| 02 檔尾 | 19 個 domain event | **21** | §2.4 原 19 列;Phase 18 補 `IndicatorTlpTightened` 成 20 列,而 `ApiKeyCreated`／`ApiKeyRevoked` 同列是兩個型別。程式碼的 `domain/event` 恰為 21 個 record,與 §2.4 的表一致 |
| `spec/README` 索引表 | 9 條 ArchUnit / 19 event / 27 表 / 10 stage / 12 排程 / 47 端點 / 15 頁面 / 6 項人工確認 | **11 / 21 / 28 / 12 / 14 / 54 / 16 列(18 路由) / 7** | 該表自 v2.0 起未再校對;各主題檔正文都已隨 phase 更新過。「10 個 pipeline stage」原是 **M1** 的數量——`BloomUpdate`／`SearchIndex` 於 M2 裝配後為 12;§8.2 的圖本來就畫 12 個,`IngestionPipelineConfig` 的 `List.of(...)` 也是 12 個且順序完全一致 |
| `spec/README` 行數欄 | 多處低估(13 少 44%、09 少 33%) | 依實際重算 | 該欄的用途是**估 context 成本**,低估等於誤導讀取決策,正是 v2.0 拆檔要解的問題 |
| 05 §5.1 結構契約樹 | 8 支腳本 | 補 `openapi-breaking-check.py` | Phase 10 建立([ADR 0007](0007-phase10-openapi-decisions.md) §2)、`openapi-check.yml` 在用、`environment/README.md` 有寫,只有**強制結構契約**的樹漏了——照字面讀,樹上沒有的檔案是「不該存在」 |

`00 §0.6` 的「27 張表中 19 張無欄位定義」**刻意不改**:那是描述 v1.1 當時的狀態,是歷史陳述。

---

## 3. README 的現況同步

| 位置 | 原 | 現 |
|---|---|---|
| 「這是什麼」與「現況/規格書」 | 二十四輪、§0.7–§0.30 | **二十五輪、§0.7–§0.31**(§0.31 是 M3 閘門實跑那一輪) |
| 架構決策 | ADR 0001–0042 | **0001–0045** |
| `environment/` | 8 支腳本 | **9 支**(8 shell + 1 python) |
| M3 閘門 | 停在閘門當天:`openapi-check` 0/29、四組 HIGH「不修」 | 補上其後:`openapi-check` **第 30 次 run 在 CI 實測轉綠**;四組 HIGH 中三個 CVE 已由 Boot 4.1.0→4.1.1 解掉([ADR 0044](0044-security-findings-remediation.md)),剩兩組在基底映像、本 repo 無動作可做;仍待完整重跑與 host 裝 `gh` |

---

## 4. 查過但**確認無誤**的項目(記下來,避免下次重查)

- **RBAC**:24 個權限、矩陣 120 格 —— `V24`/`V27`/`V29`/`V31`/`V32` 種子、`RbacMatrix` 常數、
  §10.3 的表三者一致。`RbacMatrix.parseSpecificationTable()` **直接 parse 規格的 markdown 表**,
  種子與規格任一漂移即紅——這是本專案最強的一道守門,也是為什麼權限沒有出現本次這類漂移。
- **STIX TLP 2.0 的五個 marking UUID**:與 OASIS 官方值逐字相符。
- **Bloom 位元布局**:LSB-first、尾端位元遮罩、`Math.floorMod` 對應規格的
  `((h1 + i·h2) mod m + m) mod m` 與 64-bit wraparound,皆符 §11.4。
- **`AuditAction`**:26 個列舉值 = `V33` 的 CHECK 清單 = §13.5 觸發點對照表。
- **保留任務**:5 支 SQL(`RetentionTasks`)+ Bloom artifact 份數清理 = §13.4 的六項。
- **排程**:程式的 14 個 cron 任務 = §8.7 的 14 列(另有一個 `fixedDelay` 的可觀測性指標刷新,不屬 §8.7)。
- **DoD 項數**:M1 38 + M2 27 + M3 25(`M3-01…24` 加 `M3-11b`)= 90,與 `dod.sh` 一致。
- **不得 commit secret**:`environment/.env.{mvp,staging,prod}` 實檔存在於工作目錄但**未進版控**;
  SBOM(`frontend/sbom.json`、`backend/target/bom.json`)依 §6.2.3b 亦未進版控。
- **無 Lombok、無 `printStackTrace`、無 `new Random()`、無吞例外的 catch、無 `dangerouslySetInnerHTML`**;
  前端兩處外部連結皆有 scheme 白名單 + `rel="noreferrer noopener"`。

---

## 5. 回報:一項已知且刻意不動的殘留

`WebhookTargetGuard` 解析後判定 → `HttpClient` 送出時**會再解析一次 DNS**,兩次之間存在
理論上的 rebinding 窗口。要根治得把已判定的 IP 釘進連線(自訂 `InetAddress` 解析或 socket 層綁定),
那會動到 HTTP client 的裝配方式。現況已是**兩道防線**(建立時擋字面 IP、送出前擋解析結果),
且 JVM 的 DNS 快取讓窗口極窄。**依規則 17 回報,不自行改動**——是否值得為此改 client 裝配是設計取捨,應由使用者定調。
