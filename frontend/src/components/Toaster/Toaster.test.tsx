import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { describe, expect, it } from 'vitest';
import { makeStore } from '../../stores';
import { toastPushed } from '../../stores/toastSlice';
import { Toaster } from './Toaster';

describe('Toaster', () => {
  it('renders queued toasts in an aria-live region', () => {
    const store = makeStore();
    store.dispatch(toastPushed({ kind: 'success', message: '已複製 IOC 值' }));
    render(
      <Provider store={store}>
        <Toaster />
      </Provider>,
    );
    expect(screen.getByLabelText('通知')).toHaveAttribute('aria-live', 'polite');
    expect(screen.getByText('已複製 IOC 值')).toBeInTheDocument();
  });

  it('dismisses a toast via its close button', async () => {
    const store = makeStore();
    store.dispatch(toastPushed({ kind: 'error', message: '匯出失敗' }));
    render(
      <Provider store={store}>
        <Toaster />
      </Provider>,
    );
    await userEvent.click(screen.getByRole('button', { name: '關閉通知' }));
    expect(screen.queryByText('匯出失敗')).not.toBeInTheDocument();
    expect(store.getState().toast.toasts).toHaveLength(0);
  });
});
