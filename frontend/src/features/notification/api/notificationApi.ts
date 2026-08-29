import {
  apiDelete,
  apiGet,
  apiPatch,
  apiPost,
  type ApiSchemas,
  type PageOf,
} from '../../../api/client';

/** 通知與 webhook 端點的薄包裝(§9.1「通知與稽核」)。 */

export type NotificationDto = ApiSchemas['NotificationDto'];
export type WebhookDto = ApiSchemas['WebhookDto'];
export type IssuedWebhookDto = ApiSchemas['IssuedWebhookDto'];
export type WebhookCreateRequest = ApiSchemas['WebhookCreateRequest'];

export interface NotificationPageParams {
  cursor?: string;
  limit?: number;
  unreadOnly?: boolean;
}

export function fetchNotifications(
  params: NotificationPageParams = {},
): Promise<PageOf<NotificationDto>> {
  return apiGet('/api/v1/notifications', { query: params }) as Promise<PageOf<NotificationDto>>;
}

export function markNotificationRead(id: string): Promise<void> {
  return apiPatch('/api/v1/notifications/{id}/read', { path: { id } });
}

export function fetchWebhooks(): Promise<WebhookDto[]> {
  return apiGet('/api/v1/webhooks');
}

export function createWebhook(request: WebhookCreateRequest): Promise<IssuedWebhookDto> {
  return apiPost('/api/v1/webhooks', request);
}

export function deleteWebhook(id: string): Promise<void> {
  return apiDelete('/api/v1/webhooks/{id}', { path: { id } });
}
