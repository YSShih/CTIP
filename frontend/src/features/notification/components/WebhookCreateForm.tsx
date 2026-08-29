import { useState, type FormEvent } from 'react';
import { ApiError } from '../../../api/client';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import type { WebhookCreateRequest } from '../api/notificationApi';

/** §13.2 的七種通知型別;清單是封閉的,後端以 enum 再擋一次。 */
export const NOTIFICATION_TYPES = [
  'NEW_IOC',
  'THREAT_UPDATED',
  'IOC_REVOKED',
  'SOURCE_FAILURE',
  'SUBSCRIPTION_CHANGED',
  'SYNC_SNAPSHOT_READY',
  'SYSTEM_ALERT',
] as const;

const IOC_TYPES = ['IPV4', 'IPV6', 'DOMAIN', 'URL', 'FILE_HASH', 'EMAIL'] as const;
const SEVERITIES = ['INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;

export interface WebhookCreateFormProps {
  submitting: boolean;
  error: ApiError | null;
  onSubmit: (values: WebhookCreateRequest) => void;
}

export function WebhookCreateForm({ submitting, error, onSubmit }: WebhookCreateFormProps) {
  const [name, setName] = useState('');
  const [targetUrl, setTargetUrl] = useState('https://');
  const [eventTypes, setEventTypes] = useState<string[]>(['NEW_IOC']);
  const [filterIocTypes, setFilterIocTypes] = useState<string[]>([]);
  const [filterMinSeverity, setFilterMinSeverity] = useState('');
  const [filterTags, setFilterTags] = useState('');

  function toggle(list: string[], value: string): string[] {
    return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit({
      name,
      targetUrl,
      eventTypes,
      filterIocTypes,
      filterMinSeverity: filterMinSeverity === '' ? undefined : filterMinSeverity,
      filterTags: filterTags
        .split(',')
        .map((tag) => tag.trim())
        .filter((tag) => tag.length > 0),
      filterSourceIds: [],
    });
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit} noValidate>
      <div className="space-y-1.5">
        <label htmlFor="webhook-name" className="text-sm font-medium">
          名稱
        </label>
        <Input
          id="webhook-name"
          required
          maxLength={128}
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </div>

      <div className="space-y-1.5">
        <label htmlFor="webhook-url" className="text-sm font-medium">
          目標 URL
        </label>
        <Input
          id="webhook-url"
          required
          type="url"
          pattern="https://.*"
          value={targetUrl}
          onChange={(event) => setTargetUrl(event.target.value)}
        />
        <p className="text-xs text-muted-foreground">必須為 https://(不變量 W1)。</p>
      </div>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">訂閱的事件型別</legend>
        <div className="flex flex-wrap gap-3">
          {NOTIFICATION_TYPES.map((eventType) => (
            <label key={eventType} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={eventTypes.includes(eventType)}
                onChange={() => setEventTypes((current) => toggle(current, eventType))}
              />
              <span className="font-mono text-xs">{eventType}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">過濾條件(留空 = 不限)</legend>
        <p className="text-xs text-muted-foreground">
          過濾在伺服器端執行(不變量 W5):不符條件的事件根本不會送到你的端點。 指定 IOC 型別之後,與
          IOC 型別無關的事件(來源失敗、方案異動)也不會送達。
        </p>
        <div className="flex flex-wrap gap-3">
          {IOC_TYPES.map((iocType) => (
            <label key={iocType} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={filterIocTypes.includes(iocType)}
                onChange={() => setFilterIocTypes((current) => toggle(current, iocType))}
              />
              <span className="font-mono text-xs">{iocType}</span>
            </label>
          ))}
        </div>
        <div className="space-y-1.5">
          <label htmlFor="webhook-severity" className="text-sm font-medium">
            最低嚴重度
          </label>
          <select
            id="webhook-severity"
            className="h-9 w-full rounded-md border bg-background px-3 text-sm"
            value={filterMinSeverity}
            onChange={(event) => setFilterMinSeverity(event.target.value)}
          >
            <option value="">不限</option>
            {SEVERITIES.map((severity) => (
              <option key={severity} value={severity}>
                {severity}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1.5">
          <label htmlFor="webhook-tags" className="text-sm font-medium">
            標籤(逗號分隔)
          </label>
          <Input
            id="webhook-tags"
            value={filterTags}
            onChange={(event) => setFilterTags(event.target.value)}
          />
        </div>
      </fieldset>

      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error.message}
        </p>
      ) : null}

      <Button type="submit" disabled={submitting || eventTypes.length === 0}>
        {submitting ? '建立中…' : '建立 webhook'}
      </Button>
    </form>
  );
}
