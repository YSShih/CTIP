import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { EmptyState } from './EmptyState';
import { ErrorState } from './ErrorState';
import { ForbiddenState } from './ForbiddenState';
import { LoadingState } from './LoadingState';

describe('LoadingState', () => {
  it('renders an accessible skeleton status', () => {
    render(<LoadingState rows={2} />);
    expect(screen.getByRole('status', { name: '載入中' })).toBeInTheDocument();
  });
});

describe('EmptyState', () => {
  it('renders description and action suggestion', () => {
    render(<EmptyState description="調整篩選條件後重試。" action={<button>清除篩選</button>} />);
    expect(screen.getByText('沒有資料')).toBeInTheDocument();
    expect(screen.getByText('調整篩選條件後重試。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '清除篩選' })).toBeInTheDocument();
  });
});

describe('ErrorState', () => {
  it('maps ApiError code to copy, shows traceId, and retries', async () => {
    const onRetry = vi.fn();
    render(
      <ErrorState
        error={new ApiError(429, 'RATE_LIMITED', 'too many requests', 'trace-123')}
        onRetry={onRetry}
      />,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('RATE_LIMITED')).toBeInTheDocument();
    expect(screen.getByText(/限流/)).toBeInTheDocument();
    expect(screen.getByText(/trace-123/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '重試' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('falls back to generic copy for unknown errors', () => {
    render(<ErrorState error={new Error('boom')} />);
    expect(screen.getByText('ERROR')).toBeInTheDocument();
    expect(screen.getByText(/未預期的錯誤/)).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});

describe('ForbiddenState', () => {
  it('explains the login requirement instead of showing blank content', () => {
    render(<ForbiddenState reason="login" />);
    expect(screen.getByText('需要登入')).toBeInTheDocument();
    expect(screen.getByText(/僅提供已登入的租戶成員/)).toBeInTheDocument();
  });

  it('explains insufficient permission with upgrade guidance', () => {
    render(<ForbiddenState reason="upgrade" />);
    expect(screen.getByText('權限不足')).toBeInTheDocument();
    expect(screen.getByText(/升級方案/)).toBeInTheDocument();
  });
});
