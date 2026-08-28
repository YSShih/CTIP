# Phase 17 — Redis（快取 + 分散式限流）  `[M2]`

## 前置條件
- Phase 16 完成判準全綠

## 交付物
- `CachePort` + `RedisCacheAdapter`
- `RedisRateLimiter`（Bucket4j + `bucket4j-redis`），依 `RATE_LIMIT_BACKEND` 切換
- 五個限流維度（api key / user / tenant / ip / endpoint class），由 specific 到 general 依序檢查
- IPv6 正規化至 `/64`
- `X-RateLimit-*` 三個標頭在**所有**回應皆帶上
- `server.forward-headers-strategy=framework` + 信任代理設定
- 測試：`DistributedRateLimitTest`（Testcontainers 起**兩個** app 實例）

## 治理規格
- [10-identity-plans.md §10.7](../10-identity-plans.md#107-限流)
- [06-tech-stack.md §6.5](../06-tech-stack.md#65-授權注意事項)（Valkey 替代）

## 完成判準
```bash
./backend/mvnw -f backend/pom.xml test -Ptest-integration -Dtest='DistributedRateLimitTest,QuotaEnforcementTest'
```
`DistributedRateLimitTest` 必須驗證：兩個實例共用同一配額桶（單實例耗盡後另一實例也被拒）。

## 不得做的事
- 不得用 Decorator 堆疊實作限流（用單一 filter 或 `HandlerInterceptor`）
- 不得讓 `CachePort` 洩漏 Lettuce/Redis 型別到 application 層
- 不得只用完整 IPv6 位址做限流鍵（必須 `/64`）
- 不得在無法確定真實 client IP 時靜默略過——必須在 `docs/deployment/` 記載限制
