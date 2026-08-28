import { useState, type ChangeEvent, type FormEvent } from 'react';
import { ApiError } from '../../../api/client';
import { Button } from '../../../components/ui/button';
import { Select } from '../../../components/ui/select';
import type { ImportFormat } from '../api/iocWriteApi';

export interface IocImportFormProps {
  submitting: boolean;
  error: ApiError | null;
  onSubmit: (values: { format: ImportFormat; payload: string }) => void;
}

/** 錯誤碼 → 說明:413 是「這一次太大,拆小就能過」,403 是「方案不支援」(§9.7)。 */
function describe(error: ApiError): string {
  switch (error.code) {
    case 'PAYLOAD_TOO_LARGE':
      return `檔案筆數超過方案上限,請拆成多個檔案:${error.message}`;
    case 'PLAN_LIMIT_EXCEEDED':
      return '目前的方案不允許批次匯入,升級方案後才能使用。';
    case 'UNSUPPORTED_MEDIA_TYPE':
      return '只接受 CSV 或 STIX 2.1 bundle。';
    case 'INVALID_REQUEST':
      return `檔案無法解析:${error.message}`;
    default:
      return error.message;
  }
}

const CSV_TEMPLATE =
  'type,value,confidence,severity,tags,note\nDOMAIN,evil.example.org,80,HIGH,,\n';

export function IocImportForm({ submitting, error, onSubmit }: IocImportFormProps) {
  const [format, setFormat] = useState<ImportFormat>('CSV');
  const [payload, setPayload] = useState('');
  const [fileName, setFileName] = useState('');

  async function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    setFileName(file.name);
    setPayload(await file.text());
    setFormat(file.name.toLowerCase().endsWith('.json') ? 'STIX_BUNDLE' : 'CSV');
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit({ format, payload });
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit} noValidate>
      <div className="space-y-1.5">
        <label htmlFor="import-format" className="text-sm font-medium">
          格式
        </label>
        <Select
          id="import-format"
          value={format}
          onChange={(event) => setFormat(event.target.value as ImportFormat)}
        >
          <option value="CSV">CSV</option>
          <option value="STIX_BUNDLE">STIX 2.1 bundle</option>
        </Select>
      </div>

      <div className="space-y-1.5">
        <label htmlFor="import-file" className="text-sm font-medium">
          檔案
        </label>
        <input
          id="import-file"
          type="file"
          accept=".csv,text/csv,.json,application/json"
          className="block w-full text-sm"
          onChange={(event) => void handleFile(event)}
        />
        {fileName === '' ? null : (
          <p className="font-mono text-xs text-muted-foreground">{fileName}</p>
        )}
      </div>

      <div className="space-y-1.5">
        <label htmlFor="import-payload" className="text-sm font-medium">
          內容(可直接貼上)
        </label>
        <textarea
          id="import-payload"
          rows={8}
          spellCheck={false}
          className="w-full rounded-md border bg-surface p-2 font-mono text-xs text-surface-foreground"
          placeholder={CSV_TEMPLATE}
          value={payload}
          onChange={(event) => setPayload(event.target.value)}
        />
        <p className="text-xs text-muted-foreground">
          CSV 需有表頭,只有 value 是必填;匯入的 IOC 一律是租戶私有(TLP:AMBER)。
        </p>
      </div>

      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {describe(error)}
        </p>
      ) : null}

      <Button type="submit" disabled={submitting || payload.trim() === ''}>
        {submitting ? '上傳中…' : '開始匯入'}
      </Button>
    </form>
  );
}
