# Phase 1 — Repository Skeleton  `[M1]`

## 前置條件
- 無（第一個 Phase）

## 交付物
- 頂層目錄結構（[05 §5.1](../05-environment.md#51-儲存庫結構契約強制)），**不得增減頂層目錄**
- `backend/pom.xml`（parent，`packaging: pom`）+ 四個 module 的 `pom.xml`
- Maven Wrapper（`mvnw`、`mvnw.cmd`、`.mvn/`）進版控
- `maven-compiler-plugin` 設定：`release 25`、`proc full`、`-parameters`、`-Xlint:all`，MapStruct 為唯一 annotation processor
- `ctip-sdk` / `ctip-core` 加 `-Werror`
- Spotless（palantir-java-format）+ Checkstyle（`backend/config/checkstyle/ctip-checks.xml`，僅五條規則）綁進 `verify`
- JaCoCo 設定，含 `ctip-core` 依套件的兩條門檻規則
- 四個測試 profile：`test-slice`、`test-integration`、`test-all`
- `frontend/` 骨架：`package.json`、`vite.config.ts`、`tsconfig.json`、`eslint.config.js`（flat config + `import/no-restricted-paths` 四條規則）、`.prettierrc`
- `environment/.noop/.gitkeep`（**必須 commit**）
- `.gitignore`（含 `environment/.env*` 與 `!environment/.env*.example`）
- `SECURITY.md`、`CONTRIBUTING.md`、`LICENSE`
- `docs/` 子目錄骨架

## 治理規格
- [05-environment.md §5.1](../05-environment.md#51-儲存庫結構契約強制)
- [06-tech-stack.md](../06-tech-stack.md)（版本、linter、編譯地雷）
- [01-architecture.md §1.3、§1.8](../01-architecture.md#13-maven-multi-module)

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml -DskipTests package     # 四個 module 皆產生 artifact
./backend/mvnw -f backend/pom.xml spotless:check
./backend/mvnw -f backend/pom.xml checkstyle:check
cd frontend && npm ci && npx tsc --noEmit && npx eslint . --max-warnings 0
test -f environment/.noop/.gitkeep
git check-ignore environment/.env.local && ! git check-ignore environment/.env.mvp.example
```

## 不得做的事
- 不得使用 Lombok（連 `<dependency>` 都不加）
- 不得新增頂層目錄
- 不得寫任何業務程式碼
- 不得在 `ctip-adapters/pom.xml` 加入 `ctip-core` 相依
- 不得在 `ctip-core/pom.xml` 加入 `spring-boot-starter-data-jpa` 或 `spring-data-commons`
