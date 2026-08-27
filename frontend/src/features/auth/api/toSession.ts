import type { AuthResponse } from './authApi';

/** AuthResponse → authSlice 的 session payload;displayName 缺席時以 email 前綴退而求其次。 */
export function toSessionPayload(response: AuthResponse, fallbackName: string) {
  const user = response.user;
  return {
    accessToken: response.accessToken ?? '',
    refreshToken: response.refreshToken ?? '',
    user: {
      id: user?.userId ?? '',
      name: user?.displayName ?? fallbackName,
    },
    tenantId: user?.tenantId ?? '',
    role: user?.role ?? '',
    permissions: user?.permissions ?? [],
  };
}
