import { describe, expect, it } from 'vitest';
import {
  authSlice,
  selectHasPermission,
  selectIsAuthenticated,
  sessionCleared,
  sessionEstablished,
} from './authSlice';
import {
  emptyIocDraft,
  filterDraftSlice,
  iocDraftFieldChanged,
  iocDraftReplaced,
  iocDraftReset,
  selectIocDraft,
} from './filterDraftSlice';
import { allToastsCleared, toastDismissed, toastPushed, toastSlice } from './toastSlice';
import {
  selectSidebarCollapsed,
  selectTheme,
  sidebarToggled,
  tableColumnsChanged,
  themeChanged,
  uiSlice,
} from './uiSlice';

describe('authSlice', () => {
  it('establishes and clears a session', () => {
    const established = authSlice.reducer(
      undefined,
      sessionEstablished({
        accessToken: 'token',
        refreshToken: 'refresh',
        user: { id: 'u1', name: 'Analyst' },
        tenantId: 't1',
        role: 'USER',
        permissions: ['ioc:read'],
      }),
    );
    expect(selectIsAuthenticated({ auth: established })).toBe(true);
    expect(selectHasPermission({ auth: established }, 'ioc:read')).toBe(true);
    expect(selectHasPermission({ auth: established }, 'ioc:write')).toBe(false);

    const cleared = authSlice.reducer(established, sessionCleared());
    expect(selectIsAuthenticated({ auth: cleared })).toBe(false);
    expect(cleared.permissions).toEqual([]);
  });
});

describe('uiSlice', () => {
  it('changes theme, toggles sidebar, and stores table columns', () => {
    let state = uiSlice.reducer(undefined, themeChanged('dark'));
    expect(selectTheme({ ui: state })).toBe('dark');

    state = uiSlice.reducer(state, sidebarToggled());
    expect(selectSidebarCollapsed({ ui: state })).toBe(true);
    state = uiSlice.reducer(state, sidebarToggled());
    expect(selectSidebarCollapsed({ ui: state })).toBe(false);

    state = uiSlice.reducer(state, tableColumnsChanged({ tableId: 'ioc', columns: ['value'] }));
    expect(state.tableColumns.ioc).toEqual(['value']);
  });
});

describe('toastSlice', () => {
  it('pushes toasts with unique ids, dismisses, and clears', () => {
    let state = toastSlice.reducer(undefined, toastPushed({ kind: 'info', message: 'one' }));
    state = toastSlice.reducer(state, toastPushed({ kind: 'error', message: 'two' }));
    expect(state.toasts).toHaveLength(2);
    expect(state.toasts[0].id).not.toBe(state.toasts[1].id);

    state = toastSlice.reducer(state, toastDismissed(state.toasts[0].id));
    expect(state.toasts).toHaveLength(1);
    expect(state.toasts[0].message).toBe('two');

    state = toastSlice.reducer(state, allToastsCleared());
    expect(state.toasts).toEqual([]);
  });
});

describe('filterDraftSlice', () => {
  it('edits, replaces, and resets the ioc draft', () => {
    let state = filterDraftSlice.reducer(
      undefined,
      iocDraftFieldChanged({ field: 'q', value: 'phish' }),
    );
    expect(selectIocDraft({ filterDraft: state }).q).toBe('phish');

    state = filterDraftSlice.reducer(state, iocDraftReplaced({ ...emptyIocDraft, type: 'DOMAIN' }));
    expect(selectIocDraft({ filterDraft: state })).toEqual({ ...emptyIocDraft, type: 'DOMAIN' });

    state = filterDraftSlice.reducer(state, iocDraftReset());
    expect(selectIocDraft({ filterDraft: state })).toEqual(emptyIocDraft);
  });
});
