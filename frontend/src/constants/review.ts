import type { ReviewIneligibleReason, ReviewSort } from '@/types/review';

export const REVIEW_SORT_OPTIONS: { value: ReviewSort; label: string }[] = [
  { value: 'latest', label: '최신순' },
  { value: 'ratingDesc', label: '별점 높은순' },
  { value: 'ratingAsc', label: '별점 낮은순' },
];

export const REVIEW_PAGE_SIZE = 10;

export const REVIEW_TITLE_MAX = 100;

export const REVIEW_CONTENT_MAX = 1000;

export const REVIEW_INELIGIBLE_MESSAGE: Record<ReviewIneligibleReason, string> = {
  LOGIN_REQUIRED: '로그인 후 리뷰를 작성할 수 있습니다.',
  PURCHASE_REQUIRED: '배송 완료된 주문이 있는 상품만 리뷰를 쓸 수 있습니다.',
  ALREADY_REVIEWED: '이미 이 상품에 리뷰를 작성했습니다.',
};
