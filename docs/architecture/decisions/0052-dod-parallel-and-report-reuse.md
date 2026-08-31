# ADR 0052 — dod.sh:報告複用、聚合去重、資源感知並行

- 狀態:accepted(2026-08-31,使用者指示「重構 dod.sh,這隻 script 執行時間太長,應該要讓多個 agent 可以帶不同參數非同步執行、檢查狀態」;追加「除了不能同時跑的以外,其他都要優化」)
- 範圍:`environment/scripts/dod.sh`、`environment/scripts/dod/`、
  [15 §15.0–§15.3](../../spec/15-dod-gates.md#150-執行方式)、[05 §5.1／§5.10](../../spec/05-environment.md#51-儲存庫結構契約強制)
- 前一則:[ADR 0051](0051-test-timeout-contract.md)

---

## 背景

`dod.sh full` 實跑 **94 分鐘**(`docs/progress.md`,2026-08-31)。盤點之後,
時間並不是花在「檢查很多」,而是花在**把同一批測試重跑好幾次**:

| # | 浪費 | 量級 |
|---|---|---|
| 1 | `M1-01` 的 `verify` 已經把全部測試跑完,`M1-16`~`M1-34` 又用 `mvn test -Dtest=<類名>` 逐一重跑其中 17 個類;`phase2` 再 23 個、`full` 再 17 個 | 三個 gate 合計約 **64 次 Maven 呼叫**,各自付 JVM + reactor + Testcontainers 啟動 |
| 2 | `M3-01 = dod.sh mvp && dod.sh phase2`,而 `phase2` 的 `M2-01` 又是 `dod.sh mvp` → **mvp 在 full 底下連跑兩次**。memo 目錄是 `mktemp -d` 且沒有 export,巢狀行程共用不到 | 約 **40 分鐘**(progress.md 已標記,「尚未進行」) |
| 3 | 全部序列執行:前端 lint/build/test、`compose config`、`gh` 查 CI 這些與 Maven 毫無資源衝突的項目全排在後面等 | —— |

第 1 點與 [ADR 0047](0047-dod-m1-02-coverage-check.md) 修 `M1-02` 是**完全同一個形狀**
(規格寫「同上」,實作照字面把整個 build 再跑一次)。

## 決策 1 —— 逐類測試判準改為對測試報告下斷言

`M1-16`~`M1-34`、`M2-02`~`M2-24`、`M3-02`~`M3-22` 的判準改為對
`backend/*/target/surefire-reports/TEST-*.xml` 下斷言,不再逐類重跑。
真正需要獨立執行的只有 3 個 `@Tag("heavy")` 的類(`-Ptest-integration` 排除 `heavy`),
以**一次批次**跑完供 `M2-22` / `M2-24` / `M3-02` 使用。

`M1-35`(`npm run test -- IocSearchPage`)與 `M3-05`(`playwright test websocket`)同理,
改為對 `M1-09` / `M2-26` 的 JSON 報告下斷言——它們原本都是把上一項已經跑過的測試再挑一部分跑一次。

### 假綠風險與四道守衛

報告檔留在 `target/` 裡。**不驗新鮮度的話,一份幾天前的舊報告會讓一個根本沒跑過的判準一路 PASS**
——與 ADR 0047 記的「`jacoco.exec` 不存在時 `jacoco:check` 靜默通過」完全同型。
這個風險不是理論上的:動手時 `backend/*/target/surefire-reports/` 裡就躺著三個 heavy 類的舊報告,
而最後一次建置用的是 `-Ptest-integration`(排除 heavy),根本沒跑過它們。

| # | 守衛 | 擋掉什麼 |
|---|---|---|
| 1 | 測試類**原始碼存在** | ADR 0017 的守衛,原樣保留 |
| 2 | 報告檔存在 | 「沒跑過」被當成「通過」 |
| 3 | 報告**比基準新** | 拿上一輪的結果給分 |
| 4 | `tests > 0`、`failures == 0`、`errors == 0`、且**不是全部 skipped** | 整個類別被 `@Disabled` 卻照樣 PASS |

第 3 道的基準分兩種情況,這是為了**不犧牲「單獨執行某一項」這個既有用法**:

- 本輪跑過建置 → 基準是建置開始時寫下的 stamp(最強:報告必須是這一輪產生的)
- 本輪沒跑建置(例如 `dod.sh mvp M1-21`)→ 基準是**最新的原始碼 mtime**:
  只要有任何一個 `src` 檔比報告新,報告就不反映現在的程式碼

> 這一道在開發過程中真的擋下了一次:改完 `backend/pom.xml` 之後跑 `dod.sh mvp M1-25`,
> 它正確地判 FAIL 並指出「原始碼比報告新」。

## 決策 2 —— `M2-01` / `M3-01` 由巢狀執行改為聚合判定

gate 展開為項目集合的聯集(`full = M1-* ∪ M2-* ∪ M3-*`),每一項只跑一次。
「DoD-MVP 全部仍通過(回歸)」以**所有 `M1-*` 皆 PASS** 判定——語意與規格逐字相同,
但不再需要把整個 mvp 重跑一遍。

## 決策 3 —— 單一全域互斥鎖 → 具名資源鎖 + 資源感知的 DAG 排程

`15 §15.0` 第 4 點(ADR 0028)的規則是「同一個 repo 同時只能有一個 gate 在跑」。
那條規則的**實據**是共用 `backend/*/target` 與容器記憶體,而不是「gate」這個單位。
把互斥降到資源層級:

| 資源 | 為什麼是資源 |
|---|---|
| `maven` | host 端 `mvnw` 寫的 `backend/*/target/` 正是 mvp dev 容器掛載的目錄 |
| `docker-env` | 四個環境共用同一個 compose 專案名、port 與 `postgres-data` volume |
| `frontend-src` | 只有 `api:check` 會**寫入** `src/api/generated/` |
| `frontend-dist` | 只有 `npm run build` 會寫 `dist/` |

排程器把所有「相依已滿足、資源空著」的項目同時發動。鎖以**排序後的順序**取得,
且**取不到就全部放掉**(非阻塞),死鎖與活鎖都不可能發生;鎖檔在 `${TMPDIR}` 且記錄 pid,
因此多個行程(多個 agent)之間同樣互斥,殘鎖會被接手。

⚠️ **`build` 與 `stack` 仍然不得並行**——兩者都要 `maven`。這是唯一無法拆開的序列化,
ADR 0028「並行執行的分數完全不可信」的教訓完整保留。
要拆開只能讓 build 跑在獨立的 worktree 上,但那會變成「閘門驗的是一份複本,不是工作樹」
(未 commit 的改動不在 worktree 裡),另案評估,不在本 ADR 範圍。

好消息是新排程下**沒有損失**:所有 host 端 mvn 都集中在 `M1-01`(此時容器還沒起),
容器起來之後不再有 host 重建,`dod_ensure_backend_up` 的自我修復因此退居安全網。

## 決策 4 —— 逐項逾時,以及多執行者介面

**逐項逾時**是 2026-08-31 那次過夜無聲卡死 8 小時 51 分**唯一擋得住的**東西
(ADR 0051 的 JUnit 30 秒在 JVM 內,管不到「JVM 整個卡死」)。
預設 `build` 2700 秒 / `stack` 1800 / `frontend` 900 / 其餘 300,`--timeout` 可覆寫;
逾時即把整棵行程樹中斷(Maven 會生 JVM 子行程,只殺 shell 沒有用)並判 FAIL。
`--parallel` 另在 macOS 自動以 `caffeinate -ims` 包住自己。

**多執行者介面**:結果檔、輸出、memo 全部放在一個 run 目錄(`${TMPDIR}`,以 `CTIP_DOD_RUN`
或 `--run-id` 指定),原子寫入。於是:

```bash
export CTIP_DOD_RUN=/tmp/ctip-dod-multi
dod.sh full --reset               # 開新的一輪(--lane/--shard 刻意不清結果,故必做)
dod.sh full --plan                # 項目 / lane / 資源 / 相依
dod.sh full --lane build          # 執行者 A
dod.sh full --lane frontend       # 執行者 B
dod.sh full --status              # 隨時查(唯讀)
dod.sh full --report              # 彙整全部,exit 0/1
```

`--lane` / `--shard` 不清上一輪的結果(那是要跟其他執行者共用同一輪),因此開新的一輪要先 `--reset`;
其餘情況一律是全新的一輪。跨執行者的相依以 `DOD_DEP_WAIT`(預設 300 秒)為上限:
相依停在 `PENDING`(沒有人在動它)且已空轉逾時,就判定「那個 lane 沒人跑」而放行,
讓判準自己以「報告不存在」誠實 FAIL,不會無限等下去。跨行程的 memo 也放在同一個 run 目錄
——`M2-27` 與 `M1-01` 是逐字相同的指令,現在真的只跑一次。

## 拆檔

`dod.sh` 573 行,加上 registry / 排程 / 狀態彙整會到 900 行以上。拆為:

```
environment/scripts/dod.sh   ← CLI 前端(參數、模式分派、唯讀模式)
environment/scripts/dod/
  registry.sh   ← 宣告式檢查表:id | lane | 資源 | 相依 | kind | 描述 | spec
  runner.sh     ← run 目錄、結果檔、memo、資源鎖、逾時、DAG 排程
  checks.sh     ← 既有的 dod_* 複合函式(原樣搬移)
  reports.sh    ← surefire / vitest / playwright 報告斷言
```

`05 §5.1` 是**強制**的結構契約且逐檔列出 `scripts/` 的內容,已一併回寫(先例:ADR 0045)。

⚠️ **必須在 bash 3.2 下可用**(macOS 內建):沒有 associative array(改用平行的 indexed array
+ 線性搜尋)、沒有 `wait -n`(以 `kill -0` 輪詢 + `wait <pid>` 回收)。

## 效果

```
=== 結果(full):89/90 通過 ===    18 分 57 秒
```

**94 分鐘 → 18 分 57 秒**(唯一未通過的 M3-19 是 HEAD 尚未推上 GitHub,與本次改動無關)。

⚠️ **加速的來源不是並行**。逐秒剖析顯示整輪 1134 秒裡有 **1034 秒(91%)只有一個項目在跑**:

| lane | 項數 | 累計耗時 | 獨佔牆鐘(該項執行時全場只有它) |
|---|---|---|---|
| stack | 9 | 610s | 611s |
| build | 65 | 524s | 420s |
| frontend | 6 | 134s | **0s** |
| static | 8 | 8s | **0s** |
| ci | 1 | 0s | **0s** |

前端與靜態檢查完全藏在 Maven 底下——並行的實際收穫是 **142 秒**。
真正的加速來自決策 1 與 2:**64 次 Maven 呼叫變成 2 次**。
把並行寫進來仍然值得(它同時是多執行者介面的基礎),但不該把功勞記在它頭上。

**剩下最大的一塊**:`M1-38`(README quickstart)345 秒中約 255 秒是
`./backend/mvnw verify -Ptest-integration`——README 的 quickstart 逐字寫著那一行,
而該判準就是「照 README 逐字執行」。memo 幫不上忙(它在 bash 子腳本裡,不是 dod 項目)。
這是 README 的設計問題而不是 `dod.sh` 的,留給使用者決定。

## 開發過程中抓到的 bug(都是實跑才看得出來的)

1. **聚合項目沒有相依,第 0 秒就判 FAIL**。`M2-01` / `M3-01` 的 `after` 是 `-`,
   排程器於是立刻發動它們,而當時大家都還是 PENDING。修法:聚合項目的相依不寫在 registry,
   由排程器依它聚合的字首自動展開。
2. **同一項可能被發動兩次**。子行程要花一點時間才寫得到結果檔,這段空窗裡下一輪掃描
   會看到 `PENDING` 而重複發動。修法:在**父行程**標記 `RUNNING` 之後才 fork。
3. **`caffeinate` 的 re-exec 把參數弄丟**。參數解析的 `shift` 會把 `$@` 吃光,
   `exec caffeinate … "$@"` 於是重放了一個沒有參數的自己。修法:解析前先存 `DOD_ARGV=("$@")`。
4. **聚合判定的範圍是「本行程選中的項目」**。`--lane aggregate` 之下一個 M1 項目都沒被選中,
   rc 保持 0,`M2-01` 空空地 PASS——假綠。修法:判定範圍改為整個 gate。
5. **失敗訊息從來沒印出來過**。原本用 `sed -n 's/.*<\(failure\|error\)…'`,
   而 BSD 的 BRE 不支援 `\|`,那一行是死的。改用 awk,並印出**是哪個測試方法**掛掉。
6. **`--status` 會改寫 `run.meta`**:查一次狀態,已跑 15 分鐘的一輪變成「已跑 11 秒」。
   唯讀模式不再寫入。

## 兩個踩過的操作坑(寫進 progress.md 給下一輪)

1. **不要在 gate 執行中編輯 `dod.sh` 或 `dod/*.sh`**。bash 是邊讀邊執行腳本檔,
   改動會讓讀取位移錯亂。本次為此主動終止並重跑了一輪。
2. **上一輪殘留的容器會讓這一輪的分數不可信**。前一輪被 kill 後 staging 的 8 個容器還開著,
   下一輪的 `M1-01` 就在 surefire「forked VM terminated without properly saying goodbye」掛掉
   ——容器與 Testcontainers 搶記憶體,而錯誤訊息完全看不出這件事,與 ADR 0028 同型。
   `dod.sh` 現在會在開跑前偵測「超過 mvp 三個容器」並警告。

## 刻意不動的

- **不加 surefire 平行 fork(`-DforkCount=2C`)**:Testcontainers 與共用 DB state
  會使測試結果與失敗定位都不可信,且屬於 `06 §6.3.6` 的 pom 契約範圍。
- **不保留 `dod.sh` 的巢狀自我呼叫**:它正是決策 2 要消除的東西。
  `15 §15.0` 第 5 點「判斷結束一律用退出碼」因此更為必要——巢狀雖已消失,
  但並行輸出的順序是**完成順序**,更不能拿來判斷進度。
