import { apiDelete, apiGet, apiPost, type ApiSchemas } from '../../../api/client';

/** API key 端點的薄包裝(§9.1、§10.5)。 */

export type ApiKeyDto = ApiSchemas['ApiKeyDto'];
export type IssuedApiKeyDto = ApiSchemas['IssuedApiKeyDto'];
export type ApiKeyCreateRequest = ApiSchemas['ApiKeyCreateRequest'];

export function fetchApiKeys(): Promise<ApiKeyDto[]> {
  return apiGet('/api/v1/api-keys');
}

export function createApiKey(body: ApiKeyCreateRequest): Promise<IssuedApiKeyDto> {
  return apiPost('/api/v1/api-keys', body);
}

export function revokeApiKey(id: string): Promise<void> {
  return apiDelete('/api/v1/api-keys/{id}', { path: { id } });
}
