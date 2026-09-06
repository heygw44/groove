import type { HomeRecommendResponse, RecommendItem } from '@/types/recommend';

type RecommendCache = RecommendItem[] | HomeRecommendResponse;

const patchItems = (items: RecommendItem[], productId: number, wishlisted: boolean) =>
  items.map((item) =>
    item.product.id === productId ? { ...item, product: { ...item.product, wishlisted } } : item,
  );

/**
 * 추천 캐시는 관련 상품(배열)과 홈(profileRequired + items) 두 모양이라 둘 다 받는다.
 * 하트를 누른 상품만 wishlisted 를 바꾸고 나머지 구조는 그대로 둔다.
 */
export const patchRecommendWishlisted = <T extends RecommendCache | undefined>(
  cache: T,
  productId: number,
  wishlisted: boolean,
): T => {
  if (!cache) {
    return cache;
  }
  if (Array.isArray(cache)) {
    return patchItems(cache, productId, wishlisted) as T;
  }
  return { ...cache, items: patchItems(cache.items, productId, wishlisted) } as T;
};
