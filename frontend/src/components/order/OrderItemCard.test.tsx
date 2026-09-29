import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { OrderItemCard } from '@/components/order/OrderItemCard';
import type { OrderItem } from '@/types/order';

const baseItem: OrderItem = {
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
};

const renderCard = (item: OrderItem) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>
          <OrderItemCard item={item} />
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
    };

    // when
    renderCard(item);

    // then
    const link = screen.getByRole('link', { name: '리뷰 쓰기' });
    expect(link).toHaveAttribute('href', '/products/1#reviews');
  });

  it('CANCEL·CONFIRM 등 아직 지원하지 않는 액션은 availableActions 에 있어도 버튼을 그리지 않는다', () => {
    // given
    const item: OrderItem = {
      ...baseItem,
      status: 'DELIVERED',
      availableActions: ['CANCEL', 'CANCEL_REQUEST', 'RETURN_REQUEST', 'WITHDRAW_CLAIM', 'CONFIRM'],
    };

    // when
    renderCard(item);

    // then
    expect(screen.queryByRole('button', { name: '구매확정' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '취소요청' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '반품요청' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '요청철회' })).not.toBeInTheDocument();
  });
});
