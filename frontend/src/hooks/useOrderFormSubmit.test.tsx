import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { useOrderFormSubmit } from '@/hooks/useOrderFormSubmit';
import { useAuthStore } from '@/store/authStore';
import type { OrderDraft, PendingOrder } from '@/utils/orderDraft';
import { getServerNowMs } from '@/utils/serverTime';

const createOrder = vi.fn();
const cancelOrder = vi.fn();
const updateOrderShippingAddress = vi.fn();
const purchaseLimitedDrop = vi.fn();
const openPaymentWindow = vi.fn();

vi.mock('@/api/order', () => ({
  createOrder: (...args: unknown[]) => createOrder(...args),
  cancelOrder: (...args: unknown[]) => cancelOrder(...args),
  updateOrderShippingAddress: (...args: unknown[]) => updateOrderShippingAddress(...args),
  getOrder: vi.fn(),
  getOrders: vi.fn(),
}));

vi.mock('@/api/limitedDrop', () => ({
  purchaseLimitedDrop: (...args: unknown[]) => purchaseLimitedDrop(...args),
}));

vi.mock('@/hooks/usePaymentWindow', () => ({
  usePaymentWindow: () => ({ openPaymentWindow, isOpening: false }),
}));

vi.mock('@/utils/serverTime', async () => {
  const actual = await vi.importActual<typeof import('@/utils/serverTime')>('@/utils/serverTime');
  return { ...actual, getServerNowMs: vi.fn(actual.getServerNowMs) };
});

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
  initialPendingOrder?: PendingOrder | null;
}

const renderSubmit = (initialProps: Props) =>
  renderHook((props: Props) => useOrderFormSubmit(props), {
    wrapper: createWrapper(),
    initialProps,
  });

beforeEach(() => {
  createOrder.mockReset();
  cancelOrder.mockReset();
  updateOrderShippingAddress.mockReset();
  purchaseLimitedDrop.mockReset();
  openPaymentWindow.mockReset().mockResolvedValue(undefined);
  vi.mocked(getServerNowMs).mockReturnValue(new Date('2026-09-28T00:00:00+09:00').getTime());
  useAuthStore.setState({ accessToken: 'token', member: null, isBootstrapping: false });
  sessionStorage.clear();
});

afterEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
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
    expect(updateOrderShippingAddress).not.toHaveBeenCalled();
    expect(cancelOrder).not.toHaveBeenCalled();
    expect(openPaymentWindow).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ orderId: 1, method: 'NAVERPAY' }),
    );
  });

  it('주문이 생긴 뒤 배송지가 바뀌면 PATCH 로 고친 뒤 같은 주문으로 결제창을 연다', async () => {
    // given
    createOrder.mockResolvedValueOnce({
      orderId: 1,
      orderNumber: 'ORD-1',
      totalAmount: 10000,
      discountAmount: 0,
      finalAmount: 10000,
    });
    updateOrderShippingAddress.mockResolvedValue({});

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
    expect(updateOrderShippingAddress).toHaveBeenCalledWith(1, 6);
    expect(openPaymentWindow).toHaveBeenNthCalledWith(2, expect.objectContaining({ orderId: 1 }));
    expect(result.current.pendingOrder).toMatchObject({ orderId: 1, addressId: 6 });
  });

  it('쿠폰이 바뀌면(지문이 다르면) 새 주문을 만든다', async () => {
    // given
    createOrder
      .mockResolvedValueOnce({
        orderId: 1,
        orderNumber: 'ORD-1',
        totalAmount: 10000,
        discountAmount: 0,
        finalAmount: 10000,
      })
      .mockResolvedValueOnce({
        orderId: 2,
        orderNumber: 'ORD-2',
        totalAmount: 10000,
        discountAmount: 3000,
        finalAmount: 7000,
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

    // 쿠폰을 적용해 지문이 바뀐다
    rerender({
      draft: { kind: 'cart', cartItemIds: [1] },
      addressId: 5,
      memberCouponId: 9,
      orderName: '앨범',
      onCouponRejected: vi.fn(),
    });
    act(() => result.current.submit('CARD'));
    await waitFor(() => expect(openPaymentWindow).toHaveBeenCalledTimes(2));

    // then
    expect(createOrder).toHaveBeenCalledTimes(2);
    expect(cancelOrder).not.toHaveBeenCalled();
    expect(openPaymentWindow).toHaveBeenNthCalledWith(2, expect.objectContaining({ orderId: 2 }));
  });

  it('만료 시각이 지나면 지문이 같아도 새 주문을 만든다', async () => {
    // given
    createOrder
      .mockResolvedValueOnce({
        orderId: 1,
        orderNumber: 'ORD-1',
        totalAmount: 10000,
        discountAmount: 0,
        finalAmount: 10000,
        expiresAt: '2026-09-28T00:10:00',
      })
      .mockResolvedValueOnce({
        orderId: 2,
        orderNumber: 'ORD-2',
        totalAmount: 10000,
        discountAmount: 0,
        finalAmount: 10000,
        expiresAt: '2026-09-28T00:20:00',
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

    vi.mocked(getServerNowMs).mockReturnValue(new Date('2026-09-28T00:15:00+09:00').getTime());
    act(() => result.current.submit('CARD'));
    await waitFor(() => expect(openPaymentWindow).toHaveBeenCalledTimes(2));

    // then
    expect(createOrder).toHaveBeenCalledTimes(2);
    expect(openPaymentWindow).toHaveBeenNthCalledWith(2, expect.objectContaining({ orderId: 2 }));
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
    expect(updateOrderShippingAddress).not.toHaveBeenCalled();
    expect(openPaymentWindow).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ orderId: 1, method: 'VIRTUAL_ACCOUNT' }),
    );
  });

  it('한정반은 배송지가 바뀌어도 재구매하지 않고 PATCH 로만 고친다', async () => {
    // given
    purchaseLimitedDrop.mockResolvedValue({
      orderId: 1,
      orderNumber: 'ORD-1',
      finalAmount: 9900,
      expiresAt: '2026-09-28T00:00:00',
    });
    updateOrderShippingAddress.mockResolvedValue({});

    const { result, rerender } = renderSubmit({
      draft: { kind: 'limited', dropId: 9 },
      addressId: 5,
      memberCouponId: null,
      orderName: '한정반 앨범',
      onCouponRejected: vi.fn(),
    });

    // when
    act(() => result.current.submit('CARD'));
    await waitFor(() => expect(openPaymentWindow).toHaveBeenCalledTimes(1));

    rerender({
      draft: { kind: 'limited', dropId: 9 },
      addressId: 6,
      memberCouponId: null,
      orderName: '한정반 앨범',
      onCouponRejected: vi.fn(),
    });
    act(() => result.current.submit('CARD'));
    await waitFor(() => expect(openPaymentWindow).toHaveBeenCalledTimes(2));

    // then
    expect(purchaseLimitedDrop).toHaveBeenCalledTimes(1);
    expect(updateOrderShippingAddress).toHaveBeenCalledWith(1, 6);
  });

  it('sessionStorage 초안에서 복원한 pendingOrder 는 새로 만들지 않고 바로 재사용한다', async () => {
    // given
    const { result } = renderSubmit({
      draft: { kind: 'cart', cartItemIds: [1] },
      addressId: 5,
      memberCouponId: null,
      orderName: '앨범',
      onCouponRejected: vi.fn(),
      initialPendingOrder: {
        orderId: 1,
        orderNumber: 'ORD-1',
        amount: 10000,
        fingerprint: JSON.stringify({
          kind: 'cart',
          cartItemIds: [1],
          memberCouponId: null,
        }),
        addressId: 5,
        expiresAtMs: null,
      },
    });

    // when
    act(() => result.current.submit('CARD'));
    await waitFor(() => expect(openPaymentWindow).toHaveBeenCalledTimes(1));

    // then
    expect(createOrder).not.toHaveBeenCalled();
    expect(openPaymentWindow).toHaveBeenCalledWith(
      expect.objectContaining({ orderId: 1, method: 'CARD' }),
    );
  });
});
