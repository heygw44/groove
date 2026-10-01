import { describe, expect, it } from 'vitest';

import { getFallbackPage, toTotalPages } from '@/utils/pagination';

describe('getFallbackPage', () => {
  it('첫 페이지가 비어 있으면 이동하지 않는다', () => {
    // given & when
    const result = getFallbackPage(0, 0, 0);

    // then
    expect(result).toBeUndefined();
  });

  it('내용이 있으면 이동하지 않는다', () => {
    // given & when
    const result = getFallbackPage(2, 5, 3);

    // then
    expect(result).toBeUndefined();
  });

  it('뒤 페이지가 비면 마지막 페이지로 이동한다', () => {
    // given & when
    const result = getFallbackPage(3, 0, 2);

    // then
    expect(result).toBe(1);
  });

  it('전체가 비었으면 첫 페이지로 이동한다', () => {
    // given & when
    const result = getFallbackPage(1, 0, 0);

    // then
    expect(result).toBe(0);
  });
});

describe('toTotalPages', () => {
  it.each([
    [0, 20, 0],
    [1, 20, 1],
    [20, 20, 1],
    [21, 20, 2],
  ])('총 %i건, 페이지 크기 %i 이면 %i 페이지다', (totalElements, size, expected) => {
    // given & when
    const result = toTotalPages(totalElements, size);

    // then
    expect(result).toBe(expected);
  });
});
