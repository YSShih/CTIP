import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** shadcn/ui 慣例:合併 class 並解決 Tailwind utility 衝突。 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
