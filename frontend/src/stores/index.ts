import { combineReducers, configureStore } from '@reduxjs/toolkit';
import { z } from 'zod';
import { authSlice } from './authSlice';
import { filterDraftSlice } from './filterDraftSlice';
import { toastSlice } from './toastSlice';
import { initialUiState, uiSlice, type UiState } from './uiSlice';

const rootReducer = combineReducers({
  auth: authSlice.reducer,
  ui: uiSlice.reducer,
  toast: toastSlice.reducer,
  filterDraft: filterDraftSlice.reducer,
});

export type RootState = ReturnType<typeof rootReducer>;

const UI_STORAGE_KEY = 'ctip.ui.v1';

const uiPersistSchema = z.object({
  theme: z.enum(['light', 'dark', 'system']),
  sidebarCollapsed: z.boolean(),
  tableColumns: z.record(z.string(), z.array(z.string())),
});

export function loadUiState(): UiState | undefined {
  try {
    const raw = localStorage.getItem(UI_STORAGE_KEY);
    if (!raw) return undefined;
    return uiPersistSchema.parse(JSON.parse(raw));
  } catch {
    // 壞資料 / 私密模式 → 回預設值
    return undefined;
  }
}

function persistUiState(ui: UiState): void {
  try {
    localStorage.setItem(UI_STORAGE_KEY, JSON.stringify(ui));
  } catch {
    // 容量滿 / 私密模式:略過,僅影響下次載入的偏好
  }
}

export function makeStore(preloadedUi?: UiState) {
  const store = configureStore({
    reducer: rootReducer,
    preloadedState: preloadedUi ? { ui: preloadedUi } : undefined,
  });
  let previous = store.getState().ui;
  store.subscribe(() => {
    const current = store.getState().ui;
    if (current !== previous) {
      previous = current;
      persistUiState(current);
    }
  });
  return store;
}

export type AppStore = ReturnType<typeof makeStore>;
export type AppDispatch = AppStore['dispatch'];

export function createAppStore(): AppStore {
  return makeStore(loadUiState() ?? initialUiState);
}
