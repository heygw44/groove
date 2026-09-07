import { useMutation, useQueryClient } from '@tanstack/react-query';

import { addAlbumWatch, removeAlbumWatch } from '@/api/albumWatch';
import { albumWatchKeys } from '@/hooks/queries/queryKeys';
import type { AlbumWatchListResponse } from '@/types/albumWatch';
import { getErrorCode } from '@/utils/apiError';

interface ToggleAlbumWatchVariables {
  albumId: number;
  albumTitle: string;
  /** 토글 전 현재 값. */
  watched: boolean;
}

interface ToggleAlbumWatchContext {
  previous: AlbumWatchListResponse | undefined;
}

export const useToggleAlbumWatch = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ albumId, watched }: ToggleAlbumWatchVariables): Promise<void> => {
      if (watched) {
        await removeAlbumWatch(albumId);
      } else {
        await addAlbumWatch(albumId);
      }
    },
    onMutate: async ({
      albumId,
      albumTitle,
      watched,
    }): Promise<ToggleAlbumWatchContext> => {
      await queryClient.cancelQueries({ queryKey: albumWatchKeys.all });

      const previous = queryClient.getQueryData<AlbumWatchListResponse>(albumWatchKeys.all);

      queryClient.setQueryData<AlbumWatchListResponse>(albumWatchKeys.all, (old) => {
        if (!old) {
          return old;
        }
        if (watched) {
          return { content: old.content.filter((watch) => watch.albumId !== albumId) };
        }
        // id 는 음수 임시값이다. onSettled 재조회가 서버가 매긴 실제 id 로 바꿔치기한다.
        const optimisticWatch = {
          id: -albumId,
          albumId,
          albumTitle,
          createdAt: new Date().toISOString(),
        };
        return { content: [optimisticWatch, ...old.content] };
      });

      return { previous };
    },
    onError: (error, _variables, context) => {
      const code = getErrorCode(error);
      // 이미 구독 중이거나 이미 해제된 상태라는 뜻이라 서버 상태가 곧 우리가 낙관적으로 반영한 값이다.
      if (code === 'ALBUM_WATCH_ALREADY_EXISTS' || code === 'ALBUM_WATCH_NOT_FOUND') {
        return;
      }
      if (!context) {
        return;
      }
      queryClient.setQueryData(albumWatchKeys.all, context.previous);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: albumWatchKeys.all });
    },
  });
};
