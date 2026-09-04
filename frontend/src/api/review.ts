import { client, unwrap } from '@/api/client';
import type { ApiResponse, PageResponse } from '@/types/api';
import type { Review, ReviewEligibility, ReviewListParams, ReviewWriteRequest } from '@/types/review';

export const getReviews = (productId: number, params: ReviewListParams) =>
  unwrap(
    client.get<ApiResponse<PageResponse<Review>>>(`/products/${productId}/reviews`, { params }),
  );

export const getReviewEligibility = (productId: number) =>
  unwrap(
    client.get<ApiResponse<ReviewEligibility>>(`/products/${productId}/reviews/eligibility`),
  );

export const createReview = (productId: number, payload: ReviewWriteRequest) =>
  unwrap(client.post<ApiResponse<Review>>(`/products/${productId}/reviews`, payload));

export const updateReview = (reviewId: number, payload: ReviewWriteRequest) =>
  unwrap(client.patch<ApiResponse<Review>>(`/reviews/${reviewId}`, payload));

export const deleteReview = async (reviewId: number) => {
  await client.delete<ApiResponse<void>>(`/reviews/${reviewId}`);
};
