# ADR 0035 — 兩層 Bloom Filter(public / tenant)

- 狀態:accepted(2026-08-30,Phase 23 補記;實作於 Phase 15–16,見 [ADR 0024](0024-phase15-bloom-decisions.md)/[0025](0025-phase16-sync-api-decisions.md))
- 範圍:[11 §11.1–§11.4](../../spec/11-sync-bloom.md)、[02 不變量 L1–L8](../../spec/02-ddd-model.md#bloomversion)、
  [07 §7.7](../../spec/07-domain-intel.md#tlp-可見度)

## 背景

Browser Extension / App 要能離線判斷「這個網域是否可能在情資集合中」。
直覺作法是每個租戶一份 Bloom。以 10M 容量 @ 0.1% FPR 計算,單份約 18 MB;
100 個租戶每次重建就是 1.8 GB——**每天**。

## 決策

兩層,不是每租戶一層:

```text
public bloom  ← 所有 TLP:CLEAR、status=ACTIVE、且可再散布的 IOC
                全體共用一份,可放 CDN,無租戶隔離問題
tenant bloom  ← 僅含該租戶的私有 IOC(AMBER / AMBER_STRICT)
                通常小兩到三個數量級
```

`TLP:GREEN` **不建第三份 Bloom**。

## 理由

1. **成本與隔離同時解決**:公開層沒有租戶維度,因此可以放 CDN 而不需要認證閘;
   私有層只含該租戶自己產生的 IOC,量級小到可以按需重建。
2. **不為 `GREEN` 另建一層**,理由是抽象判準([01 §1.7](../../spec/01-architecture.md#17-抽象判準強制)):
   那需要新增一個 `BloomScope` 成員、一條發布路徑,以及「CDN 上的認證閘」這個部署問題,
   而 `GREEN` 目前零資料量。擴充點已經留好——`BloomScope` enum 加一個成員即可。
3. **`GREEN` 沒有 Bloom 覆蓋是安全結論,不是遺漏**:TLP 2.0 明確排除把 `GREEN`
   放上公開可存取通道,而 public bloom 的定位就是公開通道。

## 後果(必須寫進 client 文件,不變量 L8)

```text
Bloom 說 NOT PRESENT → 一定不在「此 Bloom 的成員集合」中
Bloom 說 PRESENT     → 只是可能 → 必須再呼叫 API 精確驗證
Bloom miss           → 只代表「不在公開(CLEAR)集合」,不代表安全
```

- **系統絕不得將 Bloom 命中視為確定惡意**([00 §0.4](../../spec/00-master.md#04-coding-llm-執行規則) 規則 13)。
  API 文件([`docs/api/sync-client-contract.md`](../../api/sync-client-contract.md))、
  SDK 文件與前端 `/sync` 頁都必須明文寫出這兩條限制,`SyncPage` 的測試會驗這段文案存在
- 位元陣列格式是**互通性關鍵**,寫死在 [11 §11.4](../../spec/11-sync-bloom.md#114-位元陣列格式強制互通性關鍵):
  任何調整都是 client 的破壞性變更
