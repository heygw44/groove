import { useEffect, useState } from 'react';
import { useRouteError } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { PageContainer } from '@/components/common/PageContainer';
import {
  canAutoReloadForChunkError,
  isChunkLoadError,
  markChunkReload,
  reloadPage,
} from '@/utils/chunkReload';

type ErrorKind = 'chunk-reloading' | 'chunk-manual' | 'unknown';

function classifyError(error: unknown): ErrorKind {
  if (!isChunkLoadError(error)) return 'unknown';
  return canAutoReloadForChunkError() ? 'chunk-reloading' : 'chunk-manual';
}

const DESCRIPTIONS: Record<ErrorKind, string> = {
  'chunk-reloading': '새 버전을 불러오는 중입니다.',
  'chunk-manual': '새 버전이 배포되었습니다. 새로고침 후 다시 시도해주세요.',
  unknown: '잠시 후 다시 시도해주세요.',
};

export function RouteErrorBoundary() {
  const error = useRouteError();
  console.error(error);

  /**
   * 자동 새로고침 여부는 에러마다 한 번만 판정한다. 렌더 중 이전 에러와 비교해 갱신하는 패턴이라
   * effect 안 setState 없이 같은 프레임에 반영된다.
   */
  const [judged, setJudged] = useState(() => ({ error, kind: classifyError(error) }));
  if (judged.error !== error) {
    setJudged({ error, kind: classifyError(error) });
  }
  const { kind } = judged;

  useEffect(() => {
    // 기록이 먼저여야 새로고침 후에도 청크가 없을 때 가드에 걸려 루프가 끊긴다.
    if (kind === 'chunk-reloading' && markChunkReload()) {
      reloadPage();
    }
  }, [error, kind]);

  return (
    <PageContainer>
      <EmptyState
        titleAs="h1"
        title="화면을 표시할 수 없습니다."
        description={DESCRIPTIONS[kind]}
        action={<Button onClick={reloadPage}>새로고침</Button>}
      />
    </PageContainer>
  );
}
