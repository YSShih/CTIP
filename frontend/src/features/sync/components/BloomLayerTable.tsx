import type { BloomManifestDto } from '../api/syncApi';

export interface BloomLayerTableProps {
  layer: BloomManifestDto;
}

/**
 * 一層 Bloom 的 manifest 欄位(11 §11.5)。
 *
 * `coverage` 排在第一列:它是這一層「涵蓋了什麼」的唯一說明,而所有版本號與 checksum
 * 都只在那個範圍內才有意義。
 */
const ROWS: ReadonlyArray<{ key: keyof BloomManifestDto; label: string }> = [
  { key: 'coverage', label: '覆蓋範圍' },
  { key: 'datasetVersion', label: 'datasetVersion(full 版號)' },
  { key: 'bloomVersion', label: 'bloomVersion(delta 版號)' },
  { key: 'memberCount', label: '成員數' },
  { key: 'capacity', label: '容量(n)' },
  { key: 'falsePositiveRate', label: '目標偽陽性率(p)' },
  { key: 'bitSize', label: 'bitSize(m)' },
  { key: 'hashFunctionCount', label: 'hashFunctionCount(k)' },
  { key: 'fingerprintAlgorithm', label: '指紋演算法' },
  { key: 'sizeBytes', label: '未壓縮大小(bytes)' },
  { key: 'compression', label: '傳輸壓縮' },
  { key: 'checksum', label: '完全同步後的 checksum' },
  { key: 'generatedAt', label: '產生時間' },
];

function format(value: BloomManifestDto[keyof BloomManifestDto], key: string): string {
  if (value === null || value === undefined) return '—';
  if (key === 'generatedAt') return new Date(String(value)).toLocaleString();
  if (typeof value === 'number' && key !== 'falsePositiveRate') return value.toLocaleString();
  return String(value);
}

export function BloomLayerTable({ layer }: BloomLayerTableProps) {
  return (
    <table className="w-full text-sm">
      <caption className="sr-only">{layer.scope} Bloom 的 manifest</caption>
      <tbody>
        {ROWS.map((row) => (
          <tr key={String(row.key)} className="border-b last:border-0">
            <th scope="row" className="py-2 pr-3 text-left font-normal text-muted-foreground">
              {row.label}
            </th>
            <td
              className="max-w-0 truncate py-2 text-right font-mono"
              title={format(layer[row.key], String(row.key))}
            >
              {format(layer[row.key], String(row.key))}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
