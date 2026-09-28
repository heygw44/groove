import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { useOrderFormSubmit } from '@/hooks/useOrderFormSubmit';
import { useAuthStore } from '@/store/authStore';
import type { OrderDraft } from '@/utils/orderDraft';

const createOrder = vi.fn();
const cancelOrder = vi.fn();
const purchaseLimitedDrop = vi.fn();
const openPaymentWindow = vi.fn();

vi.mock('@/api/order', () => ({
  createOrder: (...args: unknown[]) => createOrder(...args),
  cancelOrder: (...args: unknown[]) => cancelOrder(...args),
  getOrder: vi.fn(),
  getOrders: vi.fn(),
}));

vi.mock('@/api/limitedDrop', () => ({
  purchaseLimitedDrop: (...args: unknown[]) => purchaseLimitedDrop(...args),
}));

vi.mock('@/hooks/usePaymentWindow', () => ({
  usePaymentWindow: () => ({ openPaymentWindow, isOpening: false }),
}));

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>{children}</MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>
  );
};

interface Props {
  draft: OrderDraft | null;
  addressId: number | undefined;
  memberCouponId: number | null;
  orderName: string;
  onCouponRejected: () => void;
}

const renderSubmit = (initialProps: Props) =>
  renderHook((props: Props) => useOrderFormSubmit(props), {
    wrapper: createWrapper(),
    initialProps,
  });

beforeEach(() => {
  createOrder.mockReset();
  cancelOrder.mockReset();
  purchaseLimitedDrop.mockReset();
  openPaymentWindow.mockReset().mockResolvedValue(undefined);
  useAuthStore.setState({ accessToken: 'token', member: null, isBootstrapping: false });
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('useOrderFormSubmit()', () => {
  it('같은 초안으로 다시 제출하면 새 주문을 만들지 않고 결제창만 다시 연다', async () => {
    // given
    createOrder.mockResolvedValue({
      orderId: 1,
      orderNumber: 'ORD-1',
      totalAmount: 10000,
      discountAmount: 0,
      finalAmount: 10000,
    });
    const { result } = renderSubmit({
      draft: { kind: 'cart', cartItemIds: [1] },
      addressId: 5,
      memberCouponId: null,
      orderName: '앨범',
      onCouponRejected: vi.fn(),
    });

    // when
    act(() => result.current.submit('CARD'));
    await waitFor(() => expect(openPaymentWindow).toHaveBeenCalledTimes(1));
    act(() => result.current.submit('NAVERPAY'));
    await waitFor(() => expect(openPaymentWindow).toHaveBeenCalledTimes(2));

    // then
    expect(createOrder).toHaveBeenCalledTimes(1);
    expect(cancelOrder).not.toHaveBeenCalled();
    expect(openPaymentWindow).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ orderId: 1, method: 'NAVERPAY' }),
    );
  });

  it('주문이 생긴 뒤에는 초안이 바뀌어도 취소·재생성하지 않고 같은 주문을 재사용한다', async () => {
    // given
    createOrder.mockResolvedValueOnce({
      orderId: 1,
      orderNumber: 'ORD-1',
      totalAmount: 10000,
      discountAmount: 0,
      finalAmount: 10000,
    });

    const { result, rerender } = renderSubmit({
      draft: { kind: 'cart', cartItemIds: [1] },
      addressId: 5,
      memberCouponId: null,
      orderName: '앨범',
      onCouponRejected: vi.fn(),
    });

    // when
    act(() => result.current.submit('CARD'));
    await waitFor(() => expect(openPaymentWindow).toHaveBeenCalledTimes(1));

    rerender({
      draft: { kind: 'cart', cartItemIds: [1] },
      addressId: 6,
      memberCouponId: null,
      orderName: '앨범',
      onCouponRejected: vi.fn(),
    });
    act(() => result.current.submit('CARD'));
    await waitFor(() => expect(openPaymentWindow).toHaveBeenCalledTimes(2));

    // then
    expect(createOrder).toHaveBeenCalledTimes(1);
    expect(cancelOrder).not.toHaveBeenCalled();
    expect(openPaymentWindow).toHaveBeenNthCalledWith(2, expect.objectContaining({ orderId: 1 }));
    expect(result.current.pendingOrder).toEqual({
      orderId: 1,
      orderNumber: 'ORD-1',
      amount: 10000,
    });
  });

  it('한정반은 재클릭해도 취소하지 않고 만든 주문을 그대로 재사용한다', async () => {
    // given
    purchaseLimitedDrop.mockResolvedValue({
      orderId: 1,
      orderNumber: 'ORD-1',
      finalAmount: 9900,
      expiresAt: '2026-09-28T00:00:00',
    });
    const { result } = renderSubmit({
      draft: { kind: 'limited', dropId: 9 },
      addressId: 5,
      memberCouponId: null,
      orderName: '한정반 앨범',
      onCouponRejected: vi.fn(),
    });

    // when
    act(() => result.current.submit('CARD'));
    await waitFor(() => expect(openPaymentWindow).toHaveBeenCalledTimes(1));
    act(() => result.current.submit('VIRTUAL_ACCOUNT'));
    await waitFor(() => expect(openPaymentWindow).toHaveBeenCalledTimes(2));

    // then
    expect(purchaseLimitedDrop).toHaveBeenCalledTimes(1);
    expect(cancelOrder).not.toHaveBeenCalled();
    expect(openPaymentWindow).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ orderId: 1, method: 'VIRTUAL_ACCOUNT' }),
    );
  });
});
