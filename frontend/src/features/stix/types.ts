import type { paths } from '../../api/generated/schema';

/** §12.4:窄化 generated 型別。STIX content 在 openapi 為自由 JSON,窄化為物件檢視。 */

type StixResponse =
  paths['/api/v1/stix/{stixId}']['get']['responses'][200]['content']['application/json'];

export type StixObject = StixResponse & Record<string, unknown>;
