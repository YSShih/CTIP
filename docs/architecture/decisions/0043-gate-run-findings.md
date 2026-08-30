# ADR 0043 — M3 閘門實跑後的三項發現

- 狀態:accepted(2026-08-30,`dod.sh full` 首次實跑後)
- 範圍:`.github/workflows/openapi-check.yml`、`logback-spring.xml`(plain pattern)、
  `.github/workflows/security.yml`(不改,但記錄理由)
- 前一則:[ADR 0042](0042-m2-gaps-token-cleanup-and-settings.md)

---

## 背景

`./environment/scripts/dod.sh full` 首次實跑結果 **23/25**,失敗兩項:**M3-01** 與 **M3-19**。
兩項各自往下追,共發現三件事——其中兩件是真缺陷,一件是**掃描器正常運作**。

---

## 1. `openapi-check` 從上線起就沒綠過(29 次 run、0 次成功)

`gh` 裝進 VM 之後才看得到 CI 的實際狀況:這支 workflow 自 2026-08-27 引入以來
**29 次 run、18 次 failure、0 次 success**,而且沒有任何人發現——因為 M3-19 以前
只看「最後一次 run 的結論」,而本機根本沒有 `gh` 去看。

### 原因

```yaml
run: ./backend/mvnw -f backend/pom.xml verify -Ptest-integration -Dtest=OpenApiCompletenessTest
```

`verify` 會綁上 JaCoCo `check`,而只跑一個測試類時 `ctip-app` 的覆蓋率只有 **0.18**,
遠低於它的 BUNDLE 門檻 0.60。

