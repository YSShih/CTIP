# Phase 23 — CI/CD 完整化 · 安全掃描 · 文件  `[M3]`

## 前置條件
- Phase 22 完成判準全綠

## 交付物

### Workflows（11 支）

> ⚠️ 其中 6 支（`backend-test`、`backend-lint`、`frontend-test`、`build` 標 M1；
> `docker-build`、`security` 標 M2）是**逾期件**——Phase 1–12 未交付，見
> [13 §13.8](../13-platform-ops.md#138-cicd-phase-23--m3基本流程自-m1-就要有) 的修訂註記。
> 本 phase 一次補齊，並在 `dod.sh` 增設「11 支檔案皆存在」的檢查。
`backend-test.yml`、`backend-lint.yml`、`frontend-test.yml`、`build.yml`、`compose-validate.yml`、`openapi-check.yml`、`docker-build.yml`（含 SBOM）、`security.yml`、`heavy-test.yml`（nightly）、`deploy-staging.yml`、`deploy-prod.yml`（**protected environment + 人工核准**）

### 安全掃描
OWASP Dependency-Check 或 Dependabot alerts、Gitleaks、Trivy、CycloneDX + `npm sbom`

### 文件（12 份必要文件）
`README.md`（**擴充**，不覆寫）、`SECURITY.md`、`CONTRIBUTING.md`、`LICENSE`、`docs/architecture/overview.md`、`docs/architecture/security.md`（含 CSRF 決策 ADR）、`docs/deployment/licensing.md`、`docs/deployment/privacy.md`、`docs/development/getting-started.md`、`docs/development/plugin-sdk.md`、`docs/development/version-audit.md`、`docs/api/openapi.json`

### ADR（至少 8 則）
不採用 CQRS、單一 compose 策略、兩層 Bloom、**移除 Lombok**、CSRF 停用、**TLP 與方案解耦**、**`ctip-sdk` 作為 Shared Kernel**、**Repository port 分層**

### SDK
`ExampleThreatSourceAdapter`（完整可編譯，CI 中實際編譯並測試）

## 治理規格
- [13-platform-ops.md §13.8](../13-platform-ops.md#138-cicd-phase-23--m3基本流程自-m1-就要有)
- [15-dod-gates.md §15.3](../15-dod-gates.md#153-dod-fullphase-2023)

## 完成判準
```bash
./environment/scripts/dod.sh full        # ← 整個 DoD-Full，25 項
gh run list --limit 1 --json conclusion -q '.[0].conclusion == "success"'
```

## 不得做的事
- **不得覆寫根目錄 `README.md` 的既有段落**——Phase 1 已建立的「這是什麼／系統摘要／模組摘要」必須保留，本 Phase 只**新增**啟動方式、API 概覽、測試方式等段落
- 不得讓 `deploy-prod.yml` 缺少 protected environment 與人工核准
- **不得自行合併版本升級 PR**（見 [06 §6.1.2](../06-tech-stack.md#612-凍結與浮動強制)）

## 里程碑閘門
**此 Phase 結束後執行 `./environment/scripts/dod.sh full`。25 項全綠即完成 M3。**
