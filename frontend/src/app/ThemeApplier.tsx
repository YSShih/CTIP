import { useEffect } from 'react';
import { useAppSelector } from '../stores/hooks';
import { selectTheme } from '../stores/uiSlice';

function applyDarkClass(dark: boolean): void {
  document.documentElement.classList.toggle('dark', dark);
}

/** uiSlice.theme → <html class="dark">;system 跟隨 prefers-color-scheme 並監聽變更。 */
export function ThemeApplier() {
  const theme = useAppSelector(selectTheme);

  useEffect(() => {
    if (theme !== 'system') {
      applyDarkClass(theme === 'dark');
      return;
    }
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    applyDarkClass(media.matches);
    const onChange = (event: MediaQueryListEvent) => applyDarkClass(event.matches);
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, [theme]);

  return null;
}
