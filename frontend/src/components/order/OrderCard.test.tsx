import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { OrderCard } from '@/components/order/OrderCard';
import type { OrderSummary } from '@/types/order';

const baseOrder: OrderSummary = {
  id: 9002,
  orderNumber: '20260902-K7Q2M9XZ',
  status: 'PENDING',
  finalAmount: 75600,
  discountAmount: 6000,
  couponName: '가을맞이 5천원 할인',
  representativeProductName: 'Kind of Blue',
  itemCount: 2,
  thumbnailUrl: 'https://cdn.groove.com/kind-of-blue-0.jpg',
  items: [
    {
      productId: 501,
      productName: 'Kind of Blue',
      quantity: 1,
      lineAmount: 69600,
      thumbnailUrl: 'https://cdn.groove.com/kind-of-blue-0.jpg',
      productOrderNumber: '20260902-K7Q2M9XZ-01',
      status: 'PAID',
      paidAmount: 69600,
      availableActions: ['CANCEL', 'TRACK'],
      refundInProgress: false,
    },
    {
      productId: 620,
      productName: 'Head Hunters',
      quantity: 1,
      lineAmount: 6000,
      thumbnailUrl: null,
      productOrderNumber: '20260902-K7Q2M9XZ-02',
      status: 'SHIPPING',
      claimStatus: 'CANCEL_REQUEST',
      paidAmount: 6000,
      courierCode: 'CJ',
      trackingNumber: '123456789012',
      availableActions: ['WITHDRAW_CLAIM', 'TRACK'],
      refundInProgress: false,
    },
  ],
  createdAt: '2026-09-02T10:00:00',
};

const renderCard = (order: OrderSummary) =>
  render(
    <MemoryRouter>
      <ul>
        <OrderCard order={order} />
      </ul>
    </MemoryRouter>,
  );

describe('OrderCard', () => {
  it('주문에 담긴 상품 행을 모두 보여준다', () => {
    // given & when
    renderCard(baseOrder);

    // then
    expect(screen.getByText('Kind of Blue')).toBeInTheDocument();
    expect(screen.getByText('Head Hunters')).toBeInTheDocument();
    expect(screen.getAllByText('수량 1개')).toHaveLength(2);
  });

  it('상품 이름은 상품 상세로 링크된다', () => {
    // given & when
    renderCard(baseOrder);

    // then
    expect(screen.getByRole('link', { name: 'Kind of Blue' }).getAttribute('href')).toBe(
      '/products/501',
    );
    expect(screen.getByRole('link', { name: 'Head Hunters' }).getAttribute('href')).toBe(
      '/products/620',
    );
  });

  it('주문 상세 링크는 주문 id 로 이동한다', () => {
    // given & when
    renderCard(baseOrder);

    // then
    expect(screen.getByRole('link', { name: '주문 상세 >' }).getAttribute('href')).toBe(
      '/orders/9002',
    );
  });

  it('상품 링크가 다른 링크 안에 중첩되지 않는다', () => {
    // given
    const { container } = renderCard(baseOrder);

    // when & then
    expect(container.querySelectorAll('a a').length).toBe(0);
  });

  it('할인 금액이 있으면 쿠폰 할인 문구를 보여준다', () => {
    // given & when
    renderCard(baseOrder);

    // then
    expect(screen.getByText('쿠폰 -6,000원')).toBeInTheDocument();
    expect(screen.getByText('75,600원')).toBeInTheDocument();
  });

  it('items 가 비어 있으면 대표 상품과 나머지 건수로 대신 보여준다', () => {
    // given
    const orderWithoutItems: OrderSummary = { ...baseOrder, items: [] };

    // when
    renderCard(orderWithoutItems);

    // then
    expect(screen.getByText('Kind of Blue 외 1건')).toBeInTheDocument();
  });
});
