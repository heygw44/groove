import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { getErrorMessage } from '@/utils/apiError';

interface QueryErrorStateProps {
  error: unknown;
  onRetry?: () => void;
  title?: string;
  titleAs?: 'p' | 'h1' | 'h2';
}

// 조회 실패 문구를 한 곳에 모아 페이지마다 다르게 쓰지 않게 한다.
export function QueryErrorState({
  error,
  onRetry,
  title = '불러오지 못했습니다.',
  titleAs,
}: QueryErrorStateProps) {
  return (
    <EmptyState
      title={title}
      description={getErrorMessage(error)}
      titleAs={titleAs}
      action={
        onRetry && (
          <Button variant="secondary" size="sm" onClick={onRetry}>
            다시 시도
          </Button>
        )
      }
    />
  );
}
