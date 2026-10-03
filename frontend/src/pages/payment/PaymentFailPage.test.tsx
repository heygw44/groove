import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useEffect } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import PaymentFailPage from '@/pages/payment/PaymentFailPage';
import { saveOrderFormDraft, type OrderFormDraftRecord } from '@/utils/orderDraft';

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

function OrderFormLandingProbe({ onLand }: { onLand: (state: unknown) => void }) {
  const location = useLocation();
  useEffect(() => {
    onLand(location.state);
  }, [location.state, onLand]);
  return <p>주문서 페이지</p>;
}

const renderPage = (search: string) => {
  const onLand = vi.fn();
  render(
    <MemoryRouter initialEntries={[`/payments/fail${search}`]}>
      <Routes>
        <Route path="/payments/fail" element={<PaymentFailPage />} />
        <Route path="/orders/new" element={<OrderFormLandingProbe onLand={onLand} />} />
        <Route path="/cart" element={<p>장바구니 페이지</p>} />
        <Route path="/" element={<p>홈 페이지</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return { onLand };
};

beforeEach(() => {
  sessionStorage.clear();
});

afterEach(() => {
  sessionStorage.clear();
});

describe('PaymentFailPage', () => {
  it('화면을 벗어나면 탭 제목을 이전 제목으로 되돌린다', async () => {
    // given
    document.title = 'GROOVE';
    saveOrderFormDraft(buildDraftRecord());
    renderPage('?code=PAY_PROCESS_CANCELED');
    expect(document.title).toBe('결제 실패 | GROOVE');

    // when
    await userEvent.click(screen.getByRole('button', { name: '주문서로 돌아가기' }));

    // then
    expect(screen.getByText('주문서 페이지')).toBeInTheDocument();
    expect(document.title).toBe('GROOVE');
  });

  it('주문번호 없이 실패 사유만 보여준다', () => {
    // given & when
    renderPage('?code=REJECT_CARD_COMPANY&orderId=ORD-7&orderRef=7');

    // then
    expect(screen.getByRole('heading', { name: '결제에 실패했습니다' })).toBeInTheDocument();
    expect(screen.queryByText(/ORD-7/)).not.toBeInTheDocument();
    expect(screen.queryByText(/주문번호/)).not.toBeInTheDocument();
  });

  it('초안이 남아있으면 주문서로 돌아가기와 장바구니 버튼을 보여준다', () => {
    // given
    saveOrderFormDraft(buildDraftRecord());

    // when
    renderPage('?code=REJECT_CARD_COMPANY');

    // then
    expect(screen.getByRole('button', { name: '주문서로 돌아가기' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '장바구니' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다시 결제하기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '홈으로' })).not.toBeInTheDocument();
  });

  it('주문서로 돌아가기를 누르면 초안의 location.state 로 주문서를 다시 연다', async () => {
    // given
    const user = userEvent.setup();
    saveOrderFormDraft(
      buildDraftRecord({ source: { kind: 'direct', productId: 10, quantity: 2 } }),
    );
    const { onLand } = renderPage('?code=REJECT_CARD_COMPANY');

    // when
    await user.click(screen.getByRole('button', { name: '주문서로 돌아가기' }));

    // then
    expect(screen.getByText('주문서 페이지')).toBeInTheDocument();
    expect(onLand).toHaveBeenCalledWith({ productId: 10, quantity: 2 });
  });

  it('초안이 없으면 장바구니와 홈 버튼만 보여준다', () => {
    // given & when
    renderPage('?code=REJECT_CARD_COMPANY');

    // then
    expect(screen.queryByRole('button', { name: '주문서로 돌아가기' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '장바구니' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '홈으로' })).toBeInTheDocument();
  });
});
