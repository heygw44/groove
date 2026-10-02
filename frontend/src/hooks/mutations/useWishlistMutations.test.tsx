import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { AxiosError, type AxiosResponse } from 'axios';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { addWishlist, changeWishlistAlert } from '@/api/wishlist';
import { useChangeWishlistAlert, useToggleWishlist } from '@/hooks/mutations/useWishlistMutations';
import { productKeys } from '@/hooks/queries/queryKeys';
import type { ProductDetail } from '@/types/product';

vi.mock('@/api/wishlist', () => ({
  addWishlist: vi.fn(),
  removeWishlist: vi.fn(),
  changeWishlistAlert: vi.fn(),
}));

const PRODUCT_ID = 7;

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
