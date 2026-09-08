import type { QueryKey } from '@tanstack/react-query';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { addAlbumWatch, removeAlbumWatch } from '@/api/albumWatch';
import { albumKeys, albumWatchKeys, productKeys } from '@/hooks/queries/queryKeys';
import type { AlbumDetail } from '@/types/catalog';
import type { ProductDetail } from '@/types/product';
import { getErrorCode } from '@/utils/apiError';

interface ToggleAlbumWatchVariables {
  albumId: number;
  albumTitle: string;
  /** 토글 전 현재 값. */
  watched: boolean;
}

interface ToggleAlbumWatchContext {
  previousAlbumDetails: Array<[QueryKey, AlbumDetail | undefined]>;
  previousProductDetails: Array<[QueryKey, ProductDetail | undefined]>;
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
    // 구독 버튼은 앨범/상품 상세에만 있다. 같은 앨범을 보고 있는 상세 캐시(앨범 자신 +
    // 그 앨범의 프레싱 상품들)의 watched 만 뒤집고, 구독 목록은 무효화로 맞춘다.
    onMutate: async ({ albumId, watched }): Promise<ToggleAlbumWatchContext> => {
      await queryClient.cancelQueries({ queryKey: albumKeys.all });
      await queryClient.cancelQueries({ queryKey: productKeys.details });

      const previousAlbumDetails = queryClient.getQueriesData<AlbumDetail>({
        queryKey: albumKeys.all,
      });
      const previousProductDetails = queryClient.getQueriesData<ProductDetail>({
        queryKey: productKeys.details,
      });

      queryClient.setQueriesData<AlbumDetail>({ queryKey: albumKeys.all }, (old) =>
        old && old.id === albumId ? { ...old, watched: !watched } : old,
      );
      queryClient.setQueriesData<ProductDetail>({ queryKey: productKeys.details }, (old) =>
        old && old.album.id === albumId
          ? { ...old, album: { ...old.album, watched: !watched } }
          : old,
      );

      return { previousAlbumDetails, previousProductDetails };
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
      context.previousAlbumDetails.forEach(([key, data]) => {
        queryClient.setQueryData(key, data);
      });
      context.previousProductDetails.forEach(([key, data]) => {
        queryClient.setQueryData(key, data);
      });
    },
    // 앨범/상품 상세는 낙관적 값이 곧 서버 값이라 재조회하지 않는다. 목록은 페이지네이션이
    // 얽혀 있어 무효화가 필요하다.
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: albumWatchKeys.all });
    },
  });
};
