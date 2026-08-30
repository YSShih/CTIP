# ADR 0048 — M3-19 的「CI 全綠」與 backend-test 的測試順序相依

- 狀態:accepted(2026-08-30,使用者問「host 安裝 gh 不會影響我本地的 git 嗎」、
  「vm 裡有安裝了不行嗎」,續以「我想徹底解決問題」)
- 範圍:`environment/scripts/dod.sh`、`NotificationApiTest`、`IngestionEndToEndTest`、
  [15 §15.3](../../spec/15-dod-gates.md)
- 前一則:[ADR 0047](0047-dod-m1-02-coverage-check.md)

---

## 起點

一個關於 `gh` 裝在哪裡的問題,查證時連帶翻出兩個更嚴重的缺陷。三件事一起處理。

### 先更正一個前提

host 與 VM **不是不同 remote,而是同一個 repo**。`Vagrantfile:55` 把
`/Users/yusen/workspace/java/` 掛成 VM 的 `/home/vagrant/java/`(synced folder),
兩邊是同一份工作目錄、同一個 `.git`,remote 都是 `ctip → git@github.com:YSShih/CTIP.git`。

VM 的 gh 實測可用,但 **VM 缺 docker / node / npm / jq**,`dod.sh full` 的主體跑不了
(docker 是 M1-14、M2-25 與全部 Testcontainers 測試的前提)。gate 只能在 host 跑
→ **host 需要 gh**。

---

## 1. `gh` 裝在 host,且不影響系統其他 git

`brew install gh` 只放一個 binary,**不碰 git**(remote 存在各 repo 的 `.git/config`,gh 只讀不寫)。

**唯一的風險是 `gh auth login` 的那一題**:「Authenticate Git with your GitHub credentials?」
答 yes 會執行 `gh auth setup-git`,在 `~/.gitconfig` 寫入
`credential."https://github.com".helper = !gh auth git-credential`。
它只作用於 **HTTPS 的 github.com URL** —— CTIP 的 remote 是 SSH 不受影響,
但**其他用 HTTPS github remote 的 repo 會改由 gh 供憑證**(目前由
`/Applications/Xcode.app/.../gitconfig` 的 `credential.helper=osxkeychain` 管)。

### 決策:不跑 `gh auth login`,改用 `GH_TOKEN`

`GH_TOKEN` 是 gh 官方支援的環境變數,**不寫入任何設定檔**——連 `~/.config/gh/hosts.yml`
都不會產生。token 為 **fine-grained PAT**,範圍僅 `YSShih/CTIP`、權限僅 **Actions: Read-only**,
存於 `~/.config/ctip/gh-token`(0600,**在 repo 之外**,gitleaks 掃不到),
由 `~/.zshrc` 的一行 `export` 帶入。

> repo 是 **PUBLIC**,實測**匿名也能讀 workflow runs**
> (`GET /repos/YSShih/CTIP/actions/runs` → HTTP 200,匿名限額 60/hr)。
> 但 `gh` 本身即使查 public repo 也要求 token,所以 token 仍是必要的——
> 這是 gh 的行為,不是權限需求。也因此權限給到 Actions: Read-only 就夠。

**零影響的證據**(安裝前後對比):

| 檢查 | 結果 |
|---|---|
| `git config --global --list` diff | 無差異 |
| `~/.gitconfig` sha | `8f14859…` → `8f14859…`(相同) |
| `git config --get credential.helper` | 仍是 `osxkeychain` |
| `~/.config/gh` | 不存在 |
| CTIP 的 remote | 未被修改 |

> ⚠️ `zsh -lc`(非互動 login shell)**不會**讀 `.zshrc`,驗證要用 `zsh -ic`。
> `dod.sh` 由互動 shell 呼叫,繼承得到該變數。

---

## 2. M3-19 的假綠:`--limit 1` 只抽一支 run

`dod.sh` 原本以 `gh run list --limit 1 --json conclusion` 判定「CI 全綠」。
但**九支 workflow 在同一次 push 同時觸發**,「最近一次」是它們之中的哪一支基本上是任意的。

**實測**:`--limit 1` 抽到 `build`(success)→ **M3-19 回報 PASS**,
而同一個 commit(`686e15d`)上 `security` 與 `backend-test` 都是 **failure**。

這與 [ADR 0022](0022-orphan-deliverables.md) 記的「只有兩支 workflow 且都綠也會通過」
是**同一個形狀**,只是換到 run 結論這一半:當時補上了「11 支檔案必須存在」,
卻沒動 run 結論的判定,於是同一類缺陷又躲了一次。

### 決策

新增 `dod_ci_all_green()`:HEAD 這個 commit 上,**每一支 push 觸發的 workflow 都必須
`completed` + `success`**,逐支以 `gh run list --workflow=<w>.yml --commit <sha>` 查詢
(已實測兩個旗標可併用)。

**必檢集合是九支**:`backend-test`、`backend-lint`、`frontend-test`、`build`、
`compose-validate`、`openapi-check`、`docker-build`、`security`、`deploy-staging`。

