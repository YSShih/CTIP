import { cva, type VariantProps } from 'class-variance-authority';
import type { HTMLAttributes } from 'react';
import { cn } from '../../utils/cn';

const badgeVariants = cva(
  'inline-flex items-center rounded-sm border px-1.5 py-0.5 font-mono text-[11px] ' +
    'font-medium uppercase tracking-wider',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-primary text-primary-foreground',
        muted: 'border-transparent bg-muted text-muted-foreground',
        outline: 'border-border text-foreground',
        ok: 'border-transparent bg-ok/15 text-ok',
        warn: 'border-transparent bg-warn/15 text-warn',
        danger: 'border-transparent bg-danger/15 text-danger',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  },
);

export interface BadgeProps
  extends HTMLAttributes<HTMLSpanElement>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}
