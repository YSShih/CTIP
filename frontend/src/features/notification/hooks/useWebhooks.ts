import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../../api/client';
import {
  createWebhook,
  deleteWebhook,
  fetchWebhooks,
  type IssuedWebhookDto,
  type WebhookCreateRequest,
  type WebhookDto,
} from '../api/notificationApi';

/** Query key 慣例(§12.3):['webhooks']。 */
export function useWebhooks() {
  return useQuery<WebhookDto[], ApiError>({
    queryKey: ['webhooks'],
    queryFn: fetchWebhooks,
  });
}

export function useCreateWebhook() {
  const queryClient = useQueryClient();
  return useMutation<IssuedWebhookDto, ApiError, WebhookCreateRequest>({
    mutationFn: createWebhook,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['webhooks'] });
    },
  });
}

export function useDeleteWebhook() {
  const queryClient = useQueryClient();
  return useMutation<void, ApiError, string>({
    mutationFn: deleteWebhook,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['webhooks'] });
    },
  });
}
