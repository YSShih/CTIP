import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../../api/client';
import { sessionCleared, sessionEstablished } from '../../../stores/authSlice';
import { useAppDispatch } from '../../../stores/hooks';
import {
  changePassword,
  login,
  register,
  type ChangePasswordRequest,
  type LoginRequest,
  type RegisterRequest,
} from '../api/authApi';
import { toSessionPayload } from '../api/toSession';

function emailLocalPart(email: string): string {
  return email.split('@')[0] ?? email;
}

/** 登入:成功後建立 session 並清空所有 Query 快取(可見度隨身分改變,舊資料一律作廢)。 */
export function useLogin() {
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();
  return useMutation<void, ApiError, LoginRequest>({
    mutationFn: async (body) => {
      const response = await login(body);
      dispatch(sessionEstablished(toSessionPayload(response, emailLocalPart(body.email ?? ''))));
      await queryClient.invalidateQueries();
    },
  });
}

export function useRegister() {
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();
  return useMutation<void, ApiError, RegisterRequest>({
    mutationFn: async (body) => {
      const response = await register(body);
      dispatch(sessionEstablished(toSessionPayload(response, emailLocalPart(body.email ?? ''))));
      await queryClient.invalidateQueries();
    },
  });
}

/**
 * 變更密碼(§12.5 /settings)。後端撤銷該使用者**全部** refresh token family——
 * 包含呼叫端自己這一枚,因此成功後必須就地清掉本地 session:
 * 留著一個再也輪替不了的 session,使用者會在 15 分鐘後莫名其妙被踢出。
 *
 * @returns 被撤銷的工作階段數(含目前這一個),供頁面告知使用者
 */
export function useChangePassword() {
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();
  return useMutation<number, ApiError, ChangePasswordRequest>({
    mutationFn: async (body) => {
      const response = await changePassword(body);
      dispatch(sessionCleared());
      await queryClient.invalidateQueries();
      return response.revokedSessions ?? 0;
    },
  });
}
