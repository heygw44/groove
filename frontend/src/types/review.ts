export type ReviewSort = 'latest' | 'ratingDesc' | 'ratingAsc';

export type ReviewIneligibleReason = 'LOGIN_REQUIRED' | 'PURCHASE_REQUIRED' | 'ALREADY_REVIEWED';

export interface Review {
  id: number;
  productId: number;
  nickname: string;
  rating: number;
  title?: string;
  content?: string;
  createdAt: string;
  updatedAt: string;
  mine: boolean;
}

export interface ReviewListParams {
  sort: ReviewSort;
  page: number;
  size: number;
}

export interface ReviewWriteRequest {
  rating: number;
  title?: string;
  content?: string;
}

export interface ReviewEligibility {
  eligible: boolean;
  reason?: ReviewIneligibleReason;
}
