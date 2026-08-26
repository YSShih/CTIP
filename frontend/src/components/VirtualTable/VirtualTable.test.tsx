import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { VirtualTable, type VirtualTableColumn } from './VirtualTable';

interface Row {
  id: string;
  value: string;
}

const columns: VirtualTableColumn<Row>[] = [
  { key: 'id', header: 'ID', cell: (row) => row.id },
  { key: 'value', header: 'Value', cell: (row) => row.value },
];

const rows: Row[] = Array.from({ length: 200 }, (_, index) => ({
  id: `row-${index}`,
  value: `value-${index}`,
}));

const originalOffsetHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetHeight');
const originalOffsetWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetWidth');

beforeEach(() => {
  // jsdom 無 layout:TanStack Virtual 以 offsetWidth/offsetHeight 量測捲動容器,
  // 給固定值虛擬化才會渲染視窗內的列
  Object.defineProperty(HTMLElement.prototype, 'offsetHeight', { configurable: true, value: 400 });
  Object.defineProperty(HTMLElement.prototype, 'offsetWidth', { configurable: true, value: 800 });
});

afterEach(() => {
  if (originalOffsetHeight) {
    Object.defineProperty(HTMLElement.prototype, 'offsetHeight', originalOffsetHeight);
  }
  if (originalOffsetWidth) {
    Object.defineProperty(HTMLElement.prototype, 'offsetWidth', originalOffsetWidth);
  }
});

describe('VirtualTable', () => {
  it('renders headers and only the virtualized window of rows', () => {
    render(<VirtualTable rows={rows} columns={columns} rowKey={(row) => row.id} />);
    expect(screen.getByRole('columnheader', { name: 'ID' })).toBeInTheDocument();
    const rendered = screen.getAllByTestId('virtual-row');
    expect(rendered.length).toBeGreaterThan(0);
    expect(rendered.length).toBeLessThan(rows.length);
  });

  it('invokes onRowClick with the row data', async () => {
    const onRowClick = vi.fn();
    render(
      <VirtualTable
        rows={rows.slice(0, 3)}
        columns={columns}
        rowKey={(row) => row.id}
        onRowClick={onRowClick}
      />,
    );
    await userEvent.click(screen.getByText('value-1'));
    expect(onRowClick).toHaveBeenCalledWith(rows[1]);
  });

  it('renders only headers when rows are empty (EmptyState is the caller responsibility)', () => {
    render(<VirtualTable rows={[]} columns={columns} rowKey={(row: Row) => row.id} />);
    expect(screen.getByRole('columnheader', { name: 'Value' })).toBeInTheDocument();
    expect(screen.queryAllByTestId('virtual-row')).toHaveLength(0);
  });
});
