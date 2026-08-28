import { useState, type FormEvent } from 'react';
import { ApiError } from '../../../api/client';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { Select } from '../../../components/ui/select';
import type { IocSubmitRequest } from '../api/iocWriteApi';
import { SEVERITY_OPTIONS, TYPE_OPTIONS } from '../types';

/**
 * §9.7 的提交表單。可選的 TLP 只有私有的兩級:
 * {@code CLEAR}/{@code GREEN} 需要 {@code ioc:publish},而那是把資料推進公開情資池的
 * 平台營運決策(§10.3),不放在一般提交表單裡;後端仍會再擋一次。
 */
const TLP_OPTIONS = ['AMBER', 'AMBER_STRICT'] as const;

export interface IocSubmitFormProps {
  submitting: boolean;
  error: ApiError | null;
  onSubmit: (values: IocSubmitRequest) => void;
}

/** 錯誤碼 → 使用者看得懂的說明(§9.7 的三種配額語意各有不同的下一步)。 */
function describe(error: ApiError): string {
  switch (error.code) {
    case 'PLAN_LIMIT_EXCEEDED':
      return '目前的方案不允許手動提交,升級方案後才能使用。';
    case 'RATE_LIMIT_EXCEEDED':
      return '今日的提交額度已用罄,請於配額重置後再試。';
    case 'FORBIDDEN':
      return '沒有提交權限,或該 TLP 需要額外的發布權限。';
    case 'INVALID_IOC_FORMAT':
      return `這筆 IOC 未通過資料品質檢查:${error.message}`;
    default:
      return error.message;
  }
}

export function IocSubmitForm({ submitting, error, onSubmit }: IocSubmitFormProps) {
  const [type, setType] = useState<string>('');
  const [value, setValue] = useState('');
  const [severity, setSeverity] = useState<string>('');
  const [tlp, setTlp] = useState<string>('AMBER');
  const [confidence, setConfidence] = useState('');
  const [tags, setTags] = useState('');
  const [note, setNote] = useState('');

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit({
      type: type === '' ? undefined : (type as IocSubmitRequest['type']),
      value,
      severity: severity === '' ? undefined : (severity as IocSubmitRequest['severity']),
      tlp: tlp as IocSubmitRequest['tlp'],
      confidence: confidence === '' ? undefined : Number(confidence),
      tags: tags === '' ? undefined : tags.split(',').map((tag) => tag.trim()),
      note: note === '' ? undefined : note,
    });
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit} noValidate>
      <div className="space-y-1.5">
        <label htmlFor="submit-value" className="text-sm font-medium">
          IOC 值
        </label>
        <Input
          id="submit-value"
          required
          maxLength={2048}
          value={value}
          onChange={(event) => setValue(event.target.value)}
        />
        <p className="text-xs text-muted-foreground">
          留空型別時由平台推斷;值會經過正規化與去重,重複提交會合併到既有的 IOC。
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5">
          <label htmlFor="submit-type" className="text-sm font-medium">
            型別
          </label>
          <Select id="submit-type" value={type} onChange={(event) => setType(event.target.value)}>
            <option value="">自動判斷</option>
            {TYPE_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </Select>
        </div>

        <div className="space-y-1.5">
          <label htmlFor="submit-severity" className="text-sm font-medium">
            嚴重度
          </label>
          <Select
            id="submit-severity"
            value={severity}
            onChange={(event) => setSeverity(event.target.value)}
          >
            <option value="">未指定</option>
            {SEVERITY_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </Select>
        </div>

        <div className="space-y-1.5">
          <label htmlFor="submit-tlp" className="text-sm font-medium">
            TLP
          </label>
          <Select id="submit-tlp" value={tlp} onChange={(event) => setTlp(event.target.value)}>
            {TLP_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </Select>
          <p className="text-xs text-muted-foreground">提交的情資只有自己的租戶看得到。</p>
        </div>

        <div className="space-y-1.5">
          <label htmlFor="submit-confidence" className="text-sm font-medium">
            信心值(0–100)
          </label>
          <Input
            id="submit-confidence"
            type="number"
            min={0}
            max={100}
            value={confidence}
            onChange={(event) => setConfidence(event.target.value)}
          />
        </div>
      </div>

      <div className="space-y-1.5">
        <label htmlFor="submit-tags" className="text-sm font-medium">
          標籤(以逗號分隔)
        </label>
        <Input id="submit-tags" value={tags} onChange={(event) => setTags(event.target.value)} />
      </div>

      <div className="space-y-1.5">
        <label htmlFor="submit-note" className="text-sm font-medium">
          備註
        </label>
        <Input
          id="submit-note"
          maxLength={1024}
          value={note}
          onChange={(event) => setNote(event.target.value)}
        />
      </div>

      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {describe(error)}
        </p>
      ) : null}

      <Button type="submit" disabled={submitting || value.trim() === ''}>
        {submitting ? '提交中…' : '提交 IOC'}
      </Button>
    </form>
  );
}
