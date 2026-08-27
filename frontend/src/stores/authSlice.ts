import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

/**
 * §12.3:access token / 使用者身分 / 權限集合屬 Redux(不得進 Query,會被快取失效清掉)。
 * refresh token 同樣只存在記憶體——不寫 localStorage,避免 XSS 直接取得長效憑證。
 */

export interface AuthUser {
  id: string;
  name: string;
}

export interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: AuthUser | null;
  tenantId: string | null;
  role: string | null;
  permissions: string[];
}

const initialState: AuthState = {
  accessToken: null,
  refreshToken: null,
  user: null,
  tenantId: null,
  role: null,
  permissions: [],
};

interface SessionPayload {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
  tenantId: string;
  role: string;
  permissions: string[];
}

export const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    sessionEstablished(state, action: PayloadAction<SessionPayload>) {
      state.accessToken = action.payload.accessToken;
      state.refreshToken = action.payload.refreshToken;
      state.user = action.payload.user;
      state.tenantId = action.payload.tenantId;
      state.role = action.payload.role;
      state.permissions = action.payload.permissions;
    },
    /** 輪替只換憑證,身分與權限沿用(後端 claims 不變時無需重繪整個 UI)。 */
    tokensRotated(state, action: PayloadAction<{ accessToken: string; refreshToken: string }>) {
      state.accessToken = action.payload.accessToken;
      state.refreshToken = action.payload.refreshToken;
    },
    sessionCleared() {
      return initialState;
    },
  },
});

export const { sessionEstablished, tokensRotated, sessionCleared } = authSlice.actions;

export interface HasAuthState {
  auth: AuthState;
}

export const selectIsAuthenticated = (state: HasAuthState): boolean =>
  state.auth.accessToken !== null;

export const selectHasPermission = (state: HasAuthState, permission: string): boolean =>
  state.auth.permissions.includes(permission);

export const selectCurrentUser = (state: HasAuthState): AuthUser | null => state.auth.user;

export const selectRefreshToken = (state: HasAuthState): string | null => state.auth.refreshToken;
