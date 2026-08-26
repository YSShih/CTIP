import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

/** §12.3:主題 / 表格欄位設定 / 側欄狀態,搭 localStorage 持久化(stores/index.ts)。 */

export type ThemePreference = 'light' | 'dark' | 'system';

export interface UiState {
  theme: ThemePreference;
  sidebarCollapsed: boolean;
  tableColumns: Record<string, string[]>;
}

export const initialUiState: UiState = {
  theme: 'system',
  sidebarCollapsed: false,
  tableColumns: {},
};

export const uiSlice = createSlice({
  name: 'ui',
  initialState: initialUiState,
  reducers: {
    themeChanged(state, action: PayloadAction<ThemePreference>) {
      state.theme = action.payload;
    },
    sidebarToggled(state) {
      state.sidebarCollapsed = !state.sidebarCollapsed;
    },
    tableColumnsChanged(state, action: PayloadAction<{ tableId: string; columns: string[] }>) {
      state.tableColumns[action.payload.tableId] = action.payload.columns;
    },
  },
});

export const { themeChanged, sidebarToggled, tableColumnsChanged } = uiSlice.actions;

export interface HasUiState {
  ui: UiState;
}

export const selectTheme = (state: HasUiState): ThemePreference => state.ui.theme;
export const selectSidebarCollapsed = (state: HasUiState): boolean => state.ui.sidebarCollapsed;
