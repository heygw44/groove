import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AdminOrderDetailDrawer } from '@/components/admin/AdminOrderDetailDrawer';
import { ToastProvider } from '@/components/common/Toast';
import { adminOrderKeys } from '@/hooks/queries/queryKeys';
import type { AdminOrderDetail } from '@/types/order';

vi.mock('@/api/admin', () => ({
  changeAdminOrderStatus: vi.fn(),
}));

const buildDetail = (overrides: Partial<AdminOrderDetail> = {}): AdminOrderDetail => ({
  id: 1,
  orderNumber: 'ORD-1',
  memberId: 1,
  memberEmail: 'member@groove.com',
  status: 'PAID',
  totalAmount: 10000,
  discountAmount: 0,
  finalAmount: 10000,
  items: [],
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

afterEach(() => {
  vi.clearAllMocks();
});

describe('AdminOrderDetailDrawer', () => {
  it('paymentStatus 가 대사 대기 상태면 결제 상태 배지를 함께 보여준다', async () => {
    // given
    const detail = buildDetail({ paymentStatus: 'UNKNOWN' });

    // when
    renderDrawer(detail);

    // then
    expect(await screen.findByText('결과 확인 중')).toBeInTheDocument();
    expect(screen.getByText('대사 대기')).toBeInTheDocument();
  });

  it('paymentStatus 가 DONE 이면 결제 상태 배지를 그리지 않는다', async () => {
    // given: 주문 상태 라벨과 겹치지 않는 상태로 확인한다(둘 다 '결제완료').
    const detail = buildDetail({ status: 'PREPARING', paymentStatus: 'DONE' });

    // when
    renderDrawer(detail);

    // then
    expect(await screen.findByText(detail.memberEmail)).toBeInTheDocument();
    expect(screen.queryByText('결제완료')).not.toBeInTheDocument();
  });

  it('paymentStatus 가 없으면 결제 상태 배지를 그리지 않는다', async () => {
    // given
    const detail = buildDetail({ status: 'PREPARING' });

    // when
    renderDrawer(detail);

    // then
    expect(await screen.findByText(detail.memberEmail)).toBeInTheDocument();
    expect(screen.queryByText('승인대기')).not.toBeInTheDocument();
    expect(screen.queryByText('결제완료')).not.toBeInTheDocument();
  });
});
