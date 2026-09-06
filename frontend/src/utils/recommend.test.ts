import { describe, expect, it } from 'vitest';

import type { HomeRecommendResponse, RecommendItem } from '@/types/recommend';
import { patchRecommendWishlisted } from '@/utils/recommend';

const item = (id: number, wishlisted = false): RecommendItem => ({
  product: {
    id,
    title: `LP ${id}`,
    artistName: 'Artist',
    price: 30000,
    status: 'ON_SALE',
    wishlisted,
  },
  reasons: ['SAME_GENRE'],
});

describe('patchRecommendWishlisted()', () => {
  it('관련 상품 배열에서 해당 상품의 wishlisted 만 바꾼다', () => {
    // given
    const cache = [item(1), item(2)];

    // when
    const patched = patchRecommendWishlisted(cache, 2, true);

    // then
    expect(patched[0].product.wishlisted).toBe(false);
    expect(patched[1].product.wishlisted).toBe(true);
    expect(patched[1].reasons).toEqual(['SAME_GENRE']);
  });

  it('홈 응답이면 profileRequired 는 두고 items 만 바꾼다', () => {
    // given
    const cache: HomeRecommendResponse = { profileRequired: false, items: [item(1, true)] };

    // when
    const patched = patchRecommendWishlisted(cache, 1, false);

    // then
    expect(patched.profileRequired).toBe(false);
    expect(patched.items[0].product.wishlisted).toBe(false);
  });

  it('캐시가 없으면 그대로 돌려준다', () => {
    // when & then
    expect(patchRecommendWishlisted(undefined, 1, true)).toBeUndefined();
  });
});
