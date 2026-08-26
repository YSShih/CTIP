import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

/**
 * §12.3:access token / 使用者身分 / 權限集合屬 Redux(不得進 Query)。
 * M1 全匿名;登入 API 於 Phase 13 接上 sessionEstablished(擴充點)。
 */

export interface AuthUser {
  id: string;
  name: string;
}

export interface AuthState {
  accessToken: string | null;
  user: AuthUser | null;
  permissions: string[];
}

const initialState: AuthState = {
  accessToken: null,
  user: null,
  permissions: [],
};

interface SessionPayload {
  accessToken: string;
  user: AuthUser;
  permissions: string[];
}

export const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    sessionEstablished(state, action: PayloadAction<SessionPayload>) {
      state.accessToken = action.payload.accessToken;
      state.user = action.payload.user;
      state.permissions = action.payload.permissions;
    },
    sessionCleared() {
      return initialState;
    },
  },
});

export const { sessionEstablished, sessionCleared } = authSlice.actions;

export interface HasAuthState {
  auth: AuthState;
}

export const selectIsAuthenticated = (state: HasAuthState): boolean =>
  state.auth.accessToken !== null;

export const selectHasPermission = (state: HasAuthState, permission: string): boolean =>
  state.auth.permissions.includes(permission);
