import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

/**
 * §12.3:尚未送出的搜尋條件草稿。已送出的條件屬 URL search params,
 * 不得存在這裡(IocSearchPage 送出時寫入 URL)。
 */

export interface IocFilterDraft {
  q: string;
  type: string;
  severity: string;
  status: string;
  tlp: string;
}

export interface FilterDraftState {
  ioc: IocFilterDraft;
}

export const emptyIocDraft: IocFilterDraft = {
  q: '',
  type: '',
  severity: '',
  status: '',
  tlp: '',
};

const initialState: FilterDraftState = { ioc: emptyIocDraft };

export const filterDraftSlice = createSlice({
  name: 'filterDraft',
  initialState,
  reducers: {
    iocDraftFieldChanged(
      state,
      action: PayloadAction<{ field: keyof IocFilterDraft; value: string }>,
    ) {
      state.ioc[action.payload.field] = action.payload.value;
    },
    iocDraftReplaced(state, action: PayloadAction<IocFilterDraft>) {
      state.ioc = action.payload;
    },
    iocDraftReset(state) {
      state.ioc = emptyIocDraft;
    },
  },
});

export const { iocDraftFieldChanged, iocDraftReplaced, iocDraftReset } = filterDraftSlice.actions;

export interface HasFilterDraftState {
  filterDraft: FilterDraftState;
}

export const selectIocDraft = (state: HasFilterDraftState): IocFilterDraft => state.filterDraft.ioc;
