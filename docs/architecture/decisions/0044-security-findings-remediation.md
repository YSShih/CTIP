# ADR 0044 — Trivy 四組 HIGH 的處置

- 狀態:accepted(2026-08-30,使用者指示「修復優化弱點」)
- 範圍:`backend/pom.xml`(Spring Boot 4.1.0 → 4.1.1)、[06 §6.2.2](../../spec/06-tech-stack.md#622-backend) 版本表
- 前一則:[ADR 0043](0043-gate-run-findings.md) §3(發現與當時「不修」的理由)

---

## 背景

[ADR 0043](0043-gate-run-findings.md) §3 判定 `security` workflow **沒有壞**,是真的掃到四組 HIGH。
當時依 [06 §6.1.2](../../spec/06-tech-stack.md#612-凍結與浮動強制)(Coding LLM 不得自行升版)
選擇**回報而不修**,把決定權交回人。**使用者隨後指示修復**,本 ADR 記錄處置。

四組的可修性差異很大,分成兩類處理。

---

## 第一類:應用相依 —— 一個 patch 升版全解(已修)

三個 CVE 全部落在 **Spring Boot BOM 納管**的傳遞相依上,因此
[06 §6.1.3 規則 1](../../spec/06-tech-stack.md#613-其他規則)(納管者不得硬寫版本)
排除了「在 `<properties>` 覆寫版本」這條路 —— **正確修法只有升 parent**。

| 元件 | 4.1.0 帶的版本 | 4.1.1 帶的版本 | CVE |
|---|---|---|---|
| `org.postgresql:postgresql` | 42.7.11 | **42.7.13** | CVE-2026-54291(SCRAM-SHA-256-PLUS 降級 → MITM 保護被繞過) |
| `httpcore5` | 5.4.2 | **5.4.3** | CVE-2026-54399(過量 HTTP header DoS) |
| `httpcore5-h2` | 5.4.2 | **5.4.3** | CVE-2026-54428(HTTP/2 HPACK DoS) |

**`spring-boot-starter-parent` 4.1.0 → 4.1.1**(patch;[06 §6.1.3](../../spec/06-tech-stack.md#613-其他規則)
規則 3 把 patch/minor 歸為自動 PR 類,不是需要人工審核的 major)。
版本以 `mvn dependency:list` 實測確認,不是照 release note 推斷。

驗證:`clean verify -Ptest-integration` **1,128 tests 全綠**。

> Dependabot 的 **PR #12** 提的正是同一個升版。本次直接在本機套用並驗證,
> 效果等同合併該 PR;**沒有由 AI 去合併 PR**(06 §6.1.2 的禁令針對的是「自行合併」這個動作)。

---

## 第二類:基底映像 —— 這個 repo 沒有動作可做(未修,回報)

| 掃描對象 | 元件 | 來源 | 這個 repo 能做什麼 |
|---|---|---|---|
| backend image | `usr/bin/pebble` 的 Go stdlib v1.26.5(8 項 HIGH) | `eclipse-temurin:25-jre`(Ubuntu 26.04) | **無**。等上游重建映像,或換基底 |
| frontend image | `libcrypto3` / `libssl3` 3.5.7-r0 → 3.5.8-r0(CVE-2026-14456) | `nginx:1.30-alpine` | **無**。等 Docker Hub 重建 1.30-alpine 帶進 alpine 的修補套件 |

映像每次 CI 都重新建置,因此上游一修補就會自動消失 —— **不需要也不應該在這個 repo 改任何東西**。

### ⚠️ Dependabot PR #1(nginx 1.30-alpine → 1.31-alpine)不得合併

它看起來能解掉 OpenSSL 那一項,但 **1.31 是奇數 minor = nginx 的 mainline 分支**,
而 [00 §0.6](../../spec/00-master.md#修正的版本錯誤3-項) 修正 v1.1 的版本錯誤時,
理由**逐字**就是「1.29 是 mainline(奇數 minor)且已退役」——本專案明確選的是 **stable 分支**
([06 §6.2.4](../../spec/06-tech-stack.md#624-infrastructure-images))。
合併它會把一個已知的規格決定倒退回去,換取一個上游遲早會自己修好的套件更新。**應關閉該 PR。**

---

## 沒有做的事:放寬掃描門檻

沒有動 `security.yml` 的 `exit-code: 1` 或 `severity`。把警報關掉會讓上面四組**就此消音**,
而 [§0.4](../../spec/00-master.md#遇到規格模糊時) 的優先序把安全性排在第一。

> **但結構性問題仍在**:第二類的兩組**這個 repo 無法採取任何行動**,卻會擋住每一個 PR。
> 上游重建得慢時 CI 會長期紅著,而「紅燈就失去意義」正是 workflow 註解裡要避免的失效模式。
> 合理的方向是把「應用相依(可修 → 擋)」與「基底映像(不可修 → 只回報)」分成兩道,
> 但那是**政策決定**,應由人決定後寫進
> [13 §13.8](../../spec/13-platform-ops.md#138-cicd-phase-23--m3基本流程自-m1-就要有)。本 ADR 不擅自更動。
