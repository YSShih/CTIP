import { apiPost, type ApiSchemas } from '../../../api/client';

/** 認證端點的薄包裝(§9.1);型別全部由 generated schema 推導。 */

export type AuthResponse = ApiSchemas['AuthResponse'];
export type RegisterRequest = ApiSchemas['RegisterRequest'];
export type LoginRequest = ApiSchemas['LoginRequest'];
export type ChangePasswordRequest = ApiSchemas['ChangePasswordRequest'];
export type ChangePasswordResponse = ApiSchemas['ChangePasswordResponse'];

export function register(body: RegisterRequest): Promise<AuthResponse> {
  return apiPost('/api/v1/auth/register', body);
}

export function login(body: LoginRequest): Promise<AuthResponse> {
  return apiPost('/api/v1/auth/login', body);
}

/** autoRefresh 必須關閉:本端點就是輪替本身,401 時再觸發輪替會等待自己。 */
export function refresh(refreshToken: string): Promise<AuthResponse> {
  return apiPost('/api/v1/auth/refresh', { refreshToken }, { autoRefresh: false });
}

/**
 * 變更密碼。後端會撤銷該使用者**全部** refresh token family(ADR 0015),
 * 包含呼叫端自己這一枚——回應的 revokedSessions 即為被撤銷的數量。
 */
export function changePassword(body: ChangePasswordRequest): Promise<ChangePasswordResponse> {
  return apiPost('/api/v1/auth/change-password', body);
}

export function logout(refreshToken: string): Promise<void> {
  return apiPost('/api/v1/auth/logout', { refreshToken }) as Promise<void>;
}
