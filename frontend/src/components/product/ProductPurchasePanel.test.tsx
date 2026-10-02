import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, type AxiosResponse } from 'axios';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastContext } from '@/components/common/toastContext';
import { ProductPurchasePanel } from '@/components/product/ProductPurchasePanel';
import { useAuthStore } from '@/store/authStore';
import type { ProductDetail } from '@/types/product';

const toggleMutate = vi.fn();
const alertMutate = vi.fn();
const navigateMock = vi.fn();
const showToast = vi.fn();

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => navigateMock,
}));

vi.mock('@/hooks/mutations/useWishlistMutations', () => ({
  useToggleWishlist: () => ({ mutate: toggleMutate, isPending: false }),
  useChangeWishlistAlert: () => ({ mutate: alertMutate, isPending: false }),
}));

vi.mock('@/hooks/mutations/useCartMutations', () => ({
  useAddCartItem: () => ({ mutate: vi.fn(), isPending: false }),
}));

const buildProduct = (overrides: Partial<ProductDetail> = {}): ProductDetail =>
  ({
    id: 7,
    title: 'Kind of Blue',
    price: 38000,
    status: 'SOLD_OUT',
    stockQuantity: 0,
    ...overrides,
  }) as ProductDetail;

const renderPanel = (product: ProductDetail) =>
  render(
    <ToastContext.Provider value={{ showToast }}>
      <MemoryRouter initialEntries={['/products/7']}>
        <ProductPurchasePanel product={product} />
      </MemoryRouter>
    </ToastContext.Provider>,
  );

beforeEach(() => {
  vi.clearAllMocks();
  useAuthStore.setState({ accessToken: 't' });
});

describe('ProductPurchasePanel 재입고 알림', () => {
  it('품절이고 위시에 없으면 위시에 담아 알림을 신청한다', async () => {
    // given
    const user = userEvent.setup();
    renderPanel(buildProduct({ wishlisted: false }));

    // when
    await user.click(screen.getByRole('button', { name: '재입고 알림 받기' }));

    // then
    expect(toggleMutate).toHaveBeenCalledWith(
      { productId: 7, wishlisted: false },
      expect.any(Object),
    );
    expect(screen.queryByRole('button', { name: '바로 구매' })).not.toBeInTheDocument();
  });

  it('위시에 있고 알림이 꺼져 있으면 알림만 켠다', async () => {
    // given
    const user = userEvent.setup();
    renderPanel(buildProduct({ wishlisted: true, alertEnabled: false }));

    // when
    await user.click(screen.getByRole('button', { name: '재입고 알림 받기' }));

    // then
    expect(alertMutate).toHaveBeenCalledWith(
      { productId: 7, alertEnabled: true },
      expect.any(Object),
    );
  });

  it('이미 신청했다면 비활성 버튼으로 보여준다', () => {
    // given & when
    renderPanel(buildProduct({ wishlisted: true, alertEnabled: true }));

    // then
    expect(screen.getByRole('button', { name: '재입고 알림 신청됨' })).toBeDisabled();
  });

  it('신청에 성공하면 안내 토스트를 띄운다', async () => {
    // given
    const user = userEvent.setup();
    toggleMutate.mockImplementation((_vars, options) => options.onSuccess());
    renderPanel(buildProduct({ wishlisted: false }));

    // when
    await user.click(screen.getByRole('button', { name: '재입고 알림 받기' }));

    // then
    expect(showToast).toHaveBeenCalledWith('success', '재입고 알림을 신청했습니다.');
  });

  it('이미 위시에 담긴 행이면 오류 없이 알림만 켠다', async () => {
    // given
    const user = userEvent.setup();
    const conflict = new AxiosError('conflict', 'ERR_BAD_REQUEST', undefined, undefined, {
      status: 409,
      data: { error: { code: 'WISHLIST_ALREADY_EXISTS', message: '이미 담겼습니다.' } },
    } as AxiosResponse);
    toggleMutate.mockImplementation((_vars, options) => options.onError(conflict));
    alertMutate.mockImplementation((_vars, options) => options.onSuccess());
    renderPanel(buildProduct({ wishlisted: false }));

    // when
    await user.click(screen.getByRole('button', { name: '재입고 알림 받기' }));

    // then
    expect(alertMutate).toHaveBeenCalledWith(
      { productId: 7, alertEnabled: true },
      expect.any(Object),
    );
    expect(showToast).toHaveBeenCalledTimes(1);
    expect(showToast).toHaveBeenCalledWith('success', '재입고 알림을 신청했습니다.');
  });

  it('그 밖의 위시 추가 실패는 오류 토스트를 띄운다', async () => {
    // given
    const user = userEvent.setup();
    toggleMutate.mockImplementation((_vars, options) => options.onError(new Error('boom')));
    renderPanel(buildProduct({ wishlisted: false }));

    // when
    await user.click(screen.getByRole('button', { name: '재입고 알림 받기' }));

    // then
    expect(alertMutate).not.toHaveBeenCalled();
    expect(showToast).toHaveBeenCalledWith('error', expect.any(String));
  });

  it('로그아웃 상태면 로그인 화면으로 보낸다', async () => {
    // given
    const user = userEvent.setup();
    useAuthStore.setState({ accessToken: null });
    renderPanel(buildProduct({ wishlisted: false }));

    // when
    await user.click(screen.getByRole('button', { name: '재입고 알림 받기' }));

    // then
    expect(navigateMock).toHaveBeenCalledWith('/login?redirect=%2Fproducts%2F7');
    expect(toggleMutate).not.toHaveBeenCalled();
  });

  it('재고가 있으면 바로 구매 버튼을 보여준다', () => {
    // given & when
    renderPanel(buildProduct({ status: 'ON_SALE', stockQuantity: 10 }));

    // then
    expect(screen.getByRole('button', { name: '바로 구매' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: '재입고 알림 받기' })).not.toBeInTheDocument();
  });
});
