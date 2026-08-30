import { Copy, Network } from 'lucide-react';
import { Link } from 'react-router';
import { ApiError } from '../../../api/client';
import { EmptyState, ErrorState, LoadingState } from '../../../components/StateViews';
import { Button } from '../../../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../../../components/ui/card';
import { useAppDispatch } from '../../../stores/hooks';
import { toastPushed } from '../../../stores/toastSlice';
import { useStixObject } from '../hooks/useStixObject';

export interface StixJsonViewerProps {
  stixId: string;
  /** 顯示「在 STIX Viewer 開啟」入口;STIX Viewer 頁自己用時關掉(§12.5 /stix/:id) */
  viewerLink?: boolean;
}

/** STIX 2.1 投影的原始 JSON 檢視;圖形檢視在 /stix/:id(§12.6)。 */
export function StixJsonViewer({ stixId, viewerLink = false }: StixJsonViewerProps) {
  const dispatch = useAppDispatch();
  const { data, isPending, isError, error, refetch } = useStixObject(stixId);

  const copy = async (payload: string) => {
    try {
      await navigator.clipboard.writeText(payload);
      dispatch(toastPushed({ kind: 'success', message: '已複製 STIX JSON' }));
    } catch {
      dispatch(toastPushed({ kind: 'error', message: '複製失敗,請手動選取' }));
    }
  };

  let body: React.ReactNode;
  if (isPending) {
    body = <LoadingState rows={3} label="載入 STIX 投影" />;
  } else if (isError && error instanceof ApiError && error.status === 404) {
    body = <EmptyState title="無 STIX 投影" description="此 IOC 尚未產生 STIX 2.1 投影。" />;
  } else if (isError) {
    body = <ErrorState error={error} onRetry={() => void refetch()} />;
  } else {
    const json = JSON.stringify(data, null, 2);
    body = (
      <div className="relative">
        <Button
          variant="outline"
          size="sm"
          className="absolute right-2 top-2"
          onClick={() => void copy(json)}
        >
          <Copy aria-hidden />
          複製
        </Button>
        <pre className="overflow-x-auto rounded-md border bg-muted/40 p-4 font-mono text-xs leading-relaxed">
          {json}
        </pre>
      </div>
    );
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between gap-3">
        <CardTitle>STIX 2.1</CardTitle>
        {viewerLink ? (
          <Link
            to={`/stix/${stixId}`}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <Network aria-hidden className="size-4" />在 STIX Viewer 開啟
          </Link>
        ) : null}
      </CardHeader>
      <CardContent>{body}</CardContent>
    </Card>
  );
}
