import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../../api/client';
import {
  createApiKey,
  fetchApiKeys,
  revokeApiKey,
  type ApiKeyCreateRequest,
  type ApiKeyDto,
  type IssuedApiKeyDto,
} from '../api/apiKeyApi';

/** Query key 慣例(§12.3):['apikey', 'list']。 */
export function useApiKeys() {
  return useQuery<ApiKeyDto[], ApiError>({
    queryKey: ['apikey', 'list'],
    queryFn: fetchApiKeys,
  });
}

export function useCreateApiKey() {
  const queryClient = useQueryClient();
  return useMutation<IssuedApiKeyDto, ApiError, ApiKeyCreateRequest>({
    mutationFn: createApiKey,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['apikey', 'list'] }),
  });
}

export function useRevokeApiKey() {
  const queryClient = useQueryClient();
  return useMutation<void, ApiError, string>({
    mutationFn: revokeApiKey,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['apikey', 'list'] }),
  });
}
