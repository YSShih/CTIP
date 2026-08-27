import { KeyRound, LogIn, LogOut, Menu, Monitor, Moon, Radar, Sun } from 'lucide-react';
import { NavLink, Outlet, useNavigate } from 'react-router';
import { Toaster } from '../components/Toaster/Toaster';
import { Button } from '../components/ui/button';
import {
  useCurrentUser,
  useHasPermission,
  useIsAuthenticated,
  useLogout,
} from '../hooks/useSession';
import { useAppDispatch, useAppSelector } from '../stores/hooks';
import {
  selectSidebarCollapsed,
  selectTheme,
  sidebarToggled,
  themeChanged,
  type ThemePreference,
} from '../stores/uiSlice';
import { cn } from '../utils/cn';

const NAV_ITEMS = [
  { to: '/', label: '儀表板', end: true },
  { to: '/iocs', label: 'IOC 檢索', end: false },
];

const THEME_CYCLE: Record<ThemePreference, ThemePreference> = {
  light: 'dark',
  dark: 'system',
  system: 'light',
};

const THEME_ICON: Record<ThemePreference, typeof Sun> = {
  light: Sun,
  dark: Moon,
  system: Monitor,
};

const THEME_LABEL: Record<ThemePreference, string> = {
  light: '主題:亮色',
  dark: '主題:深色',
  system: '主題:跟隨系統',
};

function navLinkClass({ isActive }: { isActive: boolean }): string {
  return cn(
    'rounded-md px-3 py-1.5 font-mono text-xs font-semibold uppercase tracking-[0.14em] transition-colors',
    isActive
      ? 'bg-accent text-accent-foreground shadow-[inset_0_-2px_0_var(--primary)]'
      : 'text-muted-foreground hover:text-foreground',
  );
}

/** §12.2:所有頁面共用的外框(header/nav/theme/session/toast/Outlet),響應式。 */
export function AppLayout() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const theme = useAppSelector(selectTheme);
  const menuCollapsed = useAppSelector(selectSidebarCollapsed);
  const ThemeIcon = THEME_ICON[theme];
  const authenticated = useIsAuthenticated();
  const user = useCurrentUser();
  const canManageApiKeys = useHasPermission('apikey:create');
  const logout = useLogout();

  async function handleLogout() {
    await logout();
    void navigate('/');
  }

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-40 border-b bg-background/85 backdrop-blur">
        <div className="mx-auto flex h-14 w-full max-w-6xl items-center gap-4 px-4">
          <NavLink to="/" className="flex items-center gap-2" aria-label="CTIP 首頁">
            <Radar aria-hidden className="size-5 text-primary" />
            <span className="font-mono text-base font-bold tracking-[0.22em]">CTIP</span>
            <span className="mt-0.5 hidden text-[10px] uppercase tracking-[0.18em] text-muted-foreground sm:inline">
              threat intel
            </span>
          </NavLink>

          <nav aria-label="主導覽" className="ml-4 hidden items-center gap-1 md:flex">
            {NAV_ITEMS.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={navLinkClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-1">
            {authenticated ? (
              <>
                {canManageApiKeys ? (
                  <NavLink
                    to="/settings/api-keys"
                    className="hidden items-center gap-1 rounded-md px-2 py-1.5 text-xs text-muted-foreground hover:text-foreground sm:inline-flex"
                  >
                    <KeyRound aria-hidden className="size-4" />
                    API Key
                  </NavLink>
                ) : null}
                <span className="hidden max-w-[12rem] truncate font-mono text-xs text-muted-foreground md:inline">
                  {user?.name}
                </span>
                <Button variant="ghost" size="sm" onClick={() => void handleLogout()}>
                  <LogOut aria-hidden className="size-4" />
                  登出
                </Button>
              </>
            ) : (
              <NavLink
                to="/login"
                className="inline-flex items-center gap-1 rounded-md px-2 py-1.5 text-xs text-muted-foreground hover:text-foreground"
              >
                <LogIn aria-hidden className="size-4" />
                登入
              </NavLink>
            )}
            <Button
              variant="ghost"
              size="icon"
              aria-label={THEME_LABEL[theme]}
              title={THEME_LABEL[theme]}
              onClick={() => dispatch(themeChanged(THEME_CYCLE[theme]))}
            >
              <ThemeIcon aria-hidden />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="md:hidden"
              aria-label="開關選單"
              aria-expanded={!menuCollapsed}
              onClick={() => dispatch(sidebarToggled())}
            >
              <Menu aria-hidden />
            </Button>
          </div>
        </div>

        {!menuCollapsed ? (
          <nav aria-label="行動版導覽" className="border-t px-4 py-2 md:hidden">
            <ul className="flex flex-col gap-1">
              {NAV_ITEMS.map((item) => (
                <li key={item.to}>
                  <NavLink to={item.to} end={item.end} className={navLinkClass}>
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        ) : null}
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
        <Outlet />
      </main>

      <footer className="border-t py-3">
        <p className="mx-auto max-w-6xl px-4 font-mono text-[11px] uppercase tracking-[0.14em] text-muted-foreground">
          CTIP — cyber threat intelligence platform · 情資依 TLP 2.0 標示
        </p>
      </footer>

      <Toaster />
    </div>
  );
}
