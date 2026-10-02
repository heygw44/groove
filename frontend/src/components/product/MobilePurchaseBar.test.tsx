import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createRef } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { MobilePurchaseBar } from '@/components/product/MobilePurchaseBar';
import type { ProductDetail } from '@/types/product';

const buildProduct = (overrides: Partial<ProductDetail> = {}): ProductDetail =>
  ({
    id: 1,
    title: 'Kind of Blue',
    price: 38000,
    status: 'SELLING',
    stockQuantity: 10,
    ...overrides,
  }) as ProductDetail;

let observerCallback: IntersectionObserverCallback = () => undefined;

class FakeIntersectionObserver {
  constructor(callback: IntersectionObserverCallback) {
    observerCallback = callback;
  }
  observe = vi.fn();
  disconnect = vi.fn();
}

const renderBar = (product: ProductDetail) => {
  const panel = document.createElement('div');
  panel.tabIndex = -1;
  panel.scrollIntoView = vi.fn();
  document.body.appendChild(panel);
  const panelRef = createRef<HTMLElement>();
  (panelRef as { current: HTMLElement | null }).current = panel;
  render(<MobilePurchaseBar product={product} panelRef={panelRef} />);
  return panel;
};

beforeEach(() => {
  vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver);
});

afterEach(() => {
  vi.unstubAllGlobals();
  document.body.innerHTML = '';
});

describe('MobilePurchaseBar', () => {
  it('판매 중이면 가격과 구매하기 버튼을 보여준다', () => {
    // given & when
    renderBar(buildProduct());

    // then
    expect(screen.getByText('38,000원')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '구매하기' })).toBeInTheDocument();
  });

  it('품절이면 재입고 알림 받기 버튼을 보여준다', () => {
    // given & when
    renderBar(buildProduct({ status: 'SOLD_OUT', stockQuantity: 0 }));

    // then
    expect(screen.getByRole('button', { name: '재입고 알림 받기' })).toBeInTheDocument();
  });

  it('품절이어도 한정반 상품이면 구매하기 버튼을 보여준다', () => {
    // given & when
    renderBar(
      buildProduct({
        status: 'SOLD_OUT',
        stockQuantity: 0,
        limitedDrop: { id: 3 } as ProductDetail['limitedDrop'],
      }),
    );

    // then
    expect(screen.getByRole('button', { name: '구매하기' })).toBeInTheDocument();
  });

  it('버튼을 누르면 구매 패널로 스크롤하고 포커스한다', async () => {
    // given
    const user = userEvent.setup();
    const panel = renderBar(buildProduct());

    // when
    await user.click(screen.getByRole('button', { name: '구매하기' }));

    // then
    expect(panel.scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'center' });
    expect(panel).toHaveFocus();
  });

  it('구매 패널이 화면에 보이면 바를 숨긴다', () => {
    // given
    renderBar(buildProduct());

    // when
    act(() => {
      observerCallback([{ isIntersecting: true } as IntersectionObserverEntry], {} as never);
    });

    // then
    expect(screen.queryByRole('button', { name: '구매하기' })).not.toBeInTheDocument();
  });

  it('IntersectionObserver 가 없으면 바를 보여준다', () => {
    // given
    vi.stubGlobal('IntersectionObserver', undefined);

    // when
    renderBar(buildProduct());

    // then
    expect(screen.getByRole('button', { name: '구매하기' })).toBeInTheDocument();
  });
});
