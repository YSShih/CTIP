# ADR 0046 — README 重構:把「沿革」與「導覽」分開

- 狀態:accepted(2026-08-30,使用者指示「readme 太冗長,幫我重新簡潔優化;系統摘要及 phase 一覽應該整合;歷史紀錄可以另外一個檔案」)
- 範圍:`README.md`、新增 `docs/history.md`、改寫 `docs/demo/README.md`、`docs/development/getting-started.md`
- 前一則:[ADR 0045](0045-full-project-review-doc-sync.md)

---

## 問題

README 長到 **59.8 KB / 372 行**,而其中三個表格儲存格就佔了約 **34 KB**
——「現況」段的 `backend/`、`frontend/`、`.github/` 三列,各自是一段橫跨七個 phase 的連續敘述,
在 GitHub 上會渲染成三個無法閱讀的巨大格子。

成因是 README 自己寫的一條規則:

> 本檔隨每個里程碑**擴充**而不覆寫。**既有段落(這是什麼／系統摘要／模組功能摘要)不得被覆寫。**

那條規則在多 session 執行期間是對的——它防的是「後面的 session 把前面的成果洗掉」。
但 23 個 phase 全部交付之後,它的副作用變成:**README 是一份 append-only 的施工日誌,
而不是一份給人看的專案入口。**

三個更具體的症狀:

1. **「系統摘要」(18 列能力表)與「Phase 一覽」(26 列)講的是同一件事的兩種切法**
   ——每一項能力都對得到某個 phase,讀者要在兩張表之間自己對照
2. **「CI/CD 與安全掃描」整段與 [`docs/development/getting-started.md`](../../development/getting-started.md) §6 幾乎逐字重複**
   ——包含「首次啟用 CI 時必做的兩件事」那兩條
3. **「快速開始」的服務表膨脹成 20 列的功能導覽**,每個 phase 都往上加一列,
   已經不是「快速」開始

---

## 處置

### 1. 沿革搬到 `docs/history.md`

README 說「現在是什麼」,`history.md` 說「怎麼走到這裡」。
三個巨大儲存格拆成逐里程碑、逐 phase 的小節,加上「三個 DoD 閘門」與
**「值得記住的幾個坑」**(Flyway 排序、`@Transactional` 內寫失敗紀錄、JaCoCo append、
`docker compose up` 不重建、compose 不視 profile 停用者為 orphan…)。

分工因此變成三層,各有各的讀者:

| 檔案 | 內容 | 讀者 |
|---|---|---|
| `README.md` | 現在是什麼、怎麼跑、去哪找 | 第一次看到這個 repo 的人 |
| `docs/history.md` | 怎麼走到這裡、踩過什麼坑 | 想理解決策脈絡的人 |
| `docs/progress.md` | 逐 phase 判準結果與交接事項 | 接手下一個 phase 的 session |
| `docs/architecture/decisions/` | 每個決策的完整理由與取捨 | 想知道「為什麼不是另一種做法」的人 |

### 2. 「系統摘要」與「Phase 一覽」合併

合併成**依里程碑分節的三張表,一列一個 phase,格子裡寫的是「交付的能力」**。
18 列能力表因此消失,資訊沒有掉——每一項能力原本就對得到某個 phase。

**23 個 phase 仍逐一列出**。這一點是刻意保留的:2026-08-30 使用者指出過
「專案有 23 個 phase,但 README 表格看不到 23 個」,把 phase 折疊成 `1–2`、`5–6` 會退回那個問題。

### 3. 重複的段落改為指路

「CI/CD 與安全掃描」「疑難排解」整段移除,改為指向 `getting-started.md`
(疑難排解那段原本只在 README,已搬進 `getting-started.md` §3)。
「快速開始」的 20 列功能導覽移入 `docs/demo/README.md`。

### 4. `docs/demo/` 從「M1 畫面速覽」改為「功能速覽」

