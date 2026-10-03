import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { useConfirmPayment } from '@/hooks/mutations/usePaymentMutations';
import PaymentSuccessPage from '@/pages/payment/PaymentSuccessPage';
import type { PaymentConfirmRequest } from '@/types/payment';
import {
  loadOrderFormDraft,
  saveOrderFormDraft,
  type OrderFormDraftRecord,
} from '@/utils/orderDraft';

vi.mock('@/hooks/mutations/usePaymentMutations', () => ({
  useConfirmPayment: vi.fn(),
}));

const buildAxiosError = (code: string, message: string) =>
  Object.assign(new Error(message), {
    isAxiosError: true,
    response: { data: { error: { code, message } } },
  });

const buildDraftRecord = (overrides: Partial<OrderFormDraftRecord> = {}): OrderFormDraftRecord => ({
  source: { kind: 'cart', items: [{ cartItemId: 1, quantity: 1 }] },
  addressId: 5,
  memberCouponId: null,
  method: 'CARD',
  pendingOrder: {
    orderId: 7,
    orderNumber: 'ORD-7',
    amount: 10000,
    fingerprint: 'fp',
    addressId: 5,
    expiresAtMs: null,
    coupon: null,
  },
  ...overrides,
});

type ConfirmPaymentMutation = ReturnType<typeof useConfirmPayment>;

const mockConfirmPaymentError = (code: string, message: string) => {
  // 컴포넌트는 mutate(payload, { onSuccess, onError }) 형태로만 호출하므로 그 부분만 흉내낸다.
  const mutate = ((
    _payload: PaymentConfirmRequest,
    options?: { onError?: (error: unknown) => void },
  ) => {
    options?.onError?.(buildAxiosError(code, message));
  }) as ConfirmPaymentMutation['mutate'];
  vi.mocked(useConfirmPayment).mockReturnValue({ mutate } as ConfirmPaymentMutation);
};

const mockConfirmPaymentSuccess = (orderId: number) => {
  const mutate = ((
    _payload: PaymentConfirmRequest,
    options?: { onSuccess?: (data: { orderId: number }) => void },
  ) => {
    options?.onSuccess?.({ orderId });
  }) as ConfirmPaymentMutation['mutate'];
  vi.mocked(useConfirmPayment).mockReturnValue({ mutate } as ConfirmPaymentMutation);
};

const renderPage = (search: string) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[`/payments/success${search}`]}>
          <Routes>
            <Route path="/payments/success" element={<PaymentSuccessPage />} />
            <Route path="/orders/:id/complete" element={<p>주문완료 페이지</p>} />
            <Route path="/orders/new" element={<p>주문서 페이지</p>} />
            <Route path="/orders" element={<p>주문 내역 페이지</p>} />
            <Route path="/cart" element={<p>장바구니 페이지</p>} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
};

beforeEach(() => {
  sessionStorage.clear();
});

afterEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
});

describe('PaymentSuccessPage', () => {
  const search = '?paymentKey=pk-1&orderId=order-1&amount=10000&orderRef=7';

  it('승인에 성공하면 주문완료 페이지로 이동한다', async () => {
    // given
    mockConfirmPaymentSuccess(7);

    // when
    renderPage(search);

    // then
    expect(await screen.findByText('주문완료 페이지')).toBeInTheDocument();
  });

  it('승인에 성공하면 남아있던 주문서 초안을 지운다', async () => {
    // given
    saveOrderFormDraft(buildDraftRecord());
    mockConfirmPaymentSuccess(7);

    // when
    renderPage(search);
    await screen.findByText('주문완료 페이지');

    // then
    expect(loadOrderFormDraft()).toBeNull();
  });

  it('결제 결과가 불명이면 확인 중 안내와 주문 내역 보기 버튼을 보여준다', async () => {
    // given
    mockConfirmPaymentError(
      'PAYMENT_RESULT_UNKNOWN',
      '결제 결과를 확인하고 있습니다. 잠시 후 주문 내역에서 확인해주세요.',
    );

    // when
    renderPage(search);

    // then
    expect(await screen.findByText('결제 결과를 확인하고 있습니다')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '주문 내역 보기' })).toBeInTheDocument();
    expect(screen.queryByText('결제 승인에 실패했습니다')).not.toBeInTheDocument();
  });

  it('결과 불명이면 초안이 있어도 지우지 않는다', async () => {
    // given
    const record = buildDraftRecord();
    saveOrderFormDraft(record);
    mockConfirmPaymentError('PAYMENT_RESULT_UNKNOWN', '결과 확인 중');

    // when
    renderPage(search);
    await screen.findByText('결제 결과를 확인하고 있습니다');

    // then
    expect(loadOrderFormDraft()).toEqual(record);
  });

  it('주문 내역 보기를 누르면 주문 목록으로 이동한다', async () => {
    // given
    const user = userEvent.setup();
    mockConfirmPaymentError('PAYMENT_RESULT_UNKNOWN', '결과 확인 중');
    renderPage(search);
    await screen.findByRole('button', { name: '주문 내역 보기' });

    // when
    await user.click(screen.getByRole('button', { name: '주문 내역 보기' }));

    // then
    expect(screen.getByText('주문 내역 페이지')).toBeInTheDocument();
  });

  it('승인이 명확히 실패하고 초안이 있으면 주문서로 돌아가기 버튼을 보여준다', async () => {
    // given
    const user = userEvent.setup();
    saveOrderFormDraft(
      buildDraftRecord({ source: { kind: 'direct', productId: 10, quantity: 2 } }),
    );
    mockConfirmPaymentError('PAYMENT_CONFIRM_FAILED', '결제 승인에 실패했습니다.');
    renderPage(search);
    await screen.findByText('결제 승인에 실패했습니다');

    // when
    await user.click(screen.getByRole('button', { name: '주문서로 돌아가기' }));

    // then
    expect(screen.getByText('주문서 페이지')).toBeInTheDocument();
  });

  it('승인이 명확히 실패해도 초안은 지우지 않는다', async () => {
    // given
    const record = buildDraftRecord();
    saveOrderFormDraft(record);
    mockConfirmPaymentError('PAYMENT_CONFIRM_FAILED', '결제 승인에 실패했습니다.');

    // when
    renderPage(search);
    await screen.findByText('결제 승인에 실패했습니다');

    // then
    expect(loadOrderFormDraft()).toEqual(record);
  });

  it('승인이 명확히 실패했는데 초안이 없으면 장바구니 버튼을 보여준다', async () => {
    // given
    mockConfirmPaymentError('PAYMENT_CONFIRM_FAILED', '결제 승인에 실패했습니다.');

    // when
    renderPage(search);

    // then
    expect(await screen.findByText('결제 승인에 실패했습니다')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '장바구니' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '주문서로 돌아가기' })).not.toBeInTheDocument();
  });

  it('ORDER_EXPIRED 면 승인 후 자동 취소됐다는 안내를 보여준다', async () => {
    // given
    mockConfirmPaymentError('ORDER_EXPIRED', '결제 기한이 지난 주문입니다.');

    // when
    renderPage(search);

    // then
    expect(await screen.findByText('결제 승인에 실패했습니다')).toBeInTheDocument();
    expect(screen.getByText('주문 시간이 지나 결제가 자동 취소되었습니다.')).toBeInTheDocument();
  });
});
