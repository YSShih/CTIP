import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Badge } from './badge';
import { Button } from './button';
import { Card, CardContent, CardHeader, CardTitle } from './card';
import { Input } from './input';
import { Separator } from './separator';
import { Skeleton } from './skeleton';

describe('Button', () => {
  it('fires onClick and defaults to type=button', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>送出</Button>);
    const button = screen.getByRole('button', { name: '送出' });
    expect(button).toHaveAttribute('type', 'button');
    await userEvent.click(button);
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('applies variant styling and disabled state', () => {
    render(
      <Button variant="destructive" disabled>
        刪除
      </Button>,
    );
    const button = screen.getByRole('button', { name: '刪除' });
    expect(button).toBeDisabled();
    expect(button.className).toContain('bg-destructive');
  });
});

describe('Badge', () => {
  it('renders variants', () => {
    render(<Badge variant="danger">CRITICAL</Badge>);
    expect(screen.getByText('CRITICAL').className).toContain('text-danger');
  });
});

describe('Card', () => {
  it('composes header, title, and content', () => {
    render(
      <Card>
        <CardHeader>
          <CardTitle>Active IOCs</CardTitle>
        </CardHeader>
        <CardContent>1,020</CardContent>
      </Card>,
    );
    expect(screen.getByRole('heading', { name: 'Active IOCs' })).toBeInTheDocument();
    expect(screen.getByText('1,020')).toBeInTheDocument();
  });
});

describe('Input', () => {
  it('accepts typing', async () => {
    render(<Input placeholder="搜尋 IOC" />);
    const input = screen.getByPlaceholderText('搜尋 IOC');
    await userEvent.type(input, '203.0.113.7');
    expect(input).toHaveValue('203.0.113.7');
  });
});

describe('Separator and Skeleton', () => {
  it('render with proper roles/classes', () => {
    render(
      <>
        <Separator />
        <Skeleton data-testid="skeleton" />
      </>,
    );
    expect(screen.getByRole('separator')).toBeInTheDocument();
    expect(screen.getByTestId('skeleton').className).toContain('animate-pulse');
  });
});
