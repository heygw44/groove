import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { useConfirmPayment } from '@/hooks/mutations/usePaymentMutations';
import PaymentSuccessPage from '@/pages/payment/PaymentSuccessPage';
import type { PaymentConfirmRequest } from '@/types/payment';

vi.mock('@/hooks/mutations/usePaymentMutations', () => ({
  useConfirmPayment: vi.fn(),
}));

const buildAxiosError = (code: string, message: string) =>
  Object.assign(new Error(message), {
    isAxiosError: true,
    response: { data: { error: { code, message } } },
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

const renderPage = (search: string) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[`/payments/success${search}`]}>
          <Routes>
            <Route path="/payments/success" element={<PaymentSuccessPage />} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
};

afterEach(() => {
  vi.clearAllMocks();
});

describe('PaymentSuccessPage', () => {
  const search = '?paymentKey=pk-1&orderId=order-1&amount=10000&orderRef=7';

  it('결제 결과가 불명이면 확인 중 안내와 주문 상세 보기 버튼을 보여준다', async () => {
    // given
    mockConfirmPaymentError(
      'PAYMENT_RESULT_UNKNOWN',
      '결제 결과를 확인하고 있습니다. 잠시 후 주문 내역에서 확인해 주세요.',
    );

    // when
    renderPage(search);

    // then
    expect(await screen.findByText('결제 결과를 확인하고 있습니다')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '주문 상세 보기' })).toBeInTheDocument();
    expect(screen.queryByText('결제 승인에 실패했습니다')).not.toBeInTheDocument();
  });

  it('다른 에러 코드면 결제 승인 실패 안내를 보여준다', async () => {
    // given
    mockConfirmPaymentError('PAYMENT_CONFIRM_FAILED', '결제 승인에 실패했습니다.');

    // when
    renderPage(search);

    // then
    expect(await screen.findByText('결제 승인에 실패했습니다')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '주문으로 돌아가기' })).toBeInTheDocument();
  });

  it('ORDER_EXPIRED 면 승인 후 자동 취소됐다는 안내를 보여준다', async () => {
    // given
    mockConfirmPaymentError('ORDER_EXPIRED', '결제 기한이 지난 주문입니다.');

    // when
    renderPage(search);

    // then
    expect(await screen.findByText('결제 승인에 실패했습니다')).toBeInTheDocument();
    expect(screen.getByText('주문 시간이 지나 결제가 자동 취소됐습니다.')).toBeInTheDocument();
  });
});
