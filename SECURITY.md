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

## 設計層安全要求

本平台處理威脅情資,多租戶隔離、TLP 可見度與再散布控制為核心安全邊界,
規範見 [docs/spec/10-identity-plans.md](docs/spec/10-identity-plans.md) 與
[docs/spec/07-domain-intel.md](docs/spec/07-domain-intel.md)。
強制安全測試共 9 條,見 [docs/spec/14-testing.md](docs/spec/14-testing.md) §14.4。
