import { useLocation, useNavigate } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { useToast } from '@/components/common/toastContext';
import { useToggleAlbumWatch } from '@/hooks/mutations/useAlbumWatchMutations';
import { useAuthStore } from '@/store/authStore';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';

interface AlbumWatchButtonProps {
  albumId: number;
  albumTitle: string;
  /** 앨범/상품 상세 응답에 실려 오는 값. 비로그인이면 null. */
  watched?: boolean;
  className?: string;
}

export function AlbumWatchButton({
  albumId,
  albumTitle,
  watched,
  className = '',
}: AlbumWatchButtonProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { showToast } = useToast();
  const isLoggedIn = useAuthStore((s) => Boolean(s.accessToken));
  const toggle = useToggleAlbumWatch();

  const isWatched = watched === true;

  const handleClick = () => {
    if (!isLoggedIn) {
      const redirect = encodeURIComponent(`${location.pathname}${location.search}`);
      navigate(`/login?redirect=${redirect}`);
      return;
    }

    toggle.mutate(
      { albumId, albumTitle, watched: isWatched },
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
      aria-pressed={isWatched}
      loading={toggle.isPending}
      onClick={handleClick}
      className={`${isWatched ? 'border-content text-content' : ''} ${className}`}
    >
      {isWatched ? '알림 받는 중' : '새 에디션 알림 받기'}
    </Button>
  );
}
