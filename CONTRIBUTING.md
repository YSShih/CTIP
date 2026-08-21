# Contributing

本專案依 [docs/spec/](docs/spec/)(CTIP Master Specification v2.0)實作。
**規格是 single source of truth**;任何貢獻(人或 AI)都必須遵守。

## 開始之前

1. 讀 [docs/spec/00-master.md](docs/spec/00-master.md) — 強制契約與 24 條執行規則
2. 讀 [docs/progress.md](docs/progress.md) — 目前進度
3. AI session 另須遵守 [CLAUDE.md](CLAUDE.md) 的執行協議

## 硬性規則(節錄)

- 遵守 Phase 順序,一次一個 phase;完成判準全綠才 commit
- 不使用 Lombok;不得自行升版任何相依(版本表:[docs/spec/06-tech-stack.md](docs/spec/06-tech-stack.md))
- 功能與測試同時產生;覆蓋率門檻見 [docs/spec/14-testing.md](docs/spec/14-testing.md)
- 格式化與可讀性由工具強制:Spotless(palantir-java-format)、Checkstyle 五條規則、ESLint + Prettier
- 命名依 [docs/spec/02-ddd-model.md](docs/spec/02-ddd-model.md) §2.1 詞彙表

## 建置與驗證

```bash
# Backend
./backend/mvnw -f backend/pom.xml verify

# Frontend
cd frontend && npm ci && npx tsc --noEmit && npx eslint . --max-warnings 0
```

## Commit 慣例

- main 直接 commit,一個 phase 一個 commit,message 格式:`Phase N: <內容摘要>`
- 不得 commit secret;`environment/.env*` 已被 .gitignore 排除(`.example` 樣板除外)

## 架構決策

規格模糊時的取捨優先序與 ADR 要求見 00-master.md §0.4。
ADR 放在 `docs/architecture/decisions/`,格式:`NNNN-<slug>.md`。
