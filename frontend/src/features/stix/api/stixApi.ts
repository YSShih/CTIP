import { apiGet } from '../../../api/client';
import type { StixObject } from '../types';

export async function fetchStixObject(stixId: string): Promise<StixObject> {
  const object = await apiGet('/api/v1/stix/{stixId}', { path: { stixId } });
  return object as StixObject;
}
