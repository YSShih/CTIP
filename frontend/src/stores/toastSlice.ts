import { createSlice, nanoid, type PayloadAction } from '@reduxjs/toolkit';

/** §12.3:全域通知佇列。 */

export type ToastKind = 'success' | 'error' | 'info' | 'warning';

export interface Toast {
  id: string;
  kind: ToastKind;
  message: string;
}

export interface ToastState {
  toasts: Toast[];
}

const initialState: ToastState = { toasts: [] };

export const toastSlice = createSlice({
  name: 'toast',
  initialState,
  reducers: {
    toastPushed: {
      reducer(state, action: PayloadAction<Toast>) {
        state.toasts.push(action.payload);
      },
      prepare(input: { kind: ToastKind; message: string }) {
        return { payload: { id: nanoid(), ...input } };
      },
    },
    toastDismissed(state, action: PayloadAction<string>) {
      state.toasts = state.toasts.filter((toast) => toast.id !== action.payload);
    },
    allToastsCleared(state) {
      state.toasts = [];
    },
  },
});

export const { toastPushed, toastDismissed, allToastsCleared } = toastSlice.actions;

export interface HasToastState {
  toast: ToastState;
}

export const selectToasts = (state: HasToastState): Toast[] => state.toast.toasts;
