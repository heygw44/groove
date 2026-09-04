import { z } from 'zod';

import { REVIEW_CONTENT_MAX, REVIEW_TITLE_MAX } from '@/constants/review';
import type { Review, ReviewWriteRequest } from '@/types/review';

export const reviewFormSchema = z.object({
  rating: z
    .number()
    .int()
    .min(1, '별점을 선택해주세요.')
    .max(5, '별점은 5 이하여야 합니다.'),
  title: z.string().trim().max(REVIEW_TITLE_MAX, `제목은 ${REVIEW_TITLE_MAX}자 이하여야 합니다.`),
  content: z
    .string()
    .trim()
    .max(REVIEW_CONTENT_MAX, `내용은 ${REVIEW_CONTENT_MAX}자 이하여야 합니다.`),
});

export type ReviewFormValues = z.infer<typeof reviewFormSchema>;

export const EMPTY_REVIEW_FORM_VALUES: ReviewFormValues = {
  rating: 0,
  title: '',
  content: '',
};

export const toReviewFormValues = (review: Review): ReviewFormValues => ({
  rating: review.rating,
  title: review.title ?? '',
  content: review.content ?? '',
});

/** 빈 문자열은 전역 non_null 계약상 서버가 optional 로 취급하므로 키 자체를 뺀다. */
export const toReviewPayload = (values: ReviewFormValues): ReviewWriteRequest => ({
  rating: values.rating,
  ...(values.title && { title: values.title }),
  ...(values.content && { content: values.content }),
});
