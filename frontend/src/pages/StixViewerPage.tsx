import { ArrowLeft } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { EmptyState, LoadingState } from '../components/StateViews';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { StixGraph } from '../features/stix/components/StixGraph';
import { StixJsonViewer } from '../features/stix/components/StixJsonViewer';
import { buildGraph, stixTypesIn } from '../features/stix/graph';
import { useStixGraph } from '../features/stix/hooks/useStixGraph';

/**
 * STIX Viewer(§12.5 `/stix/:id`,匿名可存取;§12.6 要求物件詳情、關聯、圖形檢視、
 * 節點展開、基本篩選)。
 *
 * 圖只能順著物件自身的參照往外長——平台沒有「哪些 relationship 指向我」的反查端點
 * (`GET /api/v1/stix/{stixId}` 只給單一物件)。虛線節點代表尚未載入,點擊即展開。
 */
export default function StixViewerPage() {
  const { id = '' } = useParams<'id'>();
  const { objects, expand, isPending, rootMissing, isError, error } = useStixGraph(id);
  const [selectedId, setSelectedId] = useState(id);
  const [hiddenTypes, setHiddenTypes] = useState<ReadonlySet<string>>(new Set());

  useEffect(() => {
    setSelectedId(id);
    setHiddenTypes(new Set());
  }, [id]);

  const types = useMemo(() => stixTypesIn(objects), [objects]);
  const graph = useMemo(() => buildGraph(objects, { hiddenTypes }), [objects, hiddenTypes]);

  const toggleType = (type: string) =>
    setHiddenTypes((current) => {
      const next = new Set(current);
      if (next.has(type)) next.delete(type);
      else next.add(type);
      return next;
    });

  const select = (stixId: string) => {
    setSelectedId(stixId);
    expand(stixId);
  };

  let content: React.ReactNode;
  if (isPending) {
    content = <LoadingState rows={6} label="載入 STIX 物件" />;
  } else if (rootMissing) {
    content = (
      <EmptyState
        title="查無此 STIX 物件"
        description="此 STIX id 不存在,或以目前身分不可見(TLP 與再散布政策)。"
      />
    );
  } else {
    content = (
      <div className="space-y-4">
        <Card>
          <CardHeader>
            <CardTitle>關聯圖</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <fieldset className="flex flex-wrap items-center gap-3">
              <legend className="sr-only">依 STIX 型別篩選</legend>
              <span className="text-sm text-muted-foreground">型別篩選</span>
              {types.map((type) => (
                <label key={type} className="flex items-center gap-1.5 font-mono text-xs">
                  <input
                    type="checkbox"
                    checked={!hiddenTypes.has(type)}
                    onChange={() => toggleType(type)}
                  />
                  {type}
                </label>
              ))}
            </fieldset>
            <StixGraph graph={graph} selectedId={selectedId} onSelect={select} />
            <p className="text-xs text-muted-foreground">
              虛線節點尚未載入,點擊即展開;實線節點點擊可在下方檢視原始 JSON。
            </p>
            {isError ? (
              <p className="text-xs text-destructive">
                部分節點載入失敗:{error instanceof Error ? error.message : '未知錯誤'}
              </p>
            ) : null}
          </CardContent>
        </Card>
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">目前選取</span>
          <code className="font-mono text-xs break-all">{selectedId}</code>
          {selectedId === id ? null : (
            <Button variant="outline" size="sm" onClick={() => setSelectedId(id)}>
              回到起點物件
            </Button>
          )}
        </div>
        <StixJsonViewer stixId={selectedId} />
      </div>
    );
  }

  return (
    <section aria-labelledby="stix-viewer-title" className="space-y-4">
      <header className="flex items-center gap-3">
        <Link
          to="/iocs"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft aria-hidden className="size-4" />
          返回 IOC 檢索
        </Link>
        <h1 id="stix-viewer-title" className="font-mono text-xl font-bold tracking-tight">
          STIX Viewer
        </h1>
      </header>
      {content}
    </section>
  );
}
