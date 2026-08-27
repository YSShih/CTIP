import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { apiPost } from '../api/client';
import {
  sessionCleared,
  selectIsAuthenticated,
  selectRefreshToken,
  type AuthUser,
} from '../stores/authSlice';
import { useAppDispatch, useAppSelector } from '../stores/hooks';

/**
 * 版面與跨 feature 元件共用的 session 動作(§12.2:layouts/ 不得 import features/**)。
 * 登入與註冊屬 auth feature;登出因為要掛在全站 header 而置於此。
 */

export function useIsAuthenticated(): boolean {
  return useAppSelector(selectIsAuthenticated);
}

export function useCurrentUser(): AuthUser | null {
  return useAppSelector((state) => state.auth.user);
}

export function useHasPermission(permission: string): boolean {
  return useAppSelector((state) => state.auth.permissions.includes(permission));
}

/** 登出:先撤銷 refresh token family(§10.4),再清空本地 session 與所有 Query 快取。 */
export function useLogout(): () => Promise<void> {
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();
  const refreshToken = useAppSelector(selectRefreshToken);
  return useCallback(async () => {
    if (refreshToken) {
      // 伺服器端撤銷失敗(例如已過期)不應阻擋本地登出
      await apiPost('/api/v1/auth/logout', { refreshToken }).catch(() => undefined);
    }
    dispatch(sessionCleared());
    await queryClient.invalidateQueries();
  }, [dispatch, queryClient, refreshToken]);
}
