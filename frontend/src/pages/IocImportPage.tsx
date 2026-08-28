import { useState } from 'react';
import { ApiError } from '../api/client';
import { ErrorState, LoadingState } from '../components/StateViews';
import { Card } from '../components/ui/card';
import { ImportJobStatus } from '../features/ioc/components/ImportJobStatus';
import { IocImportForm } from '../features/ioc/components/IocImportForm';
import { useImportIocs, useImportJob } from '../features/ioc/hooks/useIocWrite';

/**
 * §12.5 /iocs/import(需登入 + ioc:import)。
 * 匯入是非同步的(§9.7:202 + jobId),因此頁面在接受之後改為輪詢進度,
 * 到終態就停止——不能只回一句「已送出」讓使用者不知道結果。
 */
export default function IocImportPage() {
  const startImport = useImportIocs();
  const [jobId, setJobId] = useState<string | null>(null);
  const job = useImportJob(jobId);

  let progress: React.ReactNode = null;
  if (jobId !== null) {
    if (job.isPending) {
      progress = <LoadingState rows={2} label="載入匯入進度" />;
    } else if (job.isError) {
      progress = <ErrorState error={job.error} onRetry={() => void job.refetch()} />;
    } else {
      progress = <ImportJobStatus job={job.data} />;
    }
  }

  return (
    <section aria-labelledby="ioc-import-title" className="space-y-4">
      <h1 id="ioc-import-title" className="font-mono text-xl font-bold tracking-tight">
        批次匯入 IOC
      </h1>

      <Card className="p-6">
        <IocImportForm
          submitting={startImport.isPending}
          error={startImport.error instanceof ApiError ? startImport.error : null}
          onSubmit={(values) =>
            startImport.mutate(values, {
              onSuccess: (accepted) => setJobId(accepted.importJobId ?? null),
            })
          }
        />
      </Card>

      {progress === null ? null : <Card className="p-6">{progress}</Card>}
    </section>
  );
}