**排除兩支**:`deploy-prod`(只有 `workflow_dispatch`)、`heavy-test`(`schedule` + `dispatch`)
——實測它們對 HEAD 沒有 run,列入必檢會**永遠 FAIL**。
它們的存在性已由既有的「11 支檔案皆存在」涵蓋。

三種失敗各給明確訊息:查無 run(HEAD 未推送)、`status != completed`(還在跑)、
`conclusion != success`(點名是哪一支、什麼結論)。

**改完之後 M3-19 會確實地 FAIL,那是正確的**:

```
[FAIL] M3-19 11 支 workflow 皆存在且 HEAD 的 CI 全綠(九支 push 觸發者)
       | backend-test:failure
       | security:failure
       | CI 未全綠(HEAD=686e15d);M3-19 要求九支 push 觸發的 workflow 全部 success
```

---

## 3. `backend-test` 從上線起 19 次 run、0 次成功

`gh run list --workflow=backend-test.yml --limit 100` → **19 failure、0 success**。
對照 `frontend-test` 19/19 綠、`build` 19/19 綠。

**而先前的複查從未檢查過它**:`docs/progress.md:2036` 列了六支綠 + `security` 紅,
`backend-test` **不在任何一邊**。它之所以能一直躲著,正是因為第 2 節的 `--limit 1`。

### 症狀

```
IngestionEndToEndTest.firstSyncIngestsAllThreeMocksWithRejectionsRecorded:89
  ingestion_rejections 筆數  expected: 12  but was: 13
NotificationApiTest.anotherTenantsNotificationIsNotVisibleAndCannotBeMarked:134
  $.items.length()  expected: 0  but was: 22
NotificationApiTest.listsAndMarksNotificationsForTheCallersTenant
  $.items.length()  expected: 0  but was: 22
```

本機 `clean verify -Ptest-integration` 是 **1,131 tests 全綠**,CI 卻穩定紅。

### 根因(已重現,非推測)

三個失敗都是「**多出來的資料**」。整個 `ctip-app` 的整合測試共用**同一個**
Testcontainers PostgreSQL(`AbstractPostgresIntegrationTest:33` 的 `static final POSTGRES`),
資料跨測試類累積。

而通知的可見度是 `tenant_id IN (自家, public)`(`NotificationJpaRepository`),
**來源事件產生的 `SOURCE_FAILURE` 掛在 public tenant → 對每個租戶都可見**。
`NotificationApiTest` 斷言的是絕對值(`items[0]`、`length()==0`),
只有在「這張表除了本測試剛送出的以外是空的」時才成立。

**為什麼本機綠、CI 紅**:`pom.xml` 沒有設 `runOrder`,surefire 預設是 `filesystem`,
而 **APFS(macOS)與 ext4(Linux runner)給出的測試類順序不同**。

**重現**:本機 `mvn -pl ctip-app test -Ptest-integration -Dsurefire.runOrder=alphabetical`
→ `NotificationApiTest` 兩個案例轉紅,`length()` 期望 0 實際 **50**。

### 決策:建立前提 + 收斂斷言,**不釘死 runOrder**

- `NotificationApiTest`:`@BeforeEach` 加 `DELETE FROM notifications`。
  這讓「跨租戶看不到」這個**測試意圖**真的成立——它要驗的是隔離,不是「碰巧沒有別人的資料」
- `IngestionEndToEndTest`:`@BeforeAll` 加 `DELETE FROM ingestion_rejections` /
  `source_sync`。它原本**只在 `@AfterAll` 收拾**,等於假設自己是第一個跑的;
  斷言絕對筆數就必須自己建立前提

**為什麼不把 `runOrder` 釘死**:那只是把今天這個巧合凍結起來。
真正的缺陷是「測試依賴全域狀態」,釘死順序之後,下一個新增的測試照樣會踩到,
而且會以更難理解的方式踩到(「為什麼加一個測試會讓另一個不相關的測試變紅」)。
固定順序可以當**重現工具**,不能當修法。

### 驗證

| 驗證 | 結果 |
|---|---|
| 修前 `-Dsurefire.runOrder=alphabetical` | **FAIL**(`length()` 期望 0 實際 50)—— 重現成立 |
| 修後 `alphabetical` | EXIT=0 |
| 修後 `reversealphabetical` | EXIT=0 |
| 修後 `clean verify -Ptest-integration`(預設 `filesystem`) | BUILD SUCCESS |

**順序無關才算修好** —— 只驗預設順序等於沒驗。

---

## 仍未解:`security`(政策決定,不在本 ADR 範圍)

剩下的兩組弱點在**基底映像**(`eclipse-temurin` 的 Go stdlib、`nginx:1.30-alpine` 的 OpenSSL),
**本 repo 沒有任何動作可做**,上游重建映像才會消失
([ADR 0044](0044-security-findings-remediation.md))。

因此即使 `backend-test` 修好,**M3-19 仍會 FAIL**。要讓它能綠,得先決定 ADR 0044 提的那個
政策問題:把「應用相依(擋 PR)」與「基底映像(只回報)」分成兩道,寫進
[13 §13.8](../../spec/13-platform-ops.md)。**那是政策決定,不由 AI 自行定調。**
