# ADR 0038 — TLP 與方案完全解耦

- 狀態:accepted(2026-08-30,Phase 23 補記;決策見
  [00 §0.6](../../spec/00-master.md#修正的規格衝突10-項) 衝突 #6,實作於 Phase 4/13/14)
- 範圍:[07 §7.7](../../spec/07-domain-intel.md#tlp-可見度)、
  [01 §1.11](../../spec/01-architecture.md#111-m1-最小安全層強制phase-4)、
  [10](../../spec/10-identity-plans.md)、`VisibilityFilter`

## 背景

v1.1 的可見度矩陣同時有「方案」與「認證狀態」兩個維度。實際檢查後發現三個問題:
Free 與 Premium 兩列**完全相同**(都是 `CLEAR`+`GREEN`),Premium 在資料層買不到任何東西;
「Tenant 成員」是獨立一列,但每個登入者本來就是某個 tenant 的成員,兩列語意重疊。

## 決策

**TLP 是資料分級(資料本身與其歸屬的屬性);方案是商業建構。兩者不得耦合。**

| AuthState | 可見範圍 |
|---|---|
| `ANONYMOUS` | public tenant 的 `CLEAR` |
| `AUTHENTICATED` | public tenant 的 `CLEAR` + `GREEN`,**加上**自家 tenant 的全部 TLP |

唯一一套過濾邏輯:

```sql
owner_tenant_id IN (:currentTenantId, '00000000-0000-0000-0000-000000000000')
AND tlp <= :maxVisibleTlp
```

方案的價值全部留在 [10](../../spec/10-identity-plans.md) 的**配額與功能**
(API 速率、Bloom 容量、`stix_export_max_objects`、`max_api_keys`、`websocket_enabled`…),
不進入任何可見度述詞。

## 理由

1. **安全關鍵的 query filter 不放商業邏輯**。把方案接進 TLP 過濾,會迫使
   **每一次查詢都先載入使用者的方案**;那條路徑上任何一次快取失效、降級或預設值,
   都會變成資料可見範圍的變化。可見度述詞必須只依賴兩個穩定輸入:租戶身分與 TLP。
2. **一套邏輯,一處實作**。可見度只有一個述詞,因此 domain(I14)、query 層與輸出層
   套用的是同一條規則;Phase 9 發現的作用域缺陷([00 §0.10](../../spec/00-master.md))
   之所以能一次修乾淨,就是因為只有一處定義。
3. **付費不等於看得更多**是刻意的產品立場:情資的分級由**提供者**決定(TLP),
   不由**購買力**決定。這也讓再散布政策([07 §7.9](../../spec/07-domain-intel.md#79-再散布政策法遵強制))
   的法遵論證成立——多數商業 feed 的授權條款正是這樣寫的。

## 後果

- `public tenant` 同時持有 `CLEAR` 與 `GREEN`;否則 `GREEN` 沒有任何棲息地,整個等級是死的
- 升級方案**不會**讓使用者看到更多情資,只會提高額度與解鎖功能;
  UI 的 `ForbiddenState` 必須據此分辨「需要登入」與「需要升級」兩種原因,不得混為一談
- `RbacMatrixTest` 與可見度測試各自獨立:權限與可見度是兩件事,測試也不得互相代替
