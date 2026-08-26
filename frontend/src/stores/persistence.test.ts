import { describe, expect, it } from 'vitest';
import { createAppStore, loadUiState, makeStore } from './index';
import { themeChanged } from './uiSlice';

describe('uiSlice persistence', () => {
  it('writes ui state to localStorage on change', () => {
    const store = makeStore();
    store.dispatch(themeChanged('dark'));
    const raw = localStorage.getItem('ctip.ui.v1');
    expect(raw).not.toBeNull();
    expect(JSON.parse(raw!)).toMatchObject({ theme: 'dark' });
  });

  it('restores persisted ui state on store creation', () => {
    const first = makeStore();
    first.dispatch(themeChanged('light'));

    const second = createAppStore();
    expect(second.getState().ui.theme).toBe('light');
  });

  it('falls back to defaults on corrupt persisted data', () => {
    localStorage.setItem('ctip.ui.v1', '{not json');
    expect(loadUiState()).toBeUndefined();

    localStorage.setItem('ctip.ui.v1', JSON.stringify({ theme: 'neon' }));
    expect(loadUiState()).toBeUndefined();

    const store = createAppStore();
    expect(store.getState().ui.theme).toBe('system');
  });
});
