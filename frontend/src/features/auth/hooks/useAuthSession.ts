import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../../api/client';
import { sessionEstablished } from '../../../stores/authSlice';
import { useAppDispatch } from '../../../stores/hooks';
import { login, register, type LoginRequest, type RegisterRequest } from '../api/authApi';
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
