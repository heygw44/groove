import { useLocation, useNavigate } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { useToast } from '@/components/common/toastContext';
import { useToggleAlbumWatch } from '@/hooks/mutations/useAlbumWatchMutations';
import { useAlbumWatches } from '@/hooks/queries/useAlbumWatches';
import { useAuthStore } from '@/store/authStore';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';

interface AlbumWatchButtonProps {
  albumId: number;
  albumTitle: string;
  className?: string;
}

export function AlbumWatchButton({ albumId, albumTitle, className = '' }: AlbumWatchButtonProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { showToast } = useToast();
  const isLoggedIn = useAuthStore((s) => Boolean(s.accessToken));
  const { data } = useAlbumWatches();
  const toggle = useToggleAlbumWatch();

  // 서버에 단건 조회 API 가 없어 전체 구독 목록에서 albumId 를 찾아 구독 여부를 판단한다.
  const watched = Boolean(data?.content.some((watch) => watch.albumId === albumId));

  const handleClick = () => {
    if (!isLoggedIn) {
      const redirect = encodeURIComponent(`${location.pathname}${location.search}`);
      navigate(`/login?redirect=${redirect}`);
      return;
    }

    toggle.mutate(
      { albumId, albumTitle, watched },
      {
        onError: (error) => {
          const code = getErrorCode(error);
          if (code !== 'ALBUM_WATCH_ALREADY_EXISTS' && code !== 'ALBUM_WATCH_NOT_FOUND') {
            showToast('error', getErrorMessage(error));
          }
        },
      },
    );
  };

  return (
    <Button
      variant="secondary"
      size="sm"
      aria-pressed={watched}
      disabled={toggle.isPending}
      onClick={handleClick}
      className={`${watched ? 'border-content text-content' : ''} ${className}`}
    >
      {watched ? '알림 받는 중' : '새 에디션 알림 받기'}
    </Button>
  );
}
