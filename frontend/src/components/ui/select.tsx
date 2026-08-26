import type { SelectHTMLAttributes } from 'react';
import { cn } from '../../utils/cn';

export type SelectProps = SelectHTMLAttributes<HTMLSelectElement>;

/** 原生 select 的 shadcn 風格外觀(M1 不引入 radix;鍵盤/無障礙走原生行為)。 */
export function Select({ className, children, ...props }: SelectProps) {
  return (
    <select
      className={cn(
        'h-9 rounded-md border bg-surface px-2 text-sm text-surface-foreground',
        'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2',
        'focus-visible:outline-ring disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      {...props}
    >
      {children}
    </select>
  );
}
