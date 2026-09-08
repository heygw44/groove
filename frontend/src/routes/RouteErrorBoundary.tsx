import { useRouteError } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { PageContainer } from '@/components/common/PageContainer';

/** 배포 직후 오래된 청크를 참조하는 동적 import 실패는 새로고침으로만 해결된다. */
function isChunkLoadError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return (
    message.includes('Failed to fetch dynamically imported module') ||
    message.includes('Importing a module script failed')
  );
}

export function RouteErrorBoundary() {
  const error = useRouteError();
  console.error(error);

  const description = isChunkLoadError(error)
    ? '새 버전이 배포되었습니다. 새로고침 후 다시 시도해주세요.'
    : '잠시 후 다시 시도해주세요.';

  return (
    <PageContainer>
      <EmptyState
        titleAs="h1"
        title="화면을 표시할 수 없습니다."
        description={description}
        action={<Button onClick={() => window.location.reload()}>새로고침</Button>}
      />
    </PageContainer>
  );
}
