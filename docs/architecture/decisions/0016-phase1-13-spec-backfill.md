# ADR 0016 — Phase 1–13 的規格漏補

- **狀態**:accepted
- **日期**:2026-08-28
- **範圍**:`01`、`05 §5.4`、`06 §6.1.2`、`09`、`13 §13.1/§13.8`、`14 §14.7`、`15 §15.3`;
  `ArchitectureTest`、`ConfigSymmetryTest`、compose 與五份 `.env.*.example`
- **背景**:盤點 Phase 14–23 的阻斷項時,使用者指出「前面 phase 規格如果有漏掉的也要補」。
  本 ADR 處理**已完成的 Phase 1–13 留下的缺口**——這些不是「以後會踩到」,是現在契約就已經不成立。

---

## Z1:ArchUnit 規則 1 的 Jackson 防線是空的

`ArchitectureTest.rule1DomainMustNotDependOnFrameworks` 禁止 domain 依賴 `com.fasterxml.jackson..`。
但 **Boot 4 的 Jackson 是 3.x,套件是 `tools.jackson..`**——這件事 Phase 8 就發現並回寫進
`06 §6.3.6` 第 6 條,**卻沒有回頭同步 Phase 4 建立的這條規則**。

結果:規則字面上還在,實際擋不住任何東西。IDE 自動 import `tools.jackson.databind.ObjectMapper`
到 domain 類別會直接放行。

**修法**:規則 1 加上 `tools.jackson..`。
**驗證**:在 `com.ctip.domain` 加一個 `tools.jackson` 依賴 → 測試轉紅;移除 → 轉綠。

## Z2:11 個環境變數三處皆未宣告

`application.yml` 使用了 **11 個**在 compose、四份 `.env.*.example`、`05 §5.4` 三處都沒有宣告的變數:

```
RATE_LIMIT_ANONYMOUS_PER_MINUTE   RATE_LIMIT_ANONYMOUS_PER_DAY
SOURCE_SYNC_CRON   IOC_EXPIRY_CRON   INGESTION_RETRY_CRON
NORMALIZATION_STRIP_WWW   DOMAIN_ALLOWLIST
STIX_EXPORT_MAX_OBJECTS
API_DEFAULT_PAGE_SIZE   API_MAX_PAGE_SIZE   API_MAX_BATCH_LOOKUP
```

來自 Phase 6 / 8 / 9。**後果不是理論性的**:compose 的 backend 環境變數是**明列白名單**
(沒有 `env_file`),未列進 compose 的變數,使用者寫進 `.env` 也到不了容器——
設定看似可調,實際完全無效。

諷刺的是 `application.yml` 自己在兩處註解裡引用 §5.5 對稱性規則來**拒絕**開放其他變數
(`refresh-token-family-max-days`、`login-max-failed-attempts`),卻同時違反它 11 次。
而 `05 §5.4.5` 的註腳早就自陳「這是第三項缺陷(變數宣告與使用不對稱)」——v1.1 犯過一次,
v2.0 補齊後又犯了第二次。

**修法**:compose + 五份 `.env.*.example` + `05 §5.4` 三處補齊(另補 `SERVER_PORT`,
它一直只存在於 compose 與 application.yml)。

**這次加上自動檢查**:`ConfigSymmetryTest` 斷言 `application.yml` 的每一個 `${VAR}`
都宣告於 compose、且列於 `05 §5.4`。人工比對已經守不住兩次了。

> `.env.*.example` 樣板只列該環境需要覆寫的項目,有 compose 預設值者不強制出現,
> 故不在自動檢查範圍——**硬性要求是「能到得了容器」**。

## Z7:`15 §15.5` 明文要求的 ArchUnit 擴充從未實作

`15 §15.5` 對人工項 P-02(Ubiquitous Language 被遵守)寫著:
「**P-02 的可自動化部分必須實作**(列為 ArchUnit 規則的擴充),剩餘部分才算人工項。」

`ArchitectureTest` 只有 rule1–rule9,這條擴充不存在,而且**沒有任何 DoD 項目檢查它**
——一條寫在強制規格裡、沒人做、也沒人會發現的要求。

**修法**:新增 **規則 10**,禁止 `02 §2.1` 詞彙表「常見誤用」欄列出的具體類別名
(`Ioc`、`Observable`、`SourceRecord`、`Feed`、`Provider`、`HashType`、`Organization`、`Tier` 等)
出現在 `com.ctip.domain..` 與 `com.ctip.sdk..`。語意層的詞彙遵守仍屬人工項。
**驗證**:建立 `domain/source/Feed.java` → 規則 10 轉紅。

## 其餘文件回補

| # | 漏補 | 處置 |
|---|---|---|
| Z3 | `.github/workflows/` 只有 2 支;13 §13.8 標 **M1** 的 4 支與標 M2 的 2 支全部逾期,Phase 1–12 執行單皆未列,`dod.sh` 也不檢查 workflow 是否存在 | 於 13 §13.8 與 phase-23 註記為逾期件並一次補齊;Phase 23 增設「11 支檔案皆存在」的 DoD 檢查。**內容已由本機判準涵蓋,是自動化缺口而非品質缺口** |
| Z4 | `09` 檔尾宣稱「端點數:47」,實列 **43** | 改為 43 |
| Z5 | `13 §13.1` 的演進圖寫「M1–M2:程序內 listener」,但全庫 `@EventListener` **零命中** | 加註:發佈端已就位、消費端尚無需求是刻意的;Phase 20 的 `KafkaForwardingListener` 是第一個消費者 |
| Z6 | `01` 的 `application/search/SearchService` 實際叫 `IndicatorQueryService` 且在 `indicator/` | 加註實況;獨立 `search/` 套件待 Phase 19 的降級邏輯才成立 |
| Z8 | `06 §6.1.2` 規定 image 用 major 浮動 tag,但 compose 與 §5.6 骨架把 Kafka/ES/Prometheus/Grafana 釘死 patch | 明確化:浮動只適用資料面元件(postgres/redis/nginx);那四個的 minor/patch 會改 API 與 dashboard schema,**維持精確 pin** |
| Z9 | `15 §15.3` 自稱「25 項全部可執行」,但 M3-17 需要 gitignore 的 `.env.prod`、M3-19 需要未安裝的 `gh` | 註明兩項前置須先備妥 |
| Z10 | `db/seed/sample_data.sql` 無方案／訂閱樣本,而 `14 §14.7` 要求 | 加註原因(`plans` 表要 Phase 14 的 `V28` 才存在)並**寫進 phase-14 交付物**;Phase 16 的 `SyncEndToEndTest` 依賴它 |
| — | phase-14 執行單寫「15 個配額維度」,實際 **14** | 改為 14 |

---

## 為什麼這些現在才被發現

前十三個 phase 的收尾都做了規格回寫(§0.7–§0.17),但回寫的範圍一律是「**本 phase 做了什麼、
偏離了什麼**」。Z1、Z2、Z7 這一類是**跨 phase 的**:Phase 8 發現 Jackson 3 卻沒回頭看 Phase 4 的規則、
Phase 6/8/9 各自加了幾個變數卻沒人重新檢查對稱性、Phase 1 就該做的 ArchUnit 擴充寫在 `15` 而不在
任何 phase 的執行單裡。逐 phase 的回寫抓不到這種缺口。

因應:Z2 與 Z7 都已改為**自動檢查**(`ConfigSymmetryTest`、規則 10),不再依賴人工複查。
