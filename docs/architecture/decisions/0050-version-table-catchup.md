# ADR 0050 — 版本表追認四支已合併的相依升版

- 狀態:accepted(2026-08-30,使用者在遠端 web 合併 PR 後指示「版本要更新規格,要摘要說明修復什麼弱點」)
- 範圍:[06 §6.2.2／§6.2.3](../../spec/06-tech-stack.md#62-版本表)、`.github/dependabot.yml`
- 前一則:[ADR 0049](0049-base-image-vulnerability-remediation.md)

---

## 背景:順序反過來了

[ADR 0049](0049-base-image-vulnerability-remediation.md) 把 Dependabot PR 從 14 支整理到 4 支,
其中 **#15**(TanStack Query 5.101.4 → 5.102.7)當時是以「**跨出版本表 pin**」為由被關閉的(前身 #8),
理由是 `06 §6.2.3` 把它 pin 在 **5.101.x**,而依 `§6.1.2` 跨出 pin 要**先更新版本表並寫 ADR**。

使用者隨後在遠端 web 直接合併了剩下四支。**既成事實與規格因此不一致** ——
`package.json` 是 `~5.102.7`,版本表寫 5.101.x。

本 ADR 追認,並把規格補齊。**不是把規則放寬**:`§6.1.2`「Coding LLM 不得自行升版」
針對的是 AI,人工核准升版本來就是那條規則預設的出口;缺的只是「更新版本表 + 寫 ADR」這一步。

---

## 這四支各自修了什麼弱點(逐一查證,不推測)

使用者要求摘要說明弱點。**查證結果與直覺相反:四支裡只有一支與安全性相關。**

| PR | 升版 | 弱點 |
|---|---|---|
| **#14** | `com.networknt:json-schema-validator` 1.5.6 → **1.5.9** | ✅ **有** |
| #15 | `@tanstack/react-query` 5.101.4 → 5.102.7 | ❌ 無 |
| #11 | `typescript-eslint` 8.67.0 → 8.68.0 | ❌ 無 |
| #3 | `@types/react-dom` 19.2.4 → 19.2.5 | ❌ 無 |

### #14 —— 唯一與安全性相關的一支

上游 changelog 明載:

> - Bump jackson to 3.1.4 to fix **CVE-2026-54512** and **CVE-2026-54513**(#1263)
> - fixes #1277 upgrade jackson to **3.2.1** to resolve a security vulnerability

⚠️ **但影響範圍僅測試。** `06 §6.2.2` 把本項列為 **test scope**、明載「**不進 runtime classpath**」
——它拉進來的那個 Jackson 不在出貨的 artifact 裡。正式環境的 Jackson 由 Spring Boot BOM 納管
(`4.1.1` 帶的版本),與本項無關。

因此正確的說法是:**#14 修掉的是測試相依鏈上的 Jackson 弱點,不是產品的弱點。**
不能寫成「修掉了 CVE-2026-54512」而不加但書。

### 其餘三支 —— 例行版本更新,未修補任何弱點

三者的 PR body 中確實出現 `security` 字樣,但那是 Dependabot 的**相容性評分徽章連結**
(網址含 `managing-security-vulnerabilities`),不是弱點修復。
逐一以 `CVE-[0-9]{4}-[0-9]+` 掃過三支的 body,**零命中**。

> 這一點特別記下來:「PR 內文出現 security 字樣」不等於「這是安全性更新」。
> Dependabot 的樣板會讓每一支 PR 都命中關鍵字搜尋。

---

## 版本表的變更

| 項目 | 前 | 後 | 備註 |
|---|---|---|---|
| TanStack Query | 5.101.x | **5.102.x** | 例行更新,無 CVE |
| networknt json-schema-validator | 1.5.x | **1.5.9+** | test scope;摘要見上 |
| `typescript-eslint` | *(未列)* | **8.68.x** | **本表原本沒有這一項** |
| `@types/react-dom` | *(未列)* | *(仍不列)* | 型別定義,隨 React 19.2.x;不獨立 pin |

### `typescript-eslint` 為何要補列

`06 §6.2.3` 列了 ESLint 與 `eslint-plugin-import`,卻**沒有 `typescript-eslint`** ——
而 TS 的規則與 parser 實際由它提供。缺列的後果在 #11 出現時就看到了:
**沒有任何判斷依據可以說它該不該升**(ADR 0049 當時只能寫「版本表未列此項」)。

---

## dependabot 必須跟著改

`.github/dependabot.yml` 的 `@tanstack/react-query` `semver-minor` ignore 是
[ADR 0049](0049-base-image-vulnerability-remediation.md) 依**舊版本表(5.101.x)** 寫的。
版本表改成 5.102.x 之後,那條註解已經對不上。

**兩者是同一件事的兩種表達**:版本表說「pin 在哪個 minor」,dependabot 的 ignore 說
「不要提跨出 pin 的 minor」。**版本表一改,ignore 的註解就要同步** ——
不同步的話,下一個讀 dependabot.yml 的人會以為 pin 還在 5.101.x。

---

## 依規則 17 回報:Dependabot alerts 是關閉的

查證過程中發現:

```
$ gh api repos/YSShih/CTIP/dependabot/alerts
{"message":"Dependabot alerts are disabled for this repository.", "status":"403"}
```

而 [06 §6.2.3b](../../spec/06-tech-stack.md) 明載本專案在「OWASP Dependency-Check 或
Dependabot alerts」二擇一中**選了 Dependabot alerts**;
[`docs/development/getting-started.md`](../../development/getting-started.md) 的
「首次啟用 CI 時必做的兩件事」第 2 條就是啟用它。

**也就是說,規格宣告的相依弱點偵測機制目前沒有在運作。**
這與 [ADR 0045](0045-full-project-review-doc-sync.md) 抓到的
「§3.3 的 ERD 標了『自動驗證』但那個驗證不存在」是同一類缺口——**宣告了,但沒有實際存在**。

目前擋 PR 的相依弱點訊號只剩 `security.yml` 的 Trivy fs 掃描(該 job 目前是綠的),
所以不是完全沒有防線,但**與規格宣告的不一致**。

⚠️ 這是 **GitHub repo 設定**(Settings → Code security),版控檔案表達不了、AI 也改不到,
必須由人操作。與 [15 §15.5](../../spec/15-dod-gates.md#155-需人工確認未被自動驗證) 的 **P-07**
(`production` environment 的 required reviewers)性質相同。

---

## 驗證

四支相依升版與 [ADR 0049](0049-base-image-vulnerability-remediation.md) 的 Dockerfile 改動
是**第一次放在一起**(那四支各自的 CI 當時是紅的——`backend-test` 0/19,見
[ADR 0048](0048-ci-green-and-test-isolation.md)),因此 rebase 後重跑:

| 檢查 | 結果 |
|---|---|
| 前端 `npm ci` + `lint` + `format:check` + `build` + `test` + `api:check` | 全綠(**186 tests**) |
| 後端 `clean verify -Ptest-integration` | 見 `docs/progress.md` 本輪紀錄 |
