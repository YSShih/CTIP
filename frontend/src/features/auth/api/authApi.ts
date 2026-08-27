import { apiPost, type ApiSchemas } from '../../../api/client';

/** 認證端點的薄包裝(§9.1);型別全部由 generated schema 推導。 */

export type AuthResponse = ApiSchemas['AuthResponse'];
export type RegisterRequest = ApiSchemas['RegisterRequest'];
export type LoginRequest = ApiSchemas['LoginRequest'];

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

export function logout(refreshToken: string): Promise<void> {
  return apiPost('/api/v1/auth/logout', { refreshToken }) as Promise<void>;
}
