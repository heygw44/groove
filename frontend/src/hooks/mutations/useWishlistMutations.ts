import type { QueryKey } from '@tanstack/react-query';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { addWishlist, changeWishlistAlert, removeWishlist } from '@/api/wishlist';
import { productKeys, recommendKeys, wishlistKeys } from '@/hooks/queries/queryKeys';
import type { PageResponse } from '@/types/api';
import type { ProductDetail, ProductSummary } from '@/types/product';
import type { HomeRecommendResponse, RecommendItem } from '@/types/recommend';
import type { WishlistItem } from '@/types/wishlist';
import { getErrorCode } from '@/utils/apiError';
import { patchRecommendWishlisted } from '@/utils/recommend';

type RecommendCache = RecommendItem[] | HomeRecommendResponse;

interface ToggleWishlistVariables {
  productId: number;
  /** 토글 전 현재 값. */
  wishlisted: boolean;
}

interface ToggleWishlistContext {
  hadDetail: boolean;
  previousDetail?: ProductDetail;
  previousLists: Array<[QueryKey, PageResponse<ProductSummary> | undefined]>;
  previousRecommends: Array<[QueryKey, RecommendCache | undefined]>;
}

export const useToggleWishlist = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ productId, wishlisted }: ToggleWishlistVariables): Promise<void> => {
      if (wishlisted) {
        await removeWishlist(productId);
      } else {
        await addWishlist(productId);
      }
    },
    // 하트를 누르는 즉시 상세/목록에 반영해야 버튼이 즉각 반응하는 것처럼 보인다.
    onMutate: async ({ productId, wishlisted }): Promise<ToggleWishlistContext> => {
      await queryClient.cancelQueries({ queryKey: productKeys.detail(productId) });
      await queryClient.cancelQueries({ queryKey: productKeys.all });
      await queryClient.cancelQueries({ queryKey: recommendKeys.all });

      const hadDetail = queryClient.getQueryState(productKeys.detail(productId)) !== undefined;
      const previousDetail = queryClient.getQueryData<ProductDetail>(productKeys.detail(productId));
      const previousLists = queryClient.getQueriesData<PageResponse<ProductSummary>>({
        queryKey: productKeys.all,
      });
      const previousRecommends = queryClient.getQueriesData<RecommendCache>({
        queryKey: recommendKeys.all,
      });

      // 서버는 위시에 담을 때 alertEnabled 를 true 로 만든다. 상세는 재조회하지 않으니
      // 여기서 같이 맞춰두지 않으면 알림 토글이 꺼진 채로 보인다.
      queryClient.setQueryData<ProductDetail>(
        productKeys.detail(productId),
        (old) =>
          old && { ...old, wishlisted: !wishlisted, alertEnabled: wishlisted ? undefined : true },
      );
      queryClient.setQueriesData<PageResponse<ProductSummary>>(
        { queryKey: productKeys.all },
        (old) =>
          old && {
            ...old,
            content: old.content.map((product) =>
              product.id === productId ? { ...product, wishlisted: !wishlisted } : product,
            ),
          },
      );

      // 추천 카드의 하트도 같은 상품이면 즉시 바뀌어야 한다. 캐시 모양이 둘이라 유틸에 맡긴다.
      queryClient.setQueriesData<RecommendCache>({ queryKey: recommendKeys.all }, (old) =>
        patchRecommendWishlisted(old, productId, !wishlisted),
      );

      return { hadDetail, previousDetail, previousLists, previousRecommends };
    },
    onError: (error, { productId }, context) => {
      const code = getErrorCode(error);
      // 이미 담겼거나 이미 빠져 있다는 뜻이라 서버 상태가 곧 우리가 낙관적으로 반영한 값이다.
      if (code === 'WISHLIST_ALREADY_EXISTS' || code === 'WISHLIST_NOT_FOUND') {
        return;
      }
      if (!context) {
        return;
      }
      if (context.hadDetail) {
        queryClient.setQueryData(productKeys.detail(productId), context.previousDetail);
      }
      context.previousLists.forEach(([key, data]) => {
        queryClient.setQueryData(key, data);
      });
      context.previousRecommends.forEach(([key, data]) => {
        queryClient.setQueryData(key, data);
      });
    },
    // 상품 상세/목록은 낙관적 값이 곧 서버 값이라 재조회하지 않는다.
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: wishlistKeys.all });
      // 추천은 위시 신호를 서버가 반영하므로 stale 로 만들되, 지금 화면에서 바로 재조회하면
      // 방금 하트를 누른 카드가 후보에서 빠져 사라지므로 다음 진입 때 새로 받게 한다.
      queryClient.invalidateQueries({ queryKey: recommendKeys.all, refetchType: 'none' });
    },
  });
};

interface ChangeWishlistAlertVariables {
  productId: number;
  /** 토글이 아니라 값 지정 API 라 여기 들어오는 값이 곧 바꾸고 싶은 새 상태다. */
  alertEnabled: boolean;
}

interface ChangeWishlistAlertContext {
  hadDetail: boolean;
  previousDetail?: ProductDetail;
  previousLists: Array<[QueryKey, PageResponse<WishlistItem> | undefined]>;
}

export const useChangeWishlistAlert = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ productId, alertEnabled }: ChangeWishlistAlertVariables) =>
      changeWishlistAlert(productId, alertEnabled),
    onMutate: async ({
      productId,
      alertEnabled,
    }): Promise<ChangeWishlistAlertContext> => {
      await queryClient.cancelQueries({ queryKey: productKeys.detail(productId) });
      await queryClient.cancelQueries({ queryKey: wishlistKeys.all });

      const hadDetail = queryClient.getQueryState(productKeys.detail(productId)) !== undefined;
      const previousDetail = queryClient.getQueryData<ProductDetail>(productKeys.detail(productId));
      const previousLists = queryClient.getQueriesData<PageResponse<WishlistItem>>({
        queryKey: wishlistKeys.all,
      });

      queryClient.setQueryData<ProductDetail>(
        productKeys.detail(productId),
        (old) => old && { ...old, alertEnabled },
      );
      queryClient.setQueriesData<PageResponse<WishlistItem>>(
        { queryKey: wishlistKeys.all },
        (old) =>
          old && {
            ...old,
            content: old.content.map((item) =>
              item.productId === productId ? { ...item, alertEnabled } : item,
            ),
          },
      );

      return { hadDetail, previousDetail, previousLists };
    },
    onError: (_error, { productId }, context) => {
      if (!context) {
        return;
      }
      if (context.hadDetail) {
        queryClient.setQueryData(productKeys.detail(productId), context.previousDetail);
      }
      context.previousLists.forEach(([key, data]) => {
        queryClient.setQueryData(key, data);
      });
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: wishlistKeys.all });
    },
  });
};
