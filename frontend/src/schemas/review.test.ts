import { describe, expect, it } from 'vitest';

import { reviewFormSchema, toReviewPayload } from '@/schemas/review';

const validValues = { rating: 4, title: '좋아요', content: '만족스러운 구매였습니다.' };

describe('reviewFormSchema', () => {
  it('rating 이 0 이면 실패한다', () => {
    // given & when
    const result = reviewFormSchema.safeParse({ ...validValues, rating: 0 });

    // then
    expect(result.success).toBe(false);
  });

  it('rating 이 5 면 통과한다', () => {
    // given & when
    const result = reviewFormSchema.safeParse({ ...validValues, rating: 5 });

    // then
    expect(result.success).toBe(true);
  });

  it('rating 이 6 이면 실패한다', () => {
    // given & when
    const result = reviewFormSchema.safeParse({ ...validValues, rating: 6 });

    // then
    expect(result.success).toBe(false);
  });

  it('title 이 101자면 실패한다', () => {
    // given
    const title = 'A'.repeat(101);

    // when
    const result = reviewFormSchema.safeParse({ ...validValues, title });

    // then
    expect(result.success).toBe(false);
  });
});

describe('toReviewPayload', () => {
  it('title·content 가 빈 문자열이면 키를 생략한다', () => {
    // given
    const values = { rating: 3, title: '', content: '' };

    // when
    const payload = toReviewPayload(values);

    // then
    expect(payload).toEqual({ rating: 3 });
  });

  it('title·content 가 있으면 그대로 담는다', () => {
    // given & when
    const payload = toReviewPayload(validValues);

    // then
    expect(payload).toEqual(validValues);
  });
});
