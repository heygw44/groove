import { describe, expect, it } from 'vitest';

import { couponIssueSchema } from '@/schemas/coupon';

describe('couponIssueSchema', () => {
  it('공백만 입력하면 trim 후 빈 값으로 실패한다', () => {
    // given & when
    const result = couponIssueSchema.safeParse({ code: '   ' });

    // then
    expect(result.success).toBe(false);
  });

  it('31자를 넘으면 실패한다', () => {
    // given
    const code = 'A'.repeat(31);

    // when
    const result = couponIssueSchema.safeParse({ code });

    // then
    expect(result.success).toBe(false);
  });

  it('30자 이하의 값은 통과한다', () => {
    // given & when
    const result = couponIssueSchema.safeParse({ code: 'WELCOME10' });

    // then
    expect(result.success).toBe(true);
  });
});
