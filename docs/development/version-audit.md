# 版本複查記錄(append-only)

> 程序見 [docs/spec/06-tech-stack.md](../spec/06-tech-stack.md) §6.4。
> 每 6 個月複查一次,或任一套件被通報 CVE 時立即執行。

## 2026-08-21 — 初始 pin(Phase 1)

依規格版本表(06 §6.2)pin,並於 pin 當下對 registry 逐一查證:

| 套件 | 規格 | 實際 pin | 查證結果 |
|---|---|---|---|
| Spring Boot | 4.1.0 | 4.1.0 | repo1.maven.org 存在 ✅(search.maven.org 索引落後,勿以其為準) |
| MapStruct | 1.6.3 | 1.6.3 | ✅ |
| springdoc | 3.1.0 | (Phase 9 引入) | repo1 存在 ✅ |
| Spotless maven-plugin | 2.x | 2.46.1 | 2.x 最新;3.10.0 已釋出,屬 major,不自行升版 |
| palantir-java-format | 2.x | 2.97.0 | 2.x 最新 ✅ |
| maven-checkstyle-plugin | 3.x | 3.6.0 | ✅ |
| JaCoCo | 0.8.x | 0.8.15 | ✅ |
| ArchUnit | 1.4.x | 1.4.2(Phase 4 引入) | 1.5.0 已釋出,屬 minor,記錄但不升版(依 06 §6.4 第 3 判斷) |
| React | 19.2.x | ~19.2.8 | npm ✅ |
| Vite | 8.2.x | ~8.2.2 | npm ✅ |
| TypeScript | 7.0.x | **~5.9.3(降版)** | 7.0.2 存在於 npm,但 typescript-eslint 8.67.0 的 peer 範圍為 `typescript <6.1.0`,`npm ci` 無法解析。依 06 §6.3.2 動用**唯一允許的降版**至 5.9.x。複查日確認 typescript-eslint 是否已支援 TS 7 後升回 |
| ESLint | 10.8.x | ~10.8.1 | npm ✅ |
| Vitest | 4.1.x | ~4.1.11 | npm ✅ |
| Prettier | 3.x | ^3.9.6 | npm ✅ |
| eslint-plugin-import | 2.x | ^2.32.0 | npm ✅,但其 peer 只宣告到 ESLint 9(`^2 || ... || ^9`),與規格 pin 的 ESLint 10.8.x 衝突。**規格版本表在 pin 日即存在此矛盾**(兩者皆標「已查證」)。處理:package.json `overrides` 將其 peer eslint 解析為 root 的 ESLint 10,`no-restricted-paths`(flat config)實測正常。複查日確認上游是否已支援 ESLint 10 後移除 override |
| typescript-eslint | (未列於版本表,ESLint flat config 必要工具鏈) | ^8.67.0 | npm ✅;複查日確認與 TS 7 / ESLint 10 相容性 |

**下次複查日:2027-02-21。**
