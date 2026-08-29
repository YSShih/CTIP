import { RotateCcw, Search } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { Select } from '../../../components/ui/select';
import { emptyThreatFilters, type ThreatFilters } from '../api/threatApi';
import {
  SEVERITY_OPTIONS,
  THREAT_STATUS_OPTIONS,
  THREAT_TYPE_OPTIONS,
  TLP_OPTIONS,
} from '../types';

export interface ThreatFilterBarProps {
  /** 目前已套用(URL)的條件;掛載時同步進草稿 */
  applied: ThreatFilters;
  onApply: (filters: ThreatFilters) => void;
}

const SELECTS: {
  field: keyof Omit<ThreatFilters, 'name'>;
  label: string;
  options: readonly string[];
}[] = [
  { field: 'type', label: '型別', options: THREAT_TYPE_OPTIONS },
  { field: 'status', label: '狀態', options: THREAT_STATUS_OPTIONS },
  { field: 'severity', label: '嚴重度', options: SEVERITY_OPTIONS },
  { field: 'tlp', label: 'TLP', options: TLP_OPTIONS },
];

/**
 * §12.3:輸入中的條件是本地草稿(表單狀態),按「搜尋」才送出;
 * 已送出的條件由呼叫端寫入 URL search params(不進 Redux、不進 Query)。
 */
export function ThreatFilterBar({ applied, onApply }: ThreatFilterBarProps) {
  const [draft, setDraft] = useState<ThreatFilters>(applied);

  // 只在掛載時把 URL 條件帶進草稿;之後草稿由使用者輸入主導
  // 依賴刻意留空:applied 之後的變更不得覆蓋使用者輸入中的草稿(同 IocFilterBar)
  useEffect(() => {
    setDraft(applied);
  }, []);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    onApply(draft);
  };

  const reset = () => {
    setDraft(emptyThreatFilters);
    onApply(emptyThreatFilters);
  };

  return (
    <form
      onSubmit={submit}
      aria-label="威脅篩選"
      className="flex flex-wrap items-end gap-3 rounded-lg border bg-surface p-4"
    >
      <label className="flex min-w-56 flex-1 flex-col gap-1">
        <span className="font-mono text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
          名稱
        </span>
        <Input
          value={draft.name}
          placeholder="名稱子字串,如 AgentTesla"
          onChange={(event) => setDraft({ ...draft, name: event.target.value })}
        />
      </label>
      {SELECTS.map(({ field, label, options }) => (
        <label key={field} className="flex flex-col gap-1">
          <span className="font-mono text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
            {label}
          </span>
          <Select
            value={draft[field]}
            onChange={(event) => setDraft({ ...draft, [field]: event.target.value })}
          >
            <option value="">全部</option>
            {options.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </Select>
        </label>
      ))}
      <div className="flex gap-2">
        <Button type="submit">
          <Search aria-hidden />
          搜尋
        </Button>
        <Button type="button" variant="ghost" onClick={reset} aria-label="清除篩選">
          <RotateCcw aria-hidden />
          清除
        </Button>
      </div>
    </form>
  );
}
