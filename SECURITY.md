# Security Policy

## 回報弱點

請勿以公開 issue 回報安全弱點。請寄信至專案維護者(見 `CONTRIBUTING.md`),信中包含:

- 受影響的元件與版本(commit hash)
- 重現步驟或 PoC
- 影響評估(機密性 / 完整性 / 可用性)

我們會在 7 天內回覆確認,並在修復發布前與回報者協調揭露時程。

## 支援範圍

只有 `main` 分支的最新 release 接受安全修補。相依套件的版本政策與複查程序見
[docs/spec/06-tech-stack.md](docs/spec/06-tech-stack.md);任何套件被通報 CVE 時
立即執行版本複查(§6.4)。

## 自動化掃描

每次 push / PR 與**每日**執行(`.github/workflows/security.yml`):

| 類型 | 工具 | 備註 |
|---|---|---|
| Secret 掃描 | Gitleaks | 掃**整段 git 歷史**,不只工作區 |
| 相依弱點 | Trivy(檔案系統)+ Dependabot alerts | Trivy 提供會擋 PR 的訊號;`ignore-unfixed`——上游無修補版本者不擋 |
| 容器映像 | Trivy(image) | 對兩個 production 映像 |
| SBOM | CycloneDX(backend)+ `npm sbom`(frontend) | 由 `build` / `docker-build` 產出 |

安全掃描類 action 一律**釘 commit SHA**([06 §6.1.2](docs/spec/06-tech-stack.md#612-凍結與浮動強制))。

## 設計層安全要求

本平台處理威脅情資,多租戶隔離、TLP 可見度與再散布控制為核心安全邊界,
規範見 [docs/spec/10-identity-plans.md](docs/spec/10-identity-plans.md) 與
[docs/spec/07-domain-intel.md](docs/spec/07-domain-intel.md)。
強制安全測試共 9 條,見 [docs/spec/14-testing.md](docs/spec/14-testing.md) §14.4。
實際成立的防線(含 **CSRF 停用的決策與其重新啟用條件**)整理於
[docs/architecture/security.md](docs/architecture/security.md)。
