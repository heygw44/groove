import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { EmptyState } from '@/components/common/EmptyState';
import { LinkButton } from '@/components/common/LinkButton';
import { Pagination } from '@/components/common/Pagination';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { AlbumWatchRow } from '@/components/notification/AlbumWatchRow';
import { useToggleAlbumWatch } from '@/hooks/mutations/useAlbumWatchMutations';
import { useAlbumWatches } from '@/hooks/queries/useAlbumWatches';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';

const PAGE_SIZE = 20;

const parsePage = (searchParams: URLSearchParams) => {
  const raw = Number(searchParams.get('page'));
  return Number.isInteger(raw) && raw >= 0 ? raw : 0;
};

export default function AlbumWatchListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePage(searchParams);
  const [removingAlbumId, setRemovingAlbumId] = useState<number | undefined>(undefined);

  const { showToast } = useToast();
  const { data, isPending, isError, error, isPlaceholderData, refetch } = useAlbumWatches({
    page,
    size: PAGE_SIZE,
  });
  const toggleAlbumWatchMutation = useToggleAlbumWatch();

  const updatePage = (nextPage: number) => {
    setSearchParams({ page: String(nextPage) });
  };

  const handleRemove = (albumId: number, albumTitle: string) => {
    // 지금 페이지의 마지막 한 건을 지우는 경우, 삭제 후 목록이 비므로 이전 페이지로 내려간다.
    const isLastItemOnPage = data?.content.length === 1 && page > 0;

    setRemovingAlbumId(albumId);
    toggleAlbumWatchMutation.mutate(
      { albumId, albumTitle, watched: true },
      {
        onSuccess: () => {
          showToast('success', '구독을 해지했습니다.');
          if (isLastItemOnPage) {
            updatePage(page - 1);
          }
        },
        onError: (error) => {
          const code = getErrorCode(error);
          if (code !== 'ALBUM_WATCH_NOT_FOUND') {
            showToast('error', getErrorMessage(error));
          }
        },
        onSettled: () => setRemovingAlbumId(undefined),
      },
    );
  };

  return (
    <div>
      <h2 className="text-xl font-bold">구독한 앨범</h2>

      <div className="mt-4">
        {isPending && (
          <div className="flex min-h-48 items-center justify-center">
            <Spinner />
          </div>
        )}

        {!isPending && isError && (
          <QueryErrorState
            error={error}
            onRetry={refetch}
            title="구독 목록을 불러오지 못했습니다."
          />
        )}

        {!isPending && !isError && data && data.content.length === 0 && (
          <EmptyState
            title="구독한 앨범이 없습니다"
            description="앨범 상세에서 새 에디션 알림을 구독해보세요."
            action={
              <LinkButton to="/products" variant="secondary">
                상품 보러 가기
              </LinkButton>
            }
          />
        )}

        {!isPending && !isError && data && data.content.length > 0 && (
          <div className={isPlaceholderData ? 'opacity-60' : ''}>
            <div className="divide-y divide-line">
              {data.content.map((item) => (
                <AlbumWatchRow
                  key={item.id}
                  item={item}
                  onRemove={() => handleRemove(item.albumId, item.albumTitle)}
                  removing={removingAlbumId === item.albumId}
                />
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
