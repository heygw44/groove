import { useMutation, useQueryClient } from '@tanstack/react-query';

import { createReview, deleteReview, updateReview } from '@/api/review';
import { productKeys, reviewKeys } from '@/hooks/queries/queryKeys';
import type { ReviewWriteRequest } from '@/types/review';

/*
 * 리뷰 CRUD 는 서버가 product.averageRating·reviewCount 도 즉시 갱신하므로
 * 리뷰 캐시뿐 아니라 상품 상세도 함께 무효화해야 상단 요약이 따라온다.
 */
const useInvalidateReviews = (productId: number) => {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: reviewKeys.product(productId) });
    queryClient.invalidateQueries({ queryKey: productKeys.detail(productId) });
  };
};

export const useCreateReview = (productId: number) => {
  const invalidate = useInvalidateReviews(productId);

  return useMutation({
    mutationFn: (payload: ReviewWriteRequest) => createReview(productId, payload),
    onSuccess: invalidate,
  });
};

export const useUpdateReview = (productId: number) => {
  const invalidate = useInvalidateReviews(productId);

  return useMutation({
    mutationFn: ({ reviewId, payload }: { reviewId: number; payload: ReviewWriteRequest }) =>
      updateReview(reviewId, payload),
    onSuccess: invalidate,
  });
};

export const useDeleteReview = (productId: number) => {
  const invalidate = useInvalidateReviews(productId);

  return useMutation({
    mutationFn: (reviewId: number) => deleteReview(reviewId),
    onSuccess: invalidate,
  });
};
