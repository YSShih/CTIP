# ADR 0054 — Dependabot 改為月更(而非關閉),與三支 patch 升版的處置

- 狀態:accepted(2026-09-01,使用者問「dependabot 可以幫我關掉嗎?」,在四個選項中選了「只降噪:weekly → monthly」)
- 範圍:`.github/dependabot.yml`、[06 §6.2 查證狀態段](../../spec/06-tech-stack.md#62-版本表)、`README.md`
- 前一則:[ADR 0053](0053-parallel-forks-and-readme-quickstart.md)

---

## 1. 使用者要的是「關掉」,為什麼結果是「改月更」

使用者的原話是「dependabot 可以幫我關掉嗎?」。**關得掉,但會撞到強制規格**,
所以先把代價攤開再讓使用者選:

[06 §6.1.3 規則 3](../../spec/06-tech-stack.md#613-其他規則)在**強制章節**裡寫著
「啟用 Dependabot 或 Renovate:patch/minor 自動開 PR、major 需人工審核」。
完全關掉版本更新 = 違反這條 = 必須改規格並寫 ADR;
若連 alerts 一起關,還會再違反 [§6.2.3b](../../spec/06-tech-stack.md)。

**但「頻率」不在規格管轄範圍內。** 這點是查證出來的,不是推測:

```
$ grep -rn "weekly\|每週\|每周" docs/spec/*.md docs/development/*.md README.md
docs/spec/05-environment.md:371:AUDIT_CLEANUP_CRON  # 預設 0 0 1 * * SUN(08 §8.7,每週日 01:00)
docs/spec/08-ingestion-sdk.md:308:| 稽核保留清理 | M3 | 每週日 01:00 | AUDIT_CLEANUP_CRON |
```

唯二的命中都是稽核清理的排程,與 Dependabot 無關。
**規格要求的是「有這個機制」,沒有要求它多常跑。**

因此 `weekly → monthly` 能達成使用者要的降噪目的(PR 頻率降為 1/4),
而**不動任何規格條文**。這是四個選項中唯一不需要修改強制章節的。

### 代價(刻意接受,不假裝沒有)

**pin 內的 patch 安全修補最多會晚一個月被提出來。** 這不是零成本。
承受這個延遲的理由是:相依弱點的**即時**訊號本來就不該靠版本更新 PR ——
`security.yml` 的 Trivy fs 掃描是每次 push / PR 都跑的,它才是會擋門的那道。
Dependabot 的 `updates:` 只是「例行保養」,月更的粒度對它是夠的。

⚠️ 但這個理由**有一個前提沒有成立**,見 §4.3。

---

## 2. 三支 PR 的處置

使用者於 2026-09-01 06:31 UTC 在 GitHub web 合併(依 `.github/dependabot.yml` 開頭
與 [06 §6.1.2](../../spec/06-tech-stack.md#612-凍結與浮動強制):**合併一律由人決定**,
本輪 AI 只做查證、驗證與文件同步,沒有也不能執行合併 —— 本機 `gh` 未登入、
SSH 對 GitHub 是 `Permission denied (publickey)`)。

| PR | 升版 | 版本表 pin | 在 pin 內? |
|---|---|---|---|
| [#16](https://github.com/YSShih/CTIP/pull/16) | `com.tngtech.archunit:archunit-junit5` 1.4.1 → **1.4.2** | ArchUnit `1.4.x` | ✅ |
| [#17](https://github.com/YSShih/CTIP/pull/17) | `@testing-library/react` 16.3.2 → **16.3.3** | `16.x` | ✅ |
| [#18](https://github.com/YSShih/CTIP/pull/18) | `@tanstack/react-query` 5.102.7 → **5.102.8** | `5.102.x` | ✅ |

**三支都是 patch 且都落在版本表既有的 pin 內**,因此:

- 版本表的 pin 欄**不需要變動**(與 [ADR 0050](0050-version-table-catchup.md) 那次不同 ——
  那次 #15 是跨出 5.101.x 的 **minor**,所以才要追認)
- [06 §6.1.2](../../spec/06-tech-stack.md#612-凍結與浮動強制)只要求 **major** 升版寫 ADR,
  patch 不需要。本 ADR 之所以存在,是為了記錄**頻率決定**,順帶把這三支的查證結論留檔

唯一要同步的是 §6.2 開頭「查證狀態」段裡逐字列出的 `TanStack Query 5.102.7` → `5.102.8`。
`git log -L 61,61:docs/spec/06-tech-stack.md` 顯示 ADR 0050 曾把這行由 `5.101.4` 改成 `5.102.7`
—— **這行是跟著現況維護的,不是 pin 日的歷史快照**,所以要跟。

### 弱點結論:三支都不修補任何弱點

[ADR 0050](0050-version-table-catchup.md) 立下的方法是掃 PR body 的 `CVE-[0-9]{4}-[0-9]+`
(因為 body 裡的 `security` 字樣是 Dependabot 相容性徽章的連結,會讓每一支都命中關鍵字)。
三支 body 各掃過,**0 命中**。

本輪再往上游多走兩步,把結論釘死:

1. **上游 release notes** —— `TNG/ArchUnit@v1.4.2`、
   `testing-library/react-testing-library@v16.3.3` 的 release body 掃 CVE 與 security/vulnerab* 字樣,
   皆 0 命中(後者的內容是 `Avoid act() re-entrant when dispatching events`)
2. **GitHub Advisory Database** —— 對三個套件查 `GET /advisories`:

   | 生態 | 套件 | advisories |
   |---|---|---|
   | npm | `@tanstack/react-query` | **0** |
   | npm | `@testing-library/react` | **0** |
   | maven | `com.tngtech.archunit:archunit-junit5` | **0** |

   這三個套件**在 Advisory DB 裡從來沒有過任何 advisory**(不分版本)。
   比「這一版沒修 CVE」更強:**它們沒有 CVE 可修。**

> `@tanstack/react-query` 查不到 `v5.102.8` 的 tag —— TanStack 的 monorepo 用
> 日期式 release(`release-2026-08-28-2343`),不是逐套件 tag。改以 Advisory DB 作結論來源。

---

## 3. `.github/dependabot.yml` 的變更

| 變更 | 內容 |
|---|---|
| 四個 ecosystem 的 `schedule.interval` | `weekly` → `monthly` |
| 開頭註解 | 補上頻率決定與其依據;**更正 alerts 的錯誤陳述**(見 §4.3) |
| `ignore` 條目 | **一律不動** |

`ignore` 不動的理由:那些條目是版本表 pin 的另一種表達
([ADR 0049 §3.2](0049-base-image-vulnerability-remediation.md)、
[ADR 0050](0050-version-table-catchup.md)),與更新頻率是正交的兩件事。
把降噪做在 `ignore` 上會讓「版本表 pin」與「不想看到 PR」混為一談,
下一個讀這個檔的人會分不出哪條是規格、哪條是偏好。

---

## 4. 順帶抓到的三個既有缺口(依規則 17 回報)

這三個都是**查證過程中撞到的**,與本輪變更無關,**本輪只記錄與更正陳述,不改規格**。

### 4.1 `06 §6.2.3b` 指向一個不存在的章節

[06 §6.2.3b](../../spec/06-tech-stack.md) 的表格寫「二擇一即可(**13 §13.9**)」,
但 `13-platform-ops.md` 的章節止於 `## 13.8 CI/CD`:

```
$ grep -c "13\.9" docs/spec/13-platform-ops.md
0
```

(`.github/dependabot.yml` 開頭引用的是 §13.8,**那個是存在的**,不受影響。)

### 4.2 `docs/development/version-audit.md` 把 ArchUnit 的實際 pin 記錯了

該檔第 19 行寫 ArchUnit 實際 pin 是「**1.4.2(Phase 4 引入)**」,但:

```
$ git log --oneline -S "archunit.version" -- backend/pom.xml
daf2a9a Phase 4: domain aggregates + minimal security layer
```

`<archunit.version>` 自 Phase 4 引入以來**只被寫過一次,值是 1.4.1**,從來沒有是 1.4.2 過。

**合併 #16 之後這一行會「碰巧變成正確的」** —— 而這正是要把它寫下來的理由:
一個錯誤的記載被後續事件意外對上,如果不記錄,它看起來就跟一直正確一樣,
下次再錯就沒有任何痕跡可循。同一形狀的問題見
[ADR 0045](0045-full-project-review-doc-sync.md)(宣告了但不存在的驗證)。

**本輪破例改了這一行**(原計畫是三個缺口都只記錄不修)。理由:值雖然碰巧對了,
但括號裡的出處「Phase 4 引入」是**確定為假**的陳述,留著等於留一句已知的錯誤。
改動只是把出處換成 git log 查得到的事實,不觸及任何 pin 或判斷。

### 4.3 Dependabot alerts 仍未開啟 —— 這讓 §1 的「代價可接受」少了一半理由

[ADR 0050](0050-version-table-catchup.md) 已查證 alerts 是關閉的
(`gh api repos/YSShih/CTIP/dependabot/alerts` → 403
「Dependabot alerts are disabled for this repository」),至今未見開啟的回報。

**本輪無法自行複查**:該端點需要認證,未持 token 時一律回 401,
分不出「關閉」與「沒權限」。因此本 ADR 不宣稱它今天仍是關的,
只陳述「2026-08-30 最後一次查證是關的,此後無人回報變更」。

⚠️ **這與本 ADR 的決定直接相關。** §1 接受「patch 安全修補晚一個月」的理由是
「即時訊號由別的機制提供」。那個「別的機制」在規格裡是
[§6.2.3b](../../spec/06-tech-stack.md) 選的 **Dependabot alerts** —— 而它沒開。
實際還在運作的只剩 `security.yml` 的 Trivy fs 掃描(目前綠)。

也就是說:**改月更本身沒有製造新風險,但它踩在一個原本就已經比規格宣告的還薄的防線上。**
`.github/dependabot.yml` 開頭原本寫著「alerts 於 repo 設定啟用」——**那句話是錯的**,本輪一併更正。

> **要做的事(需使用者操作,AI 改不到 repo 設定)**:
> GitHub → Settings → Code security → 開啟 **Dependabot alerts**。
> 與 [15 §15.5](../../spec/15-dod-gates.md#155-需人工確認未被自動驗證) 的 **P-07** 同性質。

---

## 5. 驗證

| 檢查 | 結果 |
|---|---|
| 遠端 main `6e77f40` 的 CI | **14/14 全綠**(以 GitHub API `check-runs` 查證) |
| 合併內容落地 | `backend/pom.xml:32` = `1.4.2`、`frontend/package.json` = `~5.102.8` / `^16.3.3` |
| 後端 `clean verify -Ptest-integration` | 見 `docs/progress.md` 本輪紀錄 |
| 前端 `lint` / `format:check` / `build` / `test` / `api:check` | 見 `docs/progress.md` 本輪紀錄 |
| `grep -c weekly .github/dependabot.yml` | `0` |
