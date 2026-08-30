# ADR 0041 — Phase 23:CI/CD 完整化、安全掃描、文件

- 狀態:accepted(2026-08-30)
- 範圍:`.github/workflows/`(11 支)、`.github/dependabot.yml`、`backend/pom.xml`(CycloneDX)、
  `frontend/`(cytoscape、`npm run sbom`、`/stix/:id`)、`environment/scripts/dod.sh`(M3-19)、
  `docs/architecture/{overview,security}.md`、`docs/development/{getting-started,plugin-sdk}.md`、
  ADR 0033–0040
- 前一則:[ADR 0032](0032-phase22-observability.md)

---

## 1. 範例 adapter 放在 SDK 的**測試**原始碼,並沿用既有 `SourceType`

`ExampleThreatSourceAdapter` 需要一個 `sourceType()`,而 `SourceType` 的四個成員
(`MOCK_OPENPHISH`/`MOCK_ABUSEIPDB`/`MOCK_ALIENVAULT`/`MANUAL`)都已被真實 adapter 佔用。
三個選項:

| 選項 | 問題 |
|---|---|
| 在 `SourceType` 加 `EXAMPLE` 成員 | 那個成員沒有 `sources` 列、永遠不會被註冊——**規則 16 的「永不可達的列舉值」** |
| 放 `src/main`,沿用既有成員 | SDK 的 jar 會出貨一個永不使用的 adapter;若哪天被誤註冊,`toUnmodifiableMap` 會讓應用啟動失敗 |
| **放 `src/test`,沿用既有成員** | ✅ CI 實際編譯並執行(M3-22 要的就是這個),永不成為 bean,不污染列舉 |

選第三個。範例的 javadoc 明寫「真實的第三方 adapter 必須在 `SourceType` 新增自己的成員」,
避免讀者照抄這一點。

## 2. SBOM 是建置產物,不是版控檔案

- backend:`cyclonedx-maven-plugin` 的 `makeAggregateBom` 綁在 `package`,
  聚合 BOM 產於 reactor 根 `backend/target/bom.json`;各 module 另有自己的一份,
  並嵌入 jar 的 `META-INF/sbom/application.cdx.json`
- frontend:新增 `npm run sbom`(`npm sbom --sbom-format cyclonedx > sbom.json`),
  `frontend/sbom.json` 進 `.gitignore` 與 `.prettierignore`

**後果(已寫入 getting-started)**:`dod.sh full` 的 **M3-20 需要先跑過一次建置**。
不 commit 的理由是它會隨每次 lockfile / 相依變動而變,而**沒有任何檢查能驗證它是最新的**——
一份過期的 SBOM 比沒有 SBOM 更危險,因為它看起來像有效的供應鏈證據。

`includeTestScope=false`:SBOM 描述的是**可佈署產物**的相依,test scope 不隨映像檔出貨。

## 3. 相依弱點掃描選 Dependabot,並用 Trivy fs 補上「會擋 PR 的訊號」

