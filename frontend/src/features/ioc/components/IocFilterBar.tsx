import { RotateCcw, Search } from 'lucide-react';
import { useEffect, type FormEvent } from 'react';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { Select } from '../../../components/ui/select';
import { useAppDispatch, useAppSelector } from '../../../stores/hooks';
import {
  emptyIocDraft,
  iocDraftFieldChanged,
  iocDraftReplaced,
  iocDraftReset,
  selectIocDraft,
  type IocFilterDraft,
} from '../../../stores/filterDraftSlice';
import type { IocFilters } from '../api/iocApi';
import { SEVERITY_OPTIONS, STATUS_OPTIONS, TLP_OPTIONS, TYPE_OPTIONS } from '../types';

export interface IocFilterBarProps {
  /** 目前已套用(URL)的條件;掛載時同步進草稿 */
  applied: IocFilters;
  onApply: (filters: IocFilters) => void;
}

const SELECTS: {
  field: keyof Omit<IocFilterDraft, 'q'>;
  label: string;
  options: readonly string[];
}[] = [
  { field: 'type', label: '型別', options: TYPE_OPTIONS },
  { field: 'severity', label: '嚴重度', options: SEVERITY_OPTIONS },
  { field: 'status', label: '狀態', options: STATUS_OPTIONS },
  { field: 'tlp', label: 'TLP', options: TLP_OPTIONS },
];

/**
 * §12.3:輸入中的條件是草稿(filterDraftSlice);按「搜尋」才送出,
 * 由呼叫端寫入 URL search params(不進 Redux)。
 */
export function IocFilterBar({ applied, onApply }: IocFilterBarProps) {
  const dispatch = useAppDispatch();
  const draft = useAppSelector(selectIocDraft);

  // 只在掛載時把 URL 條件帶進草稿;之後草稿由使用者輸入主導
  useEffect(() => {
    dispatch(iocDraftReplaced({ ...emptyIocDraft, ...applied }));
  }, []); // 依賴刻意留空:applied 之後的變更不得覆蓋使用者輸入中的草稿

  const submit = (event: FormEvent) => {
    event.preventDefault();
    onApply(draft);
  };

  const reset = () => {
    dispatch(iocDraftReset());
    onApply(emptyIocDraft);
  };

  return (
    <form
      onSubmit={submit}
      aria-label="IOC 篩選"
      className="flex flex-wrap items-end gap-3 rounded-lg border bg-surface p-4"
    >
      <label className="flex min-w-56 flex-1 flex-col gap-1">
        <span className="font-mono text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
          關鍵字
        </span>
        <Input
          value={draft.q}
          placeholder="IOC 值子字串,如 ctip-sample 或 203.0.113"
          onChange={(event) =>
            dispatch(iocDraftFieldChanged({ field: 'q', value: event.target.value }))
          }
        />
      </label>
      {SELECTS.map(({ field, label, options }) => (
        <label key={field} className="flex flex-col gap-1">
          <span className="font-mono text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
            {label}
          </span>
          <Select
            value={draft[field]}
            onChange={(event) =>
              dispatch(iocDraftFieldChanged({ field, value: event.target.value }))
            }
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
