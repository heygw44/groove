import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { AxiosError, type AxiosResponse } from 'axios';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { addWishlist, changeWishlistAlert } from '@/api/wishlist';
import { useChangeWishlistAlert, useToggleWishlist } from '@/hooks/mutations/useWishlistMutations';
import { albumKeys, productKeys, recentViewKeys } from '@/hooks/queries/queryKeys';
import type { AlbumDetail } from '@/types/catalog';
import type { ProductDetail, ProductSummary } from '@/types/product';
import type { WishlistItem } from '@/types/wishlist';

vi.mock('@/api/wishlist', () => ({
  addWishlist: vi.fn(),
  removeWishlist: vi.fn(),
  changeWishlistAlert: vi.fn(),
}));

const PRODUCT_ID = 7;
const OTHER_ID = 8;
const ALBUM_ID = 3;

const summaryFixture = (id: number) => ({ id, wishlisted: false }) as ProductSummary;

const seedCardCaches = (queryClient: QueryClient) => {
  queryClient.setQueryData(recentViewKeys.all, [
    summaryFixture(PRODUCT_ID),
    summaryFixture(OTHER_ID),
  ]);
  queryClient.setQueryData(albumKeys.detail(ALBUM_ID), {
    id: ALBUM_ID,
    pressings: [summaryFixture(PRODUCT_ID), summaryFixture(OTHER_ID)],
  } as AlbumDetail);
};

const cardHearts = (queryClient: QueryClient) => ({
  recent: queryClient.getQueryData<ProductSummary[]>(recentViewKeys.all)?.map((p) => p.wishlisted),
  pressings: queryClient
    .getQueryData<AlbumDetail>(albumKeys.detail(ALBUM_ID))
    ?.pressings.map((p) => p.wishlisted),
});

const apiError = (status: number, code: string) =>
  new AxiosError('error', 'ERR_BAD_REQUEST', undefined, undefined, {
    status,
    data: { error: { code, message: code } },
  } as AxiosResponse);

const detailFixture = (overrides: Partial<ProductDetail> = {}) =>
  ({ id: PRODUCT_ID, wishlisted: false, alertEnabled: undefined, ...overrides }) as ProductDetail;

const setup = (detail: ProductDetail) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(productKeys.detail(PRODUCT_ID), detail);
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { queryClient, invalidate, wrapper };
};

const detailInvalidated = (invalidate: { mock: { calls: unknown[][] } }) =>
  invalidate.mock.calls.some(
    ([filters]) =>
      JSON.stringify((filters as { queryKey?: unknown } | undefined)?.queryKey) ===
      JSON.stringify(productKeys.detail(PRODUCT_ID)),
  );

describe('useToggleWishlist', () => {
  it('이미 담긴 상품이면 하트는 유지하고 알림 값은 되돌린 뒤 상세를 다시 받는다', async () => {
    // given
    vi.mocked(addWishlist).mockRejectedValue(apiError(409, 'WISHLIST_ALREADY_EXISTS'));
    const { queryClient, invalidate, wrapper } = setup(detailFixture({ wishlisted: false }));
    const { result } = renderHook(() => useToggleWishlist(), { wrapper });

    // when
    result.current.mutate({ productId: PRODUCT_ID, wishlisted: false });

    // then
    await waitFor(() => expect(result.current.isError).toBe(true));
    const detail = queryClient.getQueryData<ProductDetail>(productKeys.detail(PRODUCT_ID));
    expect(detail?.wishlisted).toBe(true);
    expect(detail?.alertEnabled).not.toBe(true);
    expect(detailInvalidated(invalidate)).toBe(true);
  });

  it('위시에 담으면 최근 본 상품과 다른 에디션 카드의 하트도 해당 상품만 바뀐다', async () => {
    // given
    vi.mocked(addWishlist).mockResolvedValue({} as WishlistItem);
    const { queryClient, wrapper } = setup(detailFixture({ wishlisted: false }));
    seedCardCaches(queryClient);
    const { result } = renderHook(() => useToggleWishlist(), { wrapper });

    // when
    result.current.mutate({ productId: PRODUCT_ID, wishlisted: false });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(cardHearts(queryClient)).toEqual({ recent: [true, false], pressings: [true, false] });
  });

  it('일반 오류로 실패하면 최근 본 상품과 다른 에디션 카드의 하트를 되돌린다', async () => {
    // given
    vi.mocked(addWishlist).mockRejectedValue(apiError(500, 'INTERNAL_SERVER_ERROR'));
    const { queryClient, wrapper } = setup(detailFixture({ wishlisted: false }));
    seedCardCaches(queryClient);
    const { result } = renderHook(() => useToggleWishlist(), { wrapper });

    // when
    result.current.mutate({ productId: PRODUCT_ID, wishlisted: false });

    // then
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(cardHearts(queryClient)).toEqual({ recent: [false, false], pressings: [false, false] });
  });
});

describe('useChangeWishlistAlert', () => {
  it('위시 행이 없으면 롤백하고 상세를 다시 받는다', async () => {
    // given
    vi.mocked(changeWishlistAlert).mockRejectedValue(apiError(404, 'WISHLIST_NOT_FOUND'));
    const { queryClient, invalidate, wrapper } = setup(
      detailFixture({ wishlisted: true, alertEnabled: false }),
    );
    const { result } = renderHook(() => useChangeWishlistAlert(), { wrapper });

    // when
    result.current.mutate({ productId: PRODUCT_ID, alertEnabled: true });

    // then
    await waitFor(() => expect(result.current.isError).toBe(true));
    const detail = queryClient.getQueryData<ProductDetail>(productKeys.detail(PRODUCT_ID));
    expect(detail?.alertEnabled).toBe(false);
    expect(detailInvalidated(invalidate)).toBe(true);
  });
});
