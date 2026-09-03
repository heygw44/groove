import { describe, expect, it } from 'vitest';

import { formatPrice } from '@/utils/formatPrice';

describe('formatPrice()', () => {
  it('천 단위로 끊어 원 단위로 표시한다', () => {
    // given & when & then
    expect(formatPrice(42000)).toBe('42,000원');
  });

  it('0 원도 그대로 표시한다', () => {
    // given & when & then
    expect(formatPrice(0)).toBe('0원');
  });

  it.each([
    [45000.4, '45,000원'],
    [45000.5, '45,001원'],
    [45000.99, '45,001원'],
  ])('소수 가격 %s 는 원 단위로 반올림한다', (amount, expected) => {
    // given & when & then
    expect(formatPrice(amount)).toBe(expected);
  });
});
