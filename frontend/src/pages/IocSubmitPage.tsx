import { useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../api/client';
import { Card } from '../components/ui/card';
import { IocSubmitForm } from '../features/ioc/components/IocSubmitForm';
import { useSubmitIoc } from '../features/ioc/hooks/useIocWrite';
import type { IocDto } from '../features/ioc/types';

/**
 * §12.5 /iocs/new(需登入 + ioc:submit)。
 * 提交走完整 pipeline,因此重複的值會合併到既有 IOC——成功訊息要說清楚是新建還是合併,
 * 否則使用者會以為提交失敗(回應的 id 指向既有那一筆)。
 */
export default function IocSubmitPage() {
  const submit = useSubmitIoc();
  const [created, setCreated] = useState<IocDto | null>(null);

  return (
    <section aria-labelledby="ioc-submit-title" className="space-y-4">
      <h1 id="ioc-submit-title" className="font-mono text-xl font-bold tracking-tight">
        提交 IOC
      </h1>

      {created ? (
        <Card className="p-4" role="status">
          <p className="text-sm">
            已提交:
            <Link to={`/iocs/${String(created.id)}`} className="ml-1 font-mono underline">
              {created.value}
            </Link>
            <span className="ml-2 text-muted-foreground">
              目前狀態 {created.status}、TLP {created.tlp}
            </span>
          </p>
        </Card>
      ) : null}

      <Card className="p-6">
        <IocSubmitForm
          submitting={submit.isPending}
          error={submit.error instanceof ApiError ? submit.error : null}
          onSubmit={(values) => submit.mutate(values, { onSuccess: setCreated })}
        />
      </Card>
    </section>
  );
}
