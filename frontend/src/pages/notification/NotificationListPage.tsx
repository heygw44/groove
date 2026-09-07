import { useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { NotificationRow } from '@/components/notification/NotificationRow';
import {
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
} from '@/hooks/mutations/useNotificationMutations';
import { useNotifications } from '@/hooks/queries/useNotifications';
import { getErrorMessage } from '@/utils/apiError';

const PAGE_SIZE = 20;

const parsePage = (searchParams: URLSearchParams) => {
  const raw = Number(searchParams.get('page'));
  return Number.isInteger(raw) && raw >= 0 ? raw : 0;
};

export default function NotificationListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePage(searchParams);
  const unreadOnly = searchParams.get('unreadOnly') === 'true';

  const { showToast } = useToast();
  const { data, isPending, isError, refetch } = useNotifications({
    page,
    size: PAGE_SIZE,
    unreadOnly,
  });
  const markReadMutation = useMarkNotificationRead();
  const markAllReadMutation = useMarkAllNotificationsRead();

  const updatePage = (nextPage: number) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('page', String(nextPage));
      return next;
    });
  };

  const toggleUnreadOnly = () => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (unreadOnly) {
        next.delete('unreadOnly');
      } else {
        next.set('unreadOnly', 'true');
      }
      next.set('page', '0');
      return next;
    });
  };

  const handleMarkAllRead = () => {
    markAllReadMutation.mutate(undefined, {
      onSuccess: () => showToast('success', '모두 읽음으로 표시했습니다.'),
      onError: (error) => showToast('error', getErrorMessage(error)),
    });
  };

  const handleRead = (id: number) => {
    // 이동이 이미 일어난 뒤라 실패해도 사용자에게 알리지 않는다.
    markReadMutation.mutate({ id });
  };

  return (
    <div>
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-xl font-bold">알림</h2>
        <div className="flex items-center gap-2">
          <Button
            variant={unreadOnly ? 'primary' : 'secondary'}
            size="sm"
            aria-pressed={unreadOnly}
            onClick={toggleUnreadOnly}
          >
            안 읽음만
          </Button>
          <Button
            variant="secondary"
            size="sm"
            onClick={handleMarkAllRead}
            disabled={markAllReadMutation.isPending}
          >
            전체 읽음
          </Button>
        </div>
      </div>

      <div className="mt-4">
        {isPending && (
          <div className="flex min-h-48 items-center justify-center">
            <Spinner />
          </div>
        )}

        {!isPending && isError && (
          <EmptyState
            title="알림을 불러오지 못했습니다."
            description="잠시 후 다시 시도해주세요."
            action={
              <Button variant="secondary" onClick={() => refetch()}>
                다시 시도
              </Button>
            }
          />
        )}

        {!isPending && !isError && data && data.content.length === 0 && (
          <EmptyState title={unreadOnly ? '안 읽은 알림이 없습니다' : '받은 알림이 없습니다'} />
        )}

        {!isPending && !isError && data && data.content.length > 0 && (
          <div>
            <div className="divide-y divide-line">
              {data.content.map((item) => (
                <NotificationRow key={item.id} item={item} onRead={handleRead} />
              ))}
            </div>

            <div className="mt-6">
              <Pagination page={page} totalPages={data.totalPages} onChange={updatePage} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
