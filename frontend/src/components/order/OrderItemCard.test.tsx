import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  cancelOrderItem,
  confirmOrderItem,
  returnOrderItem,
  withdrawOrderClaim,
} from '@/api/order';
import { ToastProvider } from '@/components/common/Toast';
import { OrderItemCard } from '@/components/order/OrderItemCard';
import type { OrderItem } from '@/types/order';
import type { OrderPayment } from '@/types/payment';

vi.mock('@/api/order', () => ({
  cancelOrderItem: vi.fn().mockResolvedValue({}),
  returnOrderItem: vi.fn().mockResolvedValue({}),
  withdrawOrderClaim: vi.fn().mockResolvedValue({}),
  confirmOrderItem: vi.fn().mockResolvedValue({}),
}));

afterEach(() => {
  vi.clearAllMocks();
});

const baseItem: OrderItem = {
  id: 11,
  productId: 1,
  productName: '레코드 판',
  price: 10000,
  quantity: 1,
  lineAmount: 10000,
  thumbnailUrl: null,
  productOrderNumber: 'ORD-1-01',
  status: 'PAID',
  paidAmount: 10000,
  availableActions: [],
  refundInProgress: false,
};

const renderCard = (item: OrderItem, payment?: OrderPayment) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>
          <OrderItemCard orderId={1} item={item} payment={payment} />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
};

