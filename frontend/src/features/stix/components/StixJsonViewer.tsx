import { Copy } from 'lucide-react';
import { ApiError } from '../../../api/client';
import { EmptyState, ErrorState, LoadingState } from '../../../components/StateViews';
import { Button } from '../../../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../../../components/ui/card';
import { useAppDispatch } from '../../../stores/hooks';
import { toastPushed } from '../../../stores/toastSlice';
import { useStixObject } from '../hooks/useStixObject';

export interface StixJsonViewerProps {
  stixId: string;
}

/** IOC 的 STIX 2.1 投影檢視(M3 才有圖形檢視;M1 呈現原始 JSON)。 */
export function StixJsonViewer({ stixId }: StixJsonViewerProps) {
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
      <CardHeader>
        <CardTitle>STIX 2.1</CardTitle>
      </CardHeader>
      <CardContent>{body}</CardContent>
    </Card>
  );
}
