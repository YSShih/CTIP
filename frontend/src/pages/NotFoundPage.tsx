import { Link } from 'react-router';
import { EmptyState } from '../components/StateViews';

export default function NotFoundPage() {
  return (
    <EmptyState
      title="404 — 頁面不存在"
      description="您要找的頁面不存在或已移動。"
      action={
        <Link
          to="/"
          className="text-sm font-medium text-primary underline underline-offset-4 hover:opacity-80"
        >
          回到儀表板
        </Link>
      }
    />
  );
}