describe('OrderItemCard', () => {
  it('상품주문번호와 상태 배지를 보여준다', () => {
    // given & when
    renderCard(baseItem);

    // then
    expect(screen.getByText('ORD-1-01')).toBeInTheDocument();
    expect(screen.getByText('결제완료')).toBeInTheDocument();
  });

  it('availableActions 에 없으면 배송조회·리뷰 쓰기 버튼을 보여주지 않는다', () => {
    // given & when
    renderCard(baseItem);

    // then
    expect(screen.queryByRole('link', { name: '배송조회' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '리뷰 쓰기' })).not.toBeInTheDocument();
  });

  it('TRACK 이 있고 송장 정보가 있으면 택배사 조회 링크를 새 탭으로 연다', () => {
    // given
    const item: OrderItem = {
      ...baseItem,
      status: 'SHIPPING',
      courierCode: 'CJ',
      trackingNumber: '123456789012',
      availableActions: ['TRACK'],
      refundInProgress: false,
    };

    // when
    renderCard(item);

    // then
    const link = screen.getByRole('link', { name: '배송조회' });
    expect(link).toHaveAttribute(
      'href',
      'https://trace.cjlogistics.com/next/tracking.html?wblNo=123456789012',
    );
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('WRITE_REVIEW 가 있으면 리뷰 쓰기 링크를 보여준다', () => {
    // given
    const item: OrderItem = {
      ...baseItem,
      status: 'DELIVERED',
      availableActions: ['WRITE_REVIEW'],
      refundInProgress: false,
    };

    // when
    renderCard(item);

    // then
    const link = screen.getByRole('link', { name: '리뷰 쓰기' });
    expect(link).toHaveAttribute('href', '/products/1#reviews');
  });

  it.each(['CANCELED', 'CANCELED_BY_NOPAYMENT', 'RETURNED'] as const)(
    '%s 상품은 환불 금액으로 보여준다',
    (status) => {
      // given & when
      renderCard({ ...baseItem, status, paidAmount: 9000 });

      // then
      expect(screen.getByText('환불 금액 9,000원')).toBeInTheDocument();
    },
  );

  it('진행 중 상품은 결제 금액으로 보여준다', () => {
    // given & when
    renderCard(baseItem);

    // then
    expect(screen.getByText('결제 금액 10,000원')).toBeInTheDocument();
  });

  it.each(['CANCELED', 'CANCELED_BY_NOPAYMENT', 'RETURNED'] as const)(
    '%s 상품이면 재구매 버튼을 보여준다',
    (status) => {
      // given & when
      renderCard({ ...baseItem, status });

      // then
      expect(screen.getByRole('button', { name: '장바구니 담기' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '바로 구매하기' })).toBeInTheDocument();
    },
  );

  it.each(['PAID', 'PREPARING', 'SHIPPING', 'DELIVERED', 'PURCHASE_CONFIRMED'] as const)(
    '%s 상품이면 재구매 버튼을 보여주지 않는다',
    (status) => {
      // given & when
      renderCard({ ...baseItem, status });

      // then
      expect(screen.queryByRole('button', { name: '장바구니 담기' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: '바로 구매하기' })).not.toBeInTheDocument();
    },
  );

  it('availableActions 에 없는 취소·반품·철회·구매확정 버튼은 그리지 않는다', () => {
    // given & when
    renderCard({ ...baseItem, status: 'PREPARING', availableActions: [] });

    // then
    ['주문취소', '취소요청', '반품요청', '요청 철회', '구매확정'].forEach((name) => {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    });
  });

  it('CANCEL 이면 주문취소를 확정할 때 사유와 함께 API 를 호출한다', async () => {
    // given
    const user = userEvent.setup();
    renderCard({ ...baseItem, availableActions: ['CANCEL'] });

    // when
    await user.click(screen.getByRole('button', { name: '주문취소' }));
    const dialog = screen.getByRole('dialog', { name: '주문을 취소하시겠습니까?' });
    await user.type(within(dialog).getByLabelText('취소 사유 (선택)'), '단순 변심');
    await user.click(within(dialog).getByRole('button', { name: '주문취소' }));

    // then
    await waitFor(() =>
      expect(cancelOrderItem).toHaveBeenCalledWith(1, 11, { reason: '단순 변심' }),
    );
  });

  it('가상계좌 결제 완료 상품을 취소하려면 환불계좌를 입력해야 한다', async () => {
    // given
    const user = userEvent.setup();
    const payment: OrderPayment = {
      paymentId: 1,
      method: '가상계좌',
      status: 'DONE',
      amount: 10000,
      approvedAt: '2026-09-13T00:01:00',
      easyPayProvider: null,
      virtualAccount: {
        bankCode: '020',
        accountNumber: '110123456789',
        customerName: '김그루브',
        dueDate: '2026-09-15T23:59:59',
      },
    };
    renderCard({ ...baseItem, availableActions: ['CANCEL'] }, payment);

    // when
    await user.click(screen.getByRole('button', { name: '주문취소' }));
    const dialog = screen.getByRole('dialog', { name: '주문을 취소하시겠습니까?' });
    await user.click(within(dialog).getByRole('button', { name: '주문취소' }));

    // then
    expect(within(dialog).getByText('환불계좌 정보를 모두 입력해주세요.')).toBeInTheDocument();
    expect(cancelOrderItem).not.toHaveBeenCalled();
  });

  it('CANCEL_REQUEST 이면 취소 요청 버튼으로 요청한다', async () => {
    // given
    const user = userEvent.setup();
    renderCard({ ...baseItem, status: 'PREPARING', availableActions: ['CANCEL_REQUEST'] });

    // when
    await user.click(screen.getByRole('button', { name: '취소요청' }));
    const dialog = screen.getByRole('dialog', { name: '취소를 요청하시겠습니까?' });
    await user.click(within(dialog).getByRole('button', { name: '취소요청' }));

    // then
    await waitFor(() => expect(cancelOrderItem).toHaveBeenCalledWith(1, 11, undefined));
  });

  it('RETURN_REQUEST 이면 반품 요청 API 를 호출한다', async () => {
    // given
    const user = userEvent.setup();
    renderCard({ ...baseItem, status: 'DELIVERED', availableActions: ['RETURN_REQUEST'] });

    // when
    await user.click(screen.getByRole('button', { name: '반품요청' }));
    const dialog = screen.getByRole('dialog', { name: '반품을 요청하시겠습니까?' });
    await user.type(within(dialog).getByLabelText('반품 사유 (선택)'), '파손');
    await user.click(within(dialog).getByRole('button', { name: '반품요청' }));

    // then
    await waitFor(() => expect(returnOrderItem).toHaveBeenCalledWith(1, 11, { reason: '파손' }));
  });

  it('WITHDRAW_CLAIM 이면 claimId 로 요청 철회 API 를 호출한다', async () => {
    // given
    const user = userEvent.setup();
    renderCard({
      ...baseItem,
      status: 'DELIVERED',
      claimStatus: 'RETURN_REQUEST',
      claimId: 77,
      availableActions: ['WITHDRAW_CLAIM'],
      refundInProgress: false,
    });

    // when
    await user.click(screen.getByRole('button', { name: '요청 철회' }));
    const dialog = screen.getByRole('dialog', { name: '요청을 철회하시겠습니까?' });
    await user.click(within(dialog).getByRole('button', { name: '요청 철회' }));

    // then
    await waitFor(() => expect(withdrawOrderClaim).toHaveBeenCalledWith(77));
  });

  it('claimId 가 없으면 WITHDRAW_CLAIM 이 있어도 철회 버튼을 그리지 않는다', () => {
    // given & when
    renderCard({ ...baseItem, availableActions: ['WITHDRAW_CLAIM'] });

    // then
    expect(screen.queryByRole('button', { name: '요청 철회' })).not.toBeInTheDocument();
  });

  it('CONFIRM 이면 구매확정 API 를 호출한다', async () => {
    // given
    const user = userEvent.setup();
    renderCard({ ...baseItem, status: 'DELIVERED', availableActions: ['CONFIRM'] });

    // when
    await user.click(screen.getByRole('button', { name: '구매확정' }));
    const dialog = screen.getByRole('dialog', { name: '구매를 확정하시겠습니까?' });
    await user.click(within(dialog).getByRole('button', { name: '구매확정' }));

    // then
    await waitFor(() => expect(confirmOrderItem).toHaveBeenCalledWith(1, 11));
  });
});
