# CTIP — AI Session 執行協議

本專案依 `docs/spec/`(CTIP Master Specification v2.0)以**多 session、一次一個 phase** 的方式實作。
每個新 session 開始任何工作前,依序執行以下開場協議。

## 開場協議(每個 session 必做)

1. 讀 `docs/progress.md` — 確認目前進度與上一個 session 留下的注意事項
2. 讀 `docs/spec/00-master.md` — 強制契約、24 條執行規則、Phase 順序
3. 讀 `docs/spec/phases/phase-NN.md` — 你被指派的執行單
4. **只讀執行單「治理規格」指向的主題檔章節,不要通讀整份規格**(拆檔的目的就是控制 context)

## 硬性規則(完整清單見 00-master.md §0.3、§0.4,以下是最容易踩的)

- **一次只做一個 phase**;未通過里程碑 DoD Gate 不得進入下一個里程碑
- **不使用 Lombok**(連 dependency 都不加)
- **不得自行升版任何 Maven / npm 相依**;版本一律依 `06-tech-stack.md` §6.2,發現過期只能回報
- 功能與測試同時產生;不得留下假 TODO / placeholder / 永不可達的 enum 值
- 標 `[M2]` / `[M3]` 的內容在 M1 只留擴充點(介面/port),不得實作
- 不得增減頂層目錄(結構契約:`05-environment.md` §5.1)
- 命名一律依 `02-ddd-model.md` §2.1 詞彙表
- 規格模糊時優先序:安全性 > 可維護性 > 可測試性 > 可擴充性 > 向後相容 > Clean Architecture 邊界;做了決定要在 `docs/architecture/decisions/` 寫 ADR 並在回報中指出

## Git 慣例

- 直接 commit `main`,**一個 phase 一個 commit**,message 格式:`Phase N: <內容摘要>`
- 完成判準**全綠才 commit**;判準指令在各 phase 執行單的「完成判準」段
- 不得 commit secret;`environment/.env*` 已在 .gitignore(`.env*.example` 除外)

## Phase 完成後的收尾(必做)

1. 跑該 phase 執行單的完成判準,全綠
2. Commit
3. 更新 `docs/progress.md`:狀態、commit hash、判準結果、偏離事項、給下一 session 的注意事項
4. **停下來回報,不得自行開始下一個 phase**(下一 phase 由使用者開新 session 指派)

里程碑結束(Phase 12 / 19 / 23 之後)由獨立 session 執行 `./environment/scripts/dod.sh <gate>`。

## Context 管理

- 大範圍規格閱讀、跨檔案搜索交給 Explore subagent,主 context 只留結論
- 實作分批進行:寫一批 → 編譯 → 再寫下一批,不得未編譯驗證就產生數百個檔案
- 若 context 吃緊,先把「已完成 / 待辦」清單寫進 `docs/progress.md` 再繼續,保證可斷點續作

## 本機環境

- JDK 25(Homebrew OpenJDK)、Node 24(nvm)、Maven 3.9.15;皆已驗證可用
- PHP / Composer 由 Laravel Herd 提供(與本專案無關,勿混用)
