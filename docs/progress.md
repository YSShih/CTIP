# CTIP 實作進度(跨 session 交接檔)

> append-only。每個 phase 完成後由該 session 更新。新 session 開場先讀這裡。
> Phase 順序與內容見 `docs/spec/00-master.md` §0.5。

## 總覽

| Milestone | Phase | 狀態 |
|---|---|---|
| M1 — MVP | 1–12 | Phase 1 完成,下一步 Phase 2 |
| M2 — Platform | 13–19 | 未開始 |
| M3 — Production | 20–23 | 未開始 |

---

## Phase 1 — Repository Skeleton

- **狀態**:done(2026-08-21)
- **執行單**:`docs/spec/phases/phase-01.md`
- **Commit**:(見 git log,message `Phase 1: repository skeleton`)
- **完成判準結果**:全綠 —
  - `./backend/mvnw -f backend/pom.xml -DskipTests package` ✅ 四個 module 皆產生 jar
  - `mvn verify`(含 Spotless、Checkstyle、JaCoCo 綁定)✅
  - `spotless:check` ✅、`checkstyle:check` ✅
  - `npm ci && npx tsc --noEmit && npx eslint . --max-warnings 0` ✅、`prettier --check .` ✅
  - `environment/.noop/.gitkeep` ✅;`.gitignore` 對 `.env*` / `.env*.example` 行為 ✅
- **偏離事項 / ADR**:兩項版本相容性處置,皆記錄於 `docs/development/version-audit.md`:
  1. **TypeScript 7.0.2 → 5.9.3 降版**:typescript-eslint 8.67.0 peer 上限 `<6.1.0`。這是 06 §6.3.2 明文允許的唯一降版。
  2. **eslint-plugin-import 2.32.0 peer 不含 ESLint 10**:規格版本表自身的矛盾(兩者皆標「已查證」)。以 package.json `overrides` 解決,規則實測正常。**已依規則 6/17 回報**。
- **給下一 session 的注意事項**:
  - Maven 指令一律用 `./backend/mvnw -f backend/pom.xml ...`(wrapper 已進版控,Maven 3.9.15)
  - 測試分層 profile 已就緒:預設只跑 `unit` tag;`-Ptest-slice` / `-Ptest-integration` / `-Ptest-all` 控制 excludedGroups
  - JaCoCo:各 module 以 `ctip.coverage.line-minimum` property 控制 BUNDLE 門檻;ctip-core 已依套件設兩條 PACKAGE 規則(domain 0.85 / application 0.75)
  - Checkstyle 設定在 `backend/config/checkstyle/ctip-checks.xml`,經 `${maven.multiModuleProjectDirectory}` 引用
  - frontend 的 feature 依賴規則 F1/F2/F4 已在 `eslint.config.js` 以 zones 實作(9 個 feature 名已預先列出);F3 由未來的 `api:check` script 負責(Phase 10)
  - Phase 2 是 Environment + Docker(compose、Dockerfile、四份 .env、scripts),治理規格 05-environment.md
