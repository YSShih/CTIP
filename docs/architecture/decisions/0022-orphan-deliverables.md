# ADR 0022 — 無主交付物歸位(批 7)

- **狀態**:accepted
- **日期**:2026-08-28
- **範圍**:`phases/phase-14/16/20/21/23.md`
- **背景**:清障計畫的批 7。使用者定調「**寫進對應 phase 執行單的交付物,不現在實作**」。

---

## 問題

盤點時發現一整類東西:**規格明文要求、DoD 會檢查,但沒有任何 phase 執行單負責交付**。
它們不會在任何 phase 的收尾被發現,只會在 M3 的閘門一次爆出來。

## 歸位結果

| 項目 | 誰要求 | 歸給 | 理由 |
|---|---|---|---|
| **Playwright E2E 骨架** | **M2-26**、M3-05、`phase-20` 判準 | **Phase 16** | M2 最後一個交付前端頁的 phase;M2-26 屬 DoD-Phase2,必須在 M2 結束前就位 |
| 前端 `/iocs/new`、`/iocs/import`、`/settings/subscription` | `12` 標 M2 | Phase 14 | 三頁都是手動提交／匯入／方案的 UI,與該 phase 的後端同批 |
| 前端 **webhook 管理頁** | `09` 有三個 `/webhooks` 端點與 `webhook:manage`,但 `12` 的頁面表**沒有這一頁** | Phase 20 | 與 webhook 後端同批 |
| 前端 **Admin Panel**(`/admin`) | `12` 標 M3 | Phase 21 | 與稽核／保留的管理操作同批 |
| 前端 **STIX Viewer**(`/stix/:id`) | `12` 標 M3;Cytoscape.js 已在版本表卻未安裝 | Phase 23 | M3 收尾 |
| **`POST /auth/change-password`** | [ADR 0015](0015-future-phase-hardening.md) 指定為「M3 責任」 | Phase 21 | 見下 |
| `docs/api/events/` schema + topic 對照 | `13 §13.1` | Phase 23 | 目錄目前是空的,且無任何 DoD 檢查 |
| `docs/api/` 的 webhook timestamp 偏差規則 | `13 §13.2` 明文「必須寫入 `docs/api/`」 | Phase 23 | 同上 |
| `docs/deployment/` 的 client IP 限制與 ShedLock 記載 | `10 §10.7`、`08 §8.7` | Phase 23 | **兩者既不在 M3-23 的 12 份清單裡,也不在人工確認清單裡**——無自動檢查也無人工檢查 |
| **`dod.sh` 的 workflow 存在性檢查** | 新增 | Phase 23 | 見下 |

### `POST /auth/change-password` 的來歷

ADR 0015 當時把「`User.changePassword` 必須一併撤銷該使用者全部 token family」列為
「M3 實作該端點時必須一併做」——理由是當時沒有呼叫端,先做屬推測性行為(規則 16)。

但盤點發現:**`09` 全文沒有 change-password 端點,M3 的四個 phase 也都沒有承接它**。
「M3 的責任」沒有落到 M3 的任何一個地方。歸給 Phase 21(與稽核同 phase,
因為改密碼是稽核 `action` 清單裡的一項)。

### `dod.sh` 的 workflow 存在性檢查

`13 §13.8` 列 11 支 workflow,其中 **6 支標 M1/M2 卻從未交付**(ADR 0016 Z3)。
之所以拖到現在才發現,是因為 **`dod.sh` 沒有任何一項檢查 workflow 檔案是否存在**
——M3-19 只跑 `gh run list` 看最後一次 run 的結論,「只有兩支且都綠」照樣通過。

Phase 23 補上這項檢查,讓同樣的逾期不會再發生一次。

---

## 沒有歸位的一項

**`gh` CLI 未安裝**。這是本機環境前置,不是專案交付物——`phase-23.md` 的判準與 M3-19 都需要它,
執行該 phase 前須由使用者自行安裝(已於 `15 §15.3` 註明,ADR 0016 Z9)。
