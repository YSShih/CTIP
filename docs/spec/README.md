# CTIP 規格書導覽

**CTIP Master Specification v2.0** — 2026-08-21（含 §0.7–§0.13 七輪實作回饋修訂，至 2026-08-27）

這是一份**供 AI 直接消費**的軟體規格書。它描述一個 Cyber Threat Intelligence Platform，設計目標是讓任何具備程式能力的 AI agent 能依此獨立完成開發，且不同 agent 在不同時間讀同一份規格會產出相容的結果。目前 **M1（Phase 1–12）已依本規格實作完成**（`dod.sh mvp` 38/38），實作進度與偏離事項見 [../progress.md](../progress.md)。

---

## 從哪裡開始

```text
你是被指派實作某個 Phase 的 AI
  ↓
1. 讀 00-master.md                    強制契約、Phase 順序、24 條執行規則
2. 讀 phases/phase-NN.md              你這個階段的前置條件、交付物、完成判準、禁止事項
3. 讀執行單指向的主題檔章節            只讀被指向的部分，不需通讀
4. 產生程式碼 → 編譯 → 測試
5. 執行執行單的完成判準                全綠才 commit
```

**不要一次讀完全部檔案。** 拆檔的目的就是讓每次工作只需要載入相關的部分。

如果你是**第一次接觸這個專案的人類**，讀 [../../README.md](../../README.md) 的系統摘要與模組表，再讀 [00-master.md](00-master.md) 的 §0.6 變更摘要。

---

## 檔案職責

行數為約值（隨實作回饋修訂增長，以檔案實際內容為準）：

| 檔案 | 行數 | 職責 |
|---|---|---|
| [00-master.md](00-master.md) | ~760 | **索引與強制契約摘要**。Phase Plan、執行規則、v1.1→v2.0 變更、§0.7–§0.27 實作回饋修訂索引 |
| [01-architecture.md](01-architecture.md) | ~330 | 分層與依賴方向、4 個 Maven module、抽象判準、9 條 ArchUnit 規則、可讀性規則與執行工具、M1 最小安全層 |
| [02-ddd-model.md](02-ddd-model.md) | ~360 | 9 個聚合與 60+ 條不變量、Ubiquitous Language 詞彙表、19 個 domain event、Shared Kernel、18 個值物件 |
| [03-diagrams.md](03-diagrams.md) | ~740 | 17 張 Mermaid 圖（模組依賴、9 張聚合、ERD、ingestion sequence、前端 5 張），**逐圖標註規範等級** |
| [04-data-dictionary.md](04-data-dictionary.md) | ~1120 | **27 張表完整 schema**、約束、索引、列舉、TTL 三步規則、Flyway 對應 |
| [05-environment.md](05-environment.md) | ~730 | 單一 compose、Dockerfile 契約、四種 profile、全部環境變數、Spring 設定對應、腳本契約、hot reload |
| [06-tech-stack.md](06-tech-stack.md) | ~320 | 版本表（含支援終止日與複查日）、分級支援窗口政策、linter、編譯地雷 |
| [07-domain-intel.md](07-domain-intel.md) | ~450 | IOC 模型、正規化與拒絕規則、去重、合併政策、評分、TLP 2.0 可見度、**STIX 2.1 完整映射** |
| [08-ingestion-sdk.md](08-ingestion-sdk.md) | ~310 | SDK 契約、10 個 pipeline stage、mock adapter、韌性、來源健康、12 個排程任務 |
| [09-api.md](09-api.md) | ~350 | 47 個端點、認證、cursor 分頁、16 個錯誤碼、DTO 規則、輸出過濾五步、OpenAPI |
| [10-identity-plans.md](10-identity-plans.md) | ~340 | 租戶隔離、RBAC 權限矩陣、JWT、API key、4 個方案 × 15 個配額維度、限流 |
| [11-sync-bloom.md](11-sync-bloom.md) | ~240 | 兩層 Bloom、**位元陣列格式**、delta 編碼、client 同步流程與 6 條契約 |
| [12-frontend.md](12-frontend.md) | ~220 | 結構、4 條 feature 依賴規則、狀態歸屬表、型別產生流程、15 個頁面、UI 要求 |
| [13-platform-ops.md](13-platform-ops.md) | ~340 | Kafka、通知、安全、隱私與 6 項保留政策、稽核、可觀測性、搜尋與降級、11 支 CI workflow |
| [14-testing.md](14-testing.md) | ~140 | L1–L4 分層、覆蓋率門檻、9 條安全測試、測試資料 |
| [15-dod-gates.md](15-dod-gates.md) | ~180 | **90 項可執行 DoD** + 6 項明確標為需人工確認 |
| `phases/phase-01..23.md` | 各 ~70 | 23 份執行單（薄，指向主題檔，不重複內容） |
| `archive/v1.1-master-codex.md` | 3038 | v1.1 原始單檔規格。**僅供出處追溯，不得依此開發** |