[06 §6.2.3b](../../spec/06-tech-stack.md#623b-安全掃描工具ci非專案相依) 允許
「OWASP Dependency-Check 或 Dependabot alerts」二擇一。選 Dependabot:
OWASP Dependency-Check 現在需要 NVD API key 才有可用的更新速率,那是一個**新的外部前置**,
而它解的問題 Dependabot alerts 已經解了。

但 **Dependabot alerts 不會讓 CI 轉紅**——它是 repo 的一個面板。因此 `security.yml` 另外跑
Trivy 的檔案系統掃描(讀 `pom.xml` / `package-lock.json`),`exit-code: 1` 會擋 PR。
兩者一起才等於「相依弱點有人看,而且有人擋」。

`ignore-unfixed: true`:上游還沒有修補版本的項目不擋 PR。長期紅著的 CI 等於沒有 CI。

## 4. 安全掃描 action 釘 commit SHA

依 [06 §6.1.2](../../spec/06-tech-stack.md#612-凍結與浮動強制):

| Action | tag | commit SHA |
|---|---|---|
| `gitleaks/gitleaks-action` | v3.0.0 | `e0c47f4f8be36e29cdc102c57e68cb5cbf0e8d1e` |
| `aquasecurity/trivy-action` | v0.36.0 | `ed142fd0673e97e23eac54620cfb913e5ce36c25` |

tag 寫在 `uses` 後面的註解,升版時兩者必須同時改。其餘 action(`checkout`、`setup-java`、
`docker/*`)用 major 浮動 tag——它們不讀 secret 之外的東西,而且浮動 tag 才吃得到修補。

`GITLEAKS_LICENSE` 只有組織帳號需要,本 repo 屬個人帳號,workflow 內以註解說明。

## 5. M3-19 就地擴充,不新增第 26 項

phase-23 要求「`dod.sh` 增設『11 支 workflow 檔案皆存在』的檢查」。
新增一個 ID 會讓 [15 §15.3](../../spec/15-dod-gates.md#153-dod-fullphase-2023) 的「25 項」失真,
而這個檢查在語意上就是 M3-19「CI 全綠」的前置——**只有兩支 workflow 且都綠**正是
M3-19 過去會誤判的原因(ADR 0016 Z3、[ADR 0022](0022-orphan-deliverables.md))。

因此 `dod_ci_green()` 依序做三件事:11 個檔案存在 → `deploy-prod.yml` 綁了
`production` environment → `gh run list` 的最後一次 run 是 success。任一失敗即 M3-19 FAIL。
規格已回寫。

## 6. `deploy-prod` 的人工核准:檔案能表達的與不能表達的

- **能**:`workflow_dispatch` 限手動觸發 + 確認字串 + `environment: production` 綁定
- **不能**:required reviewers 存在 GitHub 的 repo 設定裡

沒有那一步,workflow 仍會跑,只是沒有人工關卡——**這正是「看起來有、實際沒有」的那類缺口**,
所以不能只寫在註解裡。處置:
1. `deploy-prod.yml` 開頭以 ⚠️ 明寫,並指向 getting-started 的步驟
2. `dod_ci_green()` 至少驗「有沒有綁 `production` environment」
3. **[15 §15.5](../../spec/15-dod-gates.md#155-需人工確認未被自動驗證) 新增 P-07**
   (需人工確認清單由 6 項增為 7 項),`dod.sh` 的結尾清單同步

## 7. STIX Viewer(`/stix/:id`)

- **Cytoscape.js 3.34.2**——版本表列 3.x([06 §6.2.3](../../spec/06-tech-stack.md#623-frontend)),
  不是升版。它**自帶型別宣告**,因此不需要 `@types/cytoscape`
- **只有這一條路由 code-split**(`React.lazy` + `Suspense`):cytoscape 約 370 kB,
  其餘頁面不該為一個 M3 頁面付這個下載成本。實測主 bundle 896 kB / STIX 頁 445 kB
- **SRO 畫成邊,不畫成節點**:`relationship` 物件的語意就是兩端之間的一條邊。
  其餘 `*_ref` / `*_refs` 內嵌參照也畫成邊,標籤取自欄位名
- ⚠️ **圖只能順著物件自身的參照往外長**:`GET /api/v1/stix/{stixId}` 只回單一物件,
  平台**沒有「哪些 relationship 指向我」的反查端點**。因此從一個 indicator 出發看不到它的
  relationship,除非直接開那個 relationship 的 id。這是資料存取面的限制,不是 UI 取捨——
  要改善需要新增反查端點(超出本 phase 範圍,且會是新的可見度述詞面)
- 圖的建構是純函式(`features/stix/graph.ts`),與 Cytoscape 完全分離,因此可單獨測;
  頁面測試把 `cytoscape` 模組 mock 掉(jsdom 沒有 canvas),驗的是「餵給它什麼元素」

## 8. 八則跨 phase 的架構 ADR(0033–0040)

phase-23 要求「至少 8 則 ADR」。這八則記錄的是**跨越單一 phase、早已生效但只散落在規格正文裡**
的決策:不採用 CQRS、單一 compose、兩層 Bloom、移除 Lombok、停用 CSRF、TLP 與方案解耦、
`ctip-sdk` 作為 Shared Kernel、Repository port 分層。

它們是**追溯記錄**,不是新決策——寫下來的價值在於「為什麼不那樣做」:
每一則都有一個看起來更常見的替代方案(用 CQRS、每環境一份 compose、每租戶一份 Bloom、
用 Lombok、開 CSRF、方案決定可見度、SDK 自建 wire 型別、直接用 Spring Data),
沒有記錄的話,後續每個 session 都可能重新提案一次。
[13 §13.3](../../spec/13-platform-ops.md#133-安全) 另外**明文要求** CSRF 那一則必須存在。

## 9. 已知未完成 / 需要人接手的三件事

1. **M3-19 在本機無法通過**:`gh` 未安裝,且 `git remote` 的 host key 未驗證,
   本 repo 從未推上 GitHub、CI 從未跑過。這是 [15 §15.3](../../spec/15-dod-gates.md#153-dod-fullphase-2023)
   註明的操作者前置([ADR 0022](0022-orphan-deliverables.md) 已列為「沒有歸位的一項」)
2. **`TOKEN_CLEANUP_CRON`**([08 §8.7](../../spec/08-ingestion-sdk.md),標 M2)至今無實作——
   自 Phase 21 起連續回報第三次
3. **[12 §12.5](../../spec/12-frontend.md) 的 Settings 頁(`/settings`,標 M2)不存在**,
   `POST /api/v1/auth/change-password` 因此仍無前端入口——同樣自 Phase 21 起第三次回報

第 2、3 項屬 M2 的遺漏,**不在 phase-23 的交付物清單內**,因此本 phase 未實作
(規則:一次只做一個 phase 的交付物);依規則 17 明確回報。
