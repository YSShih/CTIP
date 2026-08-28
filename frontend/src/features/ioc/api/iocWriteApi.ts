import { apiGet, apiPost, apiPostRaw, type ApiSchemas } from '../../../api/client';
import type { IocDto } from '../types';

/** IOC 寫入端點的薄包裝(§9.1「IOC — 寫入」、§9.7)。 */

export type IocSubmitRequest = ApiSchemas['IocSubmitRequest'];
export type ImportJobDto = ApiSchemas['ImportJobDto'];
export type FalsePositiveRequest = ApiSchemas['FalsePositiveRequest'];

/** 匯入的兩種格式(§9.7);Content-Type 決定後端怎麼解碼。 */
export const IMPORT_CONTENT_TYPES = {
  CSV: 'text/csv',
  STIX_BUNDLE: 'application/json',
} as const;

export type ImportFormat = keyof typeof IMPORT_CONTENT_TYPES;

export function submitIoc(body: IocSubmitRequest): Promise<IocDto> {
  return apiPost('/api/v1/iocs', body) as Promise<IocDto>;
}

/** 原文直送:CSV 與 STIX bundle 都不能再被 JSON.stringify 包一層。 */
export function importIocs(format: ImportFormat, payload: string): Promise<ImportJobDto> {
  return apiPostRaw('/api/v1/iocs/import', payload, IMPORT_CONTENT_TYPES[format]);
}

export function fetchImportJob(jobId: string): Promise<ImportJobDto> {
  return apiGet('/api/v1/iocs/import/{jobId}', { path: { jobId } });
}

export function reportFalsePositive(id: string, body: FalsePositiveRequest): Promise<IocDto> {
  return apiPost('/api/v1/iocs/{id}/report-false-positive', body, {
    path: { id },
  }) as Promise<IocDto>;
}