原本只涵蓋 M1 的四個畫面(2026-08-27 的截圖),M2／M3 的 15 個頁面完全沒有出現過。
改寫為三個里程碑的畫面導覽 + 端點速查,並**補拍 11 張** M2／M3 的畫面
(threat-feed／threat-detail／sync／api-keys／subscription／settings／
stix-viewer／notifications／webhooks／audit／admin)。

截圖以 Playwright 對本機 mvp 環境拍攝,兩處**不是預設狀態、已在文件中標明**:
示範租戶手動指派 `ENTERPRISE` + `SYSTEM_ADMIN`(自助註冊拿到的是 `TENANT_ADMIN` + `FREE`,
而 `/notifications` 需要 `websocket_enabled`、`/admin` 需要 `system:admin`);
威脅情報的四筆資料經 `POST /threats` 建立(種子只有 IOC,Threat 只能由寫入端點產生)。

> **一個踩到的坑**:前端**刻意只把 token 存在記憶體**(避免 XSS),因此
> Playwright 每次 `page.goto()` 的整頁重載都會把登入狀態清掉,拍出來全是「需要登入」。
> 登入後必須改走 SPA 內導航(`history.pushState` + `popstate`),不得再 `goto`。

### 5. 補拍時發現的兩個實質缺陷(已修)

截圖是一種**沒有測試在做的檢查**——它逼你真的去看每一個頁面。兩個缺陷因此浮出來:

| # | 缺陷 | 修法 |
|---|---|---|
| 1 | `ForbiddenState` 的 `login` 文案寫「**M1 尚未開放註冊與登入**;正式版將在此引導您登入」——那是 Phase 12 的實況,而登入/註冊**自 Phase 13 就存在**(`/login`、`/register` 是實際路由)。§12.6 #4 要求顯示**原因**,而顯示一個已經不成立的原因比空白更糟 | 改為「請由右上角登入,或先註冊一個租戶」 |
| 2 | **STIX Viewer 在最常見的情況下看起來像壞掉**:cytoscape 的 layout `fit()` 沒有 zoom 上限,**單一節點且無關聯**(任何還沒有 relationship 的 indicator,也就是目前全部)會放大到標籤撐爆畫布。另外 `text-wrap: 'ellipsis'` 不處理 `\n`,程式碼刻意組的「型別 / 名稱」兩行標籤從來沒生效過,擠成一行後被 `text-max-width` 截掉頭尾 | 加 `minZoom: 0.2` / `maxZoom: 1.5`;`text-wrap` 改 `'wrap'`,並把 32 字元雜湊之類的長名稱先截短(無空白不可斷行) |

兩者都不是截圖才有的問題,而是**使用者一直看得到**的。
前端 `lint`／`format:check`／`tsc`／`test`(186)／`api:check` 修後全綠。

---

## 結果

| | 前 | 後 |
|---|---|---|
| `README.md` | 59.8 KB / 372 行 | **14.1 KB / 240 行** |
| `docs/history.md` | — | 21.5 KB |
| `docs/demo/README.md` | 1.9 KB、4 張圖(僅 M1) | 涵蓋三個里程碑 + 端點速查,**15 張圖** |

**M1-38 的相容性**:該判準把 README 的**全部 ` ```bash ` 區塊**串起來以 `bash -e` 執行
(`dod.sh:267`)。新 README 保留原本那兩個 bash 區塊、逐字不動,其餘可執行片段一律標 `sh`
(這個約定原本就寫在 README 的 HTML 註解裡,一併保留)。

**連結**:README 與 `docs/history.md`、`docs/demo/README.md` 的全部相對連結逐一驗證存在。
M3-24 只掃 `docs/spec/**`,README 的連結沒有自動守門——這次是手動驗的。

---

## 移除的那條規則

README 結尾的「本檔隨每個里程碑擴充而不覆寫……既有段落不得被覆寫」**已移除**。
它是多 session 施工期的保護措施,而施工已經結束;留著它會讓下一次合理的重寫變成「違反規格」
——正是 [03 §規範等級](../../spec/03-diagrams.md) 開頭說的那種「規格腐化的起點」。

需要防止內容被洗掉的地方,現在由 `docs/progress.md`(append-only 的施工日誌)
與 `docs/history.md` 承接。
