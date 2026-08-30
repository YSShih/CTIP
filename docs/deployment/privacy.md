# 隱私與個人資料處理

> 本文件為 [13-platform-ops.md §13.4](../spec/13-platform-ops.md#134-隱私與資料保留) 要求的部署文件,
> 說明 CTIP 處理哪些個人資料、法律基礎、保留期,以及資料主體權利的操作程序。
> DoD 的 **M3-23**(文件齊備)會檢查本檔存在且非空。

## 1. 平台處理的個人資料

CTIP 是威脅情資平台,**不主動關聯 IOC 與可識別自然人**(§13.4)。
會構成個人資料的只有下列三處,全部來自平台自身的使用者與存取記錄:

| 位置 | 欄位 | 為什麼需要 |
|---|---|---|
| `users`(表 10) | `email`、`display_name`、`last_login_at` | 帳號識別與登入 |
| `refresh_tokens`(表 15) | `ip`、`user_agent` | 憑證竊用偵測(不變量 U5 的重用偵測需要知道「從哪裡來的」) |
| `audit_logs`(表 27) | `ip`、`user_agent`、`actor_id` | 稽核軌跡(§13.5) |

⚠️ **IP 位址在 GDPR 下可能構成個人資料**,因此 `audit_logs.ip` 與 `refresh_tokens.ip`
一律受保留政策約束(見 §3)。

情資本身(`indicators`、`indicator_sources`)可能包含 email 型 IOC——那是**被通報的惡意位址**,
不是平台使用者的個資,不在本文件的資料主體程序範圍內。

## 2. 法律基礎

處理上述資料的法律基礎是**正當利益**(GDPR Art. 6(1)(f)):
**網路與資訊安全**。GDPR Recital 49 明文承認為確保網路與資訊系統安全而處理個人資料
(包含防止未經授權的存取)構成正當利益。

具體而言:

- 登入與憑證資料是提供服務所必需;
- `refresh_tokens` 的 IP／user-agent 用於偵測憑證竊用(U4–U6 的輪替與重用偵測);
- `audit_logs` 用於事後調查「誰在什麼時候對什麼做了什麼」——多租戶情資平台若無此軌跡,
  資料外洩事件將無法追溯。

## 3. 保留期

保留政策由環境變數控制,清理任務每日/每週自動執行
([13 §13.4](../spec/13-platform-ops.md#134-隱私與資料保留)、[08 §8.7](../spec/08-ingestion-sdk.md#排程)):

| 資料 | 保留期 | 變數 |
|---|---|---|
| `audit_logs`(含 `ip`、`user_agent`) | 180 天 | `AUDIT_RETENTION_DAYS` |
| `indicator_sources.raw_payload` | 30 天後清空該欄位 | `RAW_PAYLOAD_RETENTION_DAYS` |
| `ingestion_rejections` | 30 天 | `REJECTION_RETENTION_DAYS` |
| `webhook_deliveries` | 30 天 | `DELIVERY_RETENTION_DAYS` |
| `EXPIRED` 的 indicator | 1 年後軟刪除 | `INDICATOR_RETENTION_DAYS` |
| Bloom artifact | 最近 30 個版本 | `BLOOM_ARTIFACT_KEEP` |

`refresh_tokens` 沒有獨立的保留期:輪替時舊枚即撤銷,登出與改密碼會撤銷整個 family,
資料主體刪除會整列刪除(見 §4)。

清理任務以**專用資料庫角色 `ctip_retention`** 執行,該角色只有各表時間欄位的
**欄位層級** SELECT 與必要的 DELETE／UPDATE——它讀不到稽核內容
(唯一的例外是 Bloom artifact 清理,它需要讀版本鏈並刪除檔案,由應用角色執行;
它刪的是平台自己的衍生產物,不涉及個資,見 [ADR 0031](../architecture/decisions/0031-phase21-audit-and-retention.md) 第 3 節)。

## 4. 資料主體權利的操作程序

兩支管理端點,權限 `system:admin`(每一次呼叫都會留下 `ADMIN_ACTION` 稽核):

### 4.1 查詢(Art. 15 存取權)

```bash
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/admin/data-subjects/<userId>
```

回傳:使用者列的內容、尚未撤銷的 refresh token 數(每一列都帶 IP 與 user-agent)、
稽核軌跡的**筆數與時間範圍**。

稽核內容本身不回傳:一列稽核可能同時涉及其他人的操作,把它整批交出去會造成新的洩漏。
若資料主體要求稽核內容,由管理者依個案以資料庫查詢處理,並先評估第三方資料的遮蔽。

### 4.2 刪除(Art. 17 被遺忘權)

```bash
curl -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/admin/data-subjects/<userId>
```

執行的動作:

1. 刪除該使用者**全部** refresh token(列本身刪除,`ip`／`user_agent` 隨之消失);
2. 使用者的可識別欄位以佔位值取代(`<userId>@erased.invalid`)、顯示名稱清空、帳號停權。
   **列本身保留**——`tenant_users`、`api_keys` 等以外鍵指向它,那些列承載的是租戶的營運事實。

**`audit_logs` 不在刪除範圍內。** 稽核軌跡是 append-only 的(§13.5 規則 1,由資料庫的
`REVOKE UPDATE, DELETE` 強制),刪除權在此讓位給:

- GDPR Art. 17(3)(b):為遵守法律義務或執行公共利益任務所必需的處理;
- 上述 §2 的正當利益(資安事件的可追溯性)。

刪除之後,稽核列上關於該資料主體的欄位只剩 `actor_id` 這個**化名識別碼**——
它已經對應不到任何可識別欄位(email 已抹除),並於 180 天後隨保留政策消失。
刪除端點的回應會明確告知仍保留幾列稽核紀錄。

### 4.3 更正(Art. 16)

顯示名稱由使用者自行維護;email 的更正目前沒有自助管道(M2 無寄信基礎設施,
[ADR 0015](../architecture/decisions/0015-future-phase-hardening.md) 已列為已知殘餘風險),
需由管理者直接處理資料庫。

## 5. 稽核的內容限制

`audit_logs.metadata` **絕不含**憑證、token 原文、密碼或完整的 `Authorization` 標頭
(§13.5 規則 5)。這由寫入端的過濾強制(命中禁用鍵一律以 `[redacted]` 取代),
並有單元測試把關,不是靠呼叫端自律。

## 6. 部署者檢查清單

- [ ] 依組織的法遵要求調整 `AUDIT_RETENTION_DAYS`(預設 180 天);縮短會同時縮短可追溯期間
- [ ] 確認 `ctip_retention` 角色存在(`environment/config/postgres/01-app-roles.sh`;
      initdb 只在資料目錄為空時執行一次,既有資料庫需手動補跑)
- [ ] 確認 `SCHEDULER_ENABLED=true`,否則保留清理不會執行,個資會無限期留存
- [ ] 反向代理下確認 `TRUSTED_PROXIES` 設定正確,否則 `audit_logs.ip` 記到的是代理的位址
      (見 `rate-limiting.md`)
- [ ] 依當地法規準備隱私權政策與資料處理紀錄(本檔只涵蓋平台實作面)
