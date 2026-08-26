import { AlertCircle, CheckCircle2, Info, TriangleAlert, X } from 'lucide-react';
import { useAppDispatch, useAppSelector } from '../../stores/hooks';
import { selectToasts, toastDismissed, type ToastKind } from '../../stores/toastSlice';
import { cn } from '../../utils/cn';

const ICONS: Record<ToastKind, typeof Info> = {
  success: CheckCircle2,
  error: AlertCircle,
  info: Info,
  warning: TriangleAlert,
};

const TONE: Record<ToastKind, string> = {
  success: 'border-ok/50 text-ok',
  error: 'border-danger/50 text-danger',
  info: 'border-primary/50 text-primary',
  warning: 'border-warn/50 text-warn',
};

/** §12.3:toast 佇列屬 toastSlice;本元件只負責渲染與 dismiss。 */
export function Toaster() {
  const toasts = useAppSelector(selectToasts);
  const dispatch = useAppDispatch();

  return (
    <div
      aria-live="polite"
      aria-label="通知"
      className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-80 flex-col gap-2"
    >
      {toasts.map((toast) => {
        const Icon = ICONS[toast.kind];
        return (
          <div
            key={toast.id}
            role="status"
            className={cn(
              'pointer-events-auto flex items-start gap-2 rounded-md border bg-surface p-3 shadow-lg',
              TONE[toast.kind],
            )}
          >
            <Icon aria-hidden className="mt-0.5 size-4 shrink-0" />
            <p className="flex-1 text-sm text-surface-foreground">{toast.message}</p>
            <button
              type="button"
              aria-label="關閉通知"
              className="text-muted-foreground hover:text-foreground"
              onClick={() => dispatch(toastDismissed(toast.id))}
            >
              <X className="size-4" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
