# ADR 0037 — 停用 CSRF 保護

- 狀態:accepted(2026-08-30,Phase 23 補記;實作於 Phase 13 的 `SecurityConfig`)
- 範圍:[13 §13.3](../../spec/13-platform-ops.md#133-安全)、[10 §10.4](../../spec/10-identity-plans.md)、
  `SecurityConfig`;**本 ADR 是 §13.3 明文要求的那一則**
  (「必須在 `docs/architecture/security.md` 明確記錄此決策與理由(ADR)」)

## 背景

Spring Security 預設對所有非安全方法(POST/PUT/PATCH/DELETE)啟用 CSRF token 檢查。
CTIP 的 API 有大量寫入端點(IOC 提交/匯入、webhook 管理、API key、改密碼),
若沿用預設,每個寫入端點都需要 client 先取 token 再帶回。

## 決策

**停用 CSRF 保護**(`http.csrf(CsrfConfigurer::disable)`)。

## 理由

CSRF 攻擊成立的前提是**瀏覽器會自動附帶憑證**(cookie、HTTP Basic、TLS client cert)。
CTIP 的認證完全不符合這個前提:

| 憑證 | 攜帶方式 | 瀏覽器會自動附帶嗎 |
|---|---|---|
| JWT access token | `Authorization: Bearer <jwt>` | 否——必須由 JS 明確設定 |
| API key | `X-API-Key` 標頭 | 否 |
| WebSocket 認證 | `Sec-WebSocket-Protocol: ctip.auth.<jwt>` | 否 |

- **沒有 cookie session**:refresh token 也是回應 body 裡的值,由 client 自行保管,不放 cookie
- 跨源請求另外受 `CORS_ALLOWED_ORIGINS` 限制,prod 絕不允許 `*`
  (啟動守衛會拒絕啟動,DoD **M3-18**)
- 惡意站台可以發出跨源的 POST,但**帶不上 `Authorization` 標頭**,
  後端一律視為匿名——匿名沒有任何寫入權限([10 §10.3](../../spec/10-identity-plans.md) 的 RBAC 矩陣)

## 重新啟用的條件(強制)

**若日後引入任何 cookie-based session(含「把 refresh token 放進 HttpOnly cookie」這種折衷),
必須同時重新啟用 CSRF 保護。** 這不是建議事項:那個變更會一次讓上表三列全部失效,
而 CSRF 停用的整個論證正是建立在那三列上。

## 後果

- client 端不需要 CSRF token 流程;`docs/api/` 的接收端契約因此不含這一段
- WebSocket 的 token **不接受 query string**([ADR 0021](0021-phase20-23-spec-resolutions.md) 第 4 點):
  query string 會進 access log,那是另一個憑證外洩面,與 CSRF 無關但同屬「憑證怎麼帶」的決策
