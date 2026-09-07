import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { NotificationRow } from '@/components/notification/NotificationRow';
import {
  useDeleteNotification,
  useDeleteReadNotifications,
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
  const [isDeleteReadOpen, setIsDeleteReadOpen] = useState(false);

  const { showToast } = useToast();
  const { data, isPending, isError, isPlaceholderData, refetch } = useNotifications({
    page,
    size: PAGE_SIZE,
    unreadOnly,
  });
  const markReadMutation = useMarkNotificationRead();
  const markAllReadMutation = useMarkAllNotificationsRead();
  const deleteMutation = useDeleteNotification();
  const deleteReadMutation = useDeleteReadNotifications();

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
      onSuccess: () => showToast('success', '모든 알림을 읽음 처리했습니다.'),
      onError: (error) => showToast('error', getErrorMessage(error)),
    });
  };

  const handleRead = (id: number) => {
    // 이동이 이미 일어난 뒤라 실패해도 사용자에게 알리지 않는다.
    markReadMutation.mutate({ id });
  };

  const handleDelete = (id: number) => {
    // 지금 페이지의 마지막 한 건을 지우는 경우, 삭제 후 목록이 비므로 이전 페이지로 내려간다.
    const isLastItemOnPage = data?.content.length === 1 && page > 0;

    deleteMutation.mutate(
      { id },
      {
        onSuccess: () => {
          showToast('success', '알림을 삭제했습니다.');
          if (isLastItemOnPage) {
            updatePage(page - 1);
          }
        },
        onError: (error) => showToast('error', getErrorMessage(error)),
      },
    );
  };

  const handleDeleteRead = () => {
    deleteReadMutation.mutate(undefined, {
      onSuccess: () => {
        setIsDeleteReadOpen(false);
        showToast('success', '읽은 알림을 모두 삭제했습니다.');
      },
      onError: (error) => {
        setIsDeleteReadOpen(false);
        showToast('error', getErrorMessage(error));
      },
    });
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
            읽지 않은 알림만
          </Button>
          <Button
            variant="secondary"
            size="sm"
            onClick={handleMarkAllRead}
            disabled={markAllReadMutation.isPending}
          >
            전체 읽음
          </Button>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setIsDeleteReadOpen(true)}
            disabled={deleteReadMutation.isPending}
          >
            읽은 알림 지우기
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
            title="알림을 불러오지 못했습니다"
            description="잠시 후 다시 시도해주세요."
            action={
              <Button variant="secondary" onClick={() => refetch()}>
                다시 시도
              </Button>
            }
          />
        )}

        {!isPending && !isError && data && data.content.length === 0 && (
          <EmptyState title={unreadOnly ? '읽지 않은 알림이 없습니다' : '받은 알림이 없습니다'} />
        )}

        {!isPending && !isError && data && data.content.length > 0 && (
          <div className={isPlaceholderData ? 'opacity-60' : ''}>
            <div className="divide-y divide-line">
              {data.content.map((item) => (
                <NotificationRow
                  key={item.id}
                  item={item}
                  onRead={handleRead}
                  onDelete={handleDelete}
                />
              ))}
            </div>

            <div className="mt-6">
              <Pagination page={page} totalPages={data.totalPages} onChange={updatePage} />
            </div>
          </div>
        )}
      </div>

      <ConfirmDialog
        open={isDeleteReadOpen}
        onClose={() => setIsDeleteReadOpen(false)}
        onConfirm={handleDeleteRead}
        title="읽은 알림을 모두 삭제하시겠습니까?"
        description="읽은 알림이 전부 사라집니다. 되돌릴 수 없습니다."
        pending={deleteReadMutation.isPending}
      />
    </div>
  );
}