---

## 規範等級

圖表與部分規則標註三個等級，差別在於**可否機械驗證**：

| 等級 | 意義 | 驗證方式 |
|---|---|---|
| 🔴 **規範·自動驗證** | 必須符合，CI 會擋 | ArchUnit / ESLint / migration 比對 / `dod.sh` |
| 🟡 **規範·人工驗證** | 必須符合，工具無法檢查 | Code review／AI 自查，偏離須寫 ADR |
| ⚪ **參考** | 協助理解，不構成約束 | 無 |

不做這個區分的話，未來任何合理重構都會被記為「違反規格」，規格就會開始被忽略。

---

## 這份規格如何產生的

```text
GPT 產生初稿（v1.0）
  → Claude 第一輪調整（v1.1，3,038 行單檔）
  → Claude 逐行審查 + 24 輪設計決策訪談 + 對外部登錄查證（v2.0，本版）
```

v1.1 → v2.0 的實質改動：修正 **4 項建置阻斷缺陷**、**3 項版本錯誤**（含兩個已 EOL／已退役的元件）、**10 項規格內部衝突**（其中 2 項會導致編譯失敗）、補完 **19 張缺失的表定義**、新增 DDD 章與關聯圖章、把 DoD 從散文改為 90 項可執行檢查。

完整變更清單見 [00-master.md §0.6](00-master.md#06-相對-v11-的變更摘要)。

v2.0 定稿後，實作期間（Phase 2–20）與各輪複查陸續發現的規格衝突與缺口以**實作回饋修訂**回寫：修正直接寫進主題檔對應章節，並在 [00-master.md](00-master.md) 的 **§0.7–§0.27** 建立索引、於 `docs/architecture/decisions/` 留下 ADR。照字面實作會踩的坑集中在 [05 §5.8.1](05-environment.md) 與 [06 §6.3.6](06-tech-stack.md)。

### 安全性複查的結論（§0.27，2026-08-29）

Phase 1–20 的跨階段複查修掉五項缺陷，其中三項是安全性問題。它們有一個共同形狀值得記下來：
**每一項都是「規格寫對了，但清單沒有跟著新端點長」或「規格只寫了字面條件，沒寫它要防的東西」**。

| 缺陷 | 為什麼測試沒抓到 |
|---|---|
| CORS 漏 `PUT`／`PATCH`（[05 §5.7](05-environment.md)） | `MockMvc` 直接呼叫 handler，不走 preflight；只有跨源 preflight 測試驗得到 |
| Webhook 送達可打內網（SSRF，[13 §13.2](13-platform-ops.md)） | W1 只說「必須 https」，而 https 到 `169.254.169.254` 也是 https |
| 鎖定期滿計數不歸零 → 帳號可被永久鎖定（[10 §10.4](10-identity-plans.md)） | 既有測試只驗「10 次會鎖」，沒有驗「鎖定過期之後會怎樣」 |
| 匯入本文無容器層上限（[09 §9.7](09-api.md)） | 端點層確實回 413——但那是在整包已經進了記憶體之後 |
| 限流分類可被 `%69mport` 繞過（[10 §10.7](10-identity-plans.md)） | 分類看的是原始 URI，routing 看的是解碼後的路徑，兩者從未被放在一起比對 |

逐項的處置與被否決的替代方案見 [ADR 0030](../architecture/decisions/0030-phase1-20-review-security-fixes.md)；
該 ADR 末尾也列出**檢查過但未發現問題**的區域，下一輪複查可以直接跳過。

---

## 給人類讀者的提醒

這份規格的正文是繁體中文，程式碼識別字、檔案路徑、技術術語為英文。命名一律依 [02-ddd-model.md §2.1](02-ddd-model.md#21-ubiquitous-language-詞彙表中英對照) 的中英對照表——那張表是為了防止不同 AI session 對同一概念產生不同命名而存在的，**它是規範性的**。

規格中凡標「本版新增」「本版修正」者，皆為 v1.1 到 v2.0 的改動，並附有為什麼改的理由。理由不是註解，是規格的一部分——它們防止未來有人（或 AI）把修正改回去。

---

*導覽結束。*
