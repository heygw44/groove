import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { AdminOrderDetailDrawer } from '@/components/admin/AdminOrderDetailDrawer';
import { ToastProvider } from '@/components/common/Toast';
import { adminOrderKeys } from '@/hooks/queries/queryKeys';
import type { AdminOrderDetail } from '@/types/adminOrder';

const buildDetail = (overrides: Partial<AdminOrderDetail> = {}): AdminOrderDetail => ({
  id: 1,
  orderNumber: 'ORD-1',
  memberId: 1,
  memberEmail: 'member@groove.com',
  status: 'PAID',
  totalAmount: 10000,
  discountAmount: 0,
  finalAmount: 10000,
  items: [
    {
      productId: 7,
      productName: '레코드 A',
      price: 10000,
      quantity: 1,
      lineAmount: 10000,
      thumbnailUrl: null,
      productOrderNumber: 'ORD-1-01',
      status: 'SHIPPING',
      claimStatus: 'RETURN_REQUEST',
    },
  ],
  shippingAddress: {
    recipientName: '김그루브',
    phone: '010-0000-0000',
    zipCode: '00000',
    address1: '서울시 어딘가',
  },
  createdAt: '2026-09-13T00:00:00',
  expiresAt: '2026-09-13T00:30:00',
  ...overrides,
});

const renderDrawer = (detail: AdminOrderDetail) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(adminOrderKeys.detail(detail.id), detail);

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AdminOrderDetailDrawer orderId={detail.id} onClose={() => {}} />
      </ToastProvider>
    </QueryClientProvider>,
  );
};

describe('AdminOrderDetailDrawer', () => {
  it('주문번호·회원·배송지와 상품주문 번호를 보여준다', async () => {
    // given
    const detail = buildDetail();

    // when
    renderDrawer(detail);

    // then
    expect(await screen.findByText(detail.memberEmail)).toBeInTheDocument();
    expect(screen.getByText('ORD-1')).toBeInTheDocument();
    expect(screen.getByText('ORD-1-01')).toBeInTheDocument();
    expect(screen.getByText('레코드 A')).toBeInTheDocument();
    expect(screen.getByText(/김그루브/)).toBeInTheDocument();
  });

  it('paymentStatus 가 UNKNOWN 이면 결과 확인 중 배지를 보여준다', async () => {
    // given
    const detail = buildDetail({ paymentStatus: 'UNKNOWN' });

    // when
    renderDrawer(detail);

    // then
    expect(await screen.findByText('결과 확인 중')).toBeInTheDocument();
  });

  it('paymentStatus 가 CANCEL_REQUESTED 면 취소 처리 중 배지를 보여준다', async () => {
    // given
    const detail = buildDetail({ paymentStatus: 'CANCEL_REQUESTED' });

    // when
    renderDrawer(detail);

    // then
    expect(await screen.findByText('취소 처리 중')).toBeInTheDocument();
  });

  it('paymentStatus 가 없으면 결제상태 배지를 그리지 않는다', async () => {
    // given
    const detail = buildDetail({ status: 'CANCELED' });

    // when
    renderDrawer(detail);

    // then
    expect(await screen.findByText(detail.memberEmail)).toBeInTheDocument();
    expect(screen.queryByText('취소 처리 중')).not.toBeInTheDocument();
  });

  it('시스템 취소 사유 코드는 문구로 바꿔 보여준다', async () => {
    // given
    const detail = buildDetail({
      status: 'CANCELED',
      canceledAt: '2026-09-13T01:00:00',
      cancelReason: 'EXPIRED',
    });

    // when
    renderDrawer(detail);

    // then
    expect(await screen.findByText(/입금기한 만료/)).toBeInTheDocument();
    expect(screen.queryByText(/EXPIRED/)).not.toBeInTheDocument();
  });
});
