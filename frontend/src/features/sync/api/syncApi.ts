import { apiGet, type ApiSchemas } from '../../../api/client';

/**
 * 同步端點的薄包裝(§9.1「同步」、11 §11.5)。
 *
 * 這裡只包 manifest:Bloom 的下載與 delta 套用屬 client SDK(Browser Extension / App)的職責,
 * 前端頁面是說明頁,不在瀏覽器裡重建 18MB 的位元陣列。契約見 docs/api/sync-client-contract.md。
 */

export type SyncManifestDto = ApiSchemas['SyncManifestDto'];
export type BloomManifestDto = ApiSchemas['BloomManifestDto'];

export function fetchSyncManifest(): Promise<SyncManifestDto> {
  return apiGet('/api/v1/sync/manifest');
}
