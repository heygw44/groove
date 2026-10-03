import { render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { useAddCartItem } from '@/hooks/mutations/useCartMutations';
import { useToggleWishlist } from '@/hooks/mutations/useWishlistMutations';
import { useWishlist } from '@/hooks/queries/useWishlist';
import WishlistPage from '@/pages/mypage/WishlistPage';

vi.mock('@/hooks/queries/useWishlist', () => ({
  useWishlist: vi.fn(),
}));

vi.mock('@/hooks/mutations/useWishlistMutations', () => ({
  useToggleWishlist: vi.fn(),
}));

vi.mock('@/hooks/mutations/useCartMutations', () => ({
  useAddCartItem: vi.fn(),
}));

const mockEmptyPage = (page: number, totalPages: number) => {
  vi.mocked(useWishlist).mockReturnValue({
    data: { content: [], page, size: 12, totalElements: totalPages * 12, totalPages },
    isPending: false,
    isError: false,
    error: null,
    isPlaceholderData: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useWishlist>);
};

const mutationStub = <T,>() => ({ mutate: vi.fn(), isPending: false }) as unknown as T;

const LocationProbe = () => {
  const location = useLocation();
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>;
};

const renderPage = (url = '/mypage/wishlist') =>
  render(
    <MemoryRouter initialEntries={[url]}>
      <ToastProvider>
        <WishlistPage />
        <LocationProbe />
      </ToastProvider>
    </MemoryRouter>,
  );

describe('WishlistPage', () => {
  beforeEach(() => {
    vi.mocked(useToggleWishlist).mockReturnValue(
      mutationStub<ReturnType<typeof useToggleWishlist>>(),
    );
    vi.mocked(useAddCartItem).mockReturnValue(mutationStub<ReturnType<typeof useAddCartItem>>());
  });

  afterEach(() => vi.clearAllMocks());

  it('마지막 페이지가 비면 찜이 남은 마지막 페이지로 이동하고 빈 상태를 보여주지 않는다', () => {
    // given
    mockEmptyPage(2, 2);

    // when
    renderPage('/mypage/wishlist?page=2');

    // then
    expect(screen.getByTestId('location')).toHaveTextContent('/mypage/wishlist?page=1');
    expect(screen.queryByText('찜한 상품이 없습니다')).not.toBeInTheDocument();
  });

  it('남은 찜이 첫 페이지뿐이면 page 파라미터를 제거한다', () => {
    // given
    mockEmptyPage(1, 1);

    // when
    renderPage('/mypage/wishlist?page=1');

    // then
    expect(screen.getByTestId('location')).toHaveTextContent('/mypage/wishlist');
    expect(screen.getByTestId('location')).not.toHaveTextContent('page=');
  });

  it('첫 페이지가 비어 있으면 빈 상태를 보여주고 URL 은 그대로 둔다', () => {
    // given
    mockEmptyPage(0, 0);

    // when
    renderPage('/mypage/wishlist');

    // then
    expect(screen.getByText('찜한 상품이 없습니다')).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/mypage\/wishlist$/);
  });
});