這正是 [15 §15.0](../../spec/15-dod-gates.md#150-執行方式) 的 ADR 0017 規則 2 寫過的坑,
原文甚至明說「**兩處原本不一致**」:`dod.sh` 一直用 `test`,只有這支 workflow 用 `verify`。
當時只改了 `dod.sh`,workflow 沒跟著改。

### 修法與驗證

改用 `test`(與 `dod.sh` 一致)。**驗證過程本身有一個值得記下來的陷阱**:

| 指令 | 結果 |
|---|---|
| `verify -Dtest=OpenApiCompletenessTest`(工作區已有前次 build 的 `target/`) | **BUILD SUCCESS** ← 假的 |
| `clean verify -Dtest=OpenApiCompletenessTest` | **BUILD FAILURE**,`ctip-app` 0.18 < 0.60 |
| `clean test -Dtest=OpenApiCompletenessTest` | BUILD SUCCESS |

第一次重現時**沒有 `clean`,於是複製不出 CI 的失敗** ——JaCoCo 的 `jacoco.exec` 預設是
**append**,前一輪完整 `verify` 的覆蓋資料還留在 `target/` 裡,把門檻墊過去了。
CI 是全新 checkout,沒有那份資料。
**要重現 CI 的失敗,本機必須 `clean`**;少了它會得到一個看起來像「修好了」的假綠。

其餘三個步驟(比對 committed 版本、上傳 artifact、破壞性變更檢查)**從來沒有被執行過**
(第一步就掛了),本次一併在本機逐步驗過:產出與 committed 版本一致 ✅、無破壞性變更 ✅。

---

## 2. M1-37(後端 reload)是 Phase 22 的日誌格式改動造成的迴歸

M3-01 失敗的原因是巢狀的 `dod.sh mvp` 只有 **37/38**,掛在 M1-37。**重跑仍然失敗,不是 flake。**

### 原因

M1-37 的判定是:改一個 `.java` → `reload.sh` → 10 秒內在日誌看到
`restartedMain` **或** `Started .+ in .+ seconds`。

實測發現重啟**確實有發生**,但兩條路都對不到:

1. `restartedMain` 是**執行緒名**,而 Phase 22 的 `logback-spring.xml` 把 mvp/dev 的
   plain pattern 換成自訂格式時**沒有帶 `%thread`** ——Spring Boot 預設 console pattern
   本來有 `[%15.15t]`。執行緒名不在日誌裡,這個字串永遠不會出現。
2. 剩下 `Started … in … seconds` 一條路,而重啟實測要 **12.3 秒**,超過 10 秒的視窗。

### 修法

把 `[%15.15t]` 加回 plain pattern。這不只是為了讓判準過:

- **併發問題查不動**是更大的代價 —— Phase 22 自己那個 Lettuce／exemplar 啟動死鎖
  ([ADR 0032](0032-phase22-observability.md) §15)就是**靠執行緒名**才定位出來的
- staging/prod 的 JSON 格式不受影響:[13 §13.6](../../spec/13-platform-ops.md#136-監控日誌追蹤-phase-22--m3)
  規定的九個必含欄位沒有 thread,本次也不動它

改完 M1-37 立刻轉綠,`SensitiveLogTest` / `CtipJsonEncoderTest` / `SensitiveMasksTest`(20 個)全過。

> **教訓**:換掉框架的預設日誌格式時,要一併問「預設格式裡有而我沒帶的欄位,是誰在用?」
> 這次的答案是「一條 DoD 判準,以及所有併發問題的排查」。

---

## 3. `security` workflow **沒有壞** —— 它抓到了四組真弱點

三個 Trivy job 全紅,但讀了 log 之後確認:**不是 action 出錯,也不是資料庫下載被限速,
而是真的掃到 HIGH 且上游已有修補版本**(`ignore-unfixed: true` 已經濾掉沒有修補的)。

| 掃描對象 | 元件 | 現況 → 修補 | CVE |
|---|---|---|---|
| fs(`ctip-app/pom.xml`) | `org.postgresql:postgresql` | 42.7.11 → 42.7.12 | CVE-2026-54291(SCRAM-SHA-256-PLUS 降級 → MITM 保護被繞過) |
| backend image(`app.jar`) | `httpcore5` | 5.4.2 → 5.4.3 | CVE-2026-54399(過量 HTTP header DoS) |
| backend image(`app.jar`) | `httpcore5-h2` | 同上 | CVE-2026-54428(HTTP/2 HPACK DoS) |
| backend image(`usr/bin/pebble`) | Go stdlib v1.26.5 | → 1.26.6 | 8 項 HIGH(asn1 遞迴 DoS、`x/net/idna` 提權…) |
| frontend image(alpine 3.24.1) | `libcrypto3` / `libssl3` | 3.5.7-r0 → 3.5.8-r0 | CVE-2026-14456(OpenSSL QUIC 記憶體增長 DoS) |

### 為什麼不由 Coding LLM 修

四組**全部**都要動版本,而
[§0.4 規則 6](../../spec/00-master.md#04-coding-llm-執行規則) 與
[06 §6.1.2](../../spec/06-tech-stack.md#612-凍結與浮動強制) 明文:
**Coding LLM 不得自行升版任何 Maven／npm 相依,只能回報**。而且:

- `postgresql` 與 `httpcore5` 都是 **Spring Boot BOM 納管**的傳遞相依
  ([06 §6.1.3](../../spec/06-tech-stack.md) 規則 1:納管者不得硬寫版本)——
  正確修法是升 `spring-boot-starter-parent`,而 **Dependabot PR #12 已經開好了**
- alpine 的 openssl 來自 `nginx:1.30-alpine` 基底映像,由上游重建或 PR #1(1.31-alpine)解決
- `pebble` 與 Go stdlib 來自 `eclipse-temurin:25-jre`,**這個 repo 完全無法自行修**,
  只能等上游重建映像或換基底

### 為什麼也不放寬掃描門檻

把 `exit-code` 拿掉或改成只掃 CRITICAL,會讓這四項**真實的 HIGH 就此消音**。
[§0.4](../../spec/00-master.md#遇到規格模糊時) 的優先序把安全性排在第一;
在沒有人決定要怎麼處理這些弱點之前就先把警報關掉,是最糟的一種「修好了」。

**因此本 ADR 不改 `security.yml`**,把決定權交回人:合併 Dependabot 的版本 PR
(需人工核准,major 另須寫 ADR),或明示接受風險並記錄。

> ⚠️ 但有一個結構性問題必須指出:基底映像那兩組(pebble/Go stdlib、alpine openssl)
> **這個 repo 沒有任何動作可做**,卻會擋住每一個 PR。若上游重建得慢,CI 就會長期紅著,
> 而「紅燈就失去意義」正是 workflow 註解裡寫的那個失效模式。
> 若要處理,合理的方向是**把「應用相依」與「基底映像」分成會擋與不會擋的兩道**——
> 但那是政策決定,應由人決定並寫進 [13 §13.8](../../spec/13-platform-ops.md#138-cicd-phase-23--m3基本流程自-m1-就要有)。

---

## 閘門結果

`dod.sh full` = **23/25**。修完前兩項之後,M3-01 的阻塞原因已排除;
**M3-19 仍紅**,而且需要三件本專案以外的事:`security` 的弱點被處理、
修好的 workflow 推上去重跑、以及 host 端安裝 `gh`(VM 裡那份 `dod.sh` 看不到)。
