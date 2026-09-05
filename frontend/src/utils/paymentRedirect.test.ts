import { describe, expect, it } from 'vitest';

import {
  buildOrderName,
  buildPaymentRedirectUrls,
  getTossFailMessage,
  parsePaymentFailParams,
  parsePaymentSuccessParams,
} from '@/utils/paymentRedirect';

describe('parsePaymentSuccessParams()', () => {
  it('paymentKey/orderId/amount 가 모두 있으면 파싱한다', () => {
    // given
    const searchParams = new URLSearchParams({
      paymentKey: 'pk_123',
      orderId: '20260905-ABC123',
      amount: '42000',
    });

    // when
    const result = parsePaymentSuccessParams(searchParams);

    // then
    expect(result).toEqual({
      paymentKey: 'pk_123',
      orderId: '20260905-ABC123',
      amount: 42000,
      orderRef: undefined,
    });
  });

  it('orderRef 가 있으면 함께 담는다', () => {
    // given
    const searchParams = new URLSearchParams({
      paymentKey: 'pk_123',
      orderId: '20260905-ABC123',
      amount: '42000',
      orderRef: '7',
    });

    // when
    const result = parsePaymentSuccessParams(searchParams);

    // then
    expect(result?.orderRef).toBe(7);
  });

  it.each([
    ['paymentKey', { orderId: '20260905-ABC123', amount: '42000' }],
    ['orderId', { paymentKey: 'pk_123', amount: '42000' }],
    ['amount', { paymentKey: 'pk_123', orderId: '20260905-ABC123' }],
  ])('%s 가 없으면 null 을 반환한다', (_field, params) => {
    // given
    const searchParams = new URLSearchParams(params);

    // when
    const result = parsePaymentSuccessParams(searchParams);

    // then
    expect(result).toBeNull();
  });

  it.each(['0', '-1', 'abc', '42000.5'])('amount 가 %s 처럼 양의 정수가 아니면 null 을 반환한다', (amount) => {
    // given
    const searchParams = new URLSearchParams({
      paymentKey: 'pk_123',
      orderId: '20260905-ABC123',
      amount,
    });

    // when
    const result = parsePaymentSuccessParams(searchParams);

    // then
    expect(result).toBeNull();
  });
});

describe('parsePaymentFailParams()', () => {
  it('code/message/orderId/orderRef 가 있으면 그대로 담는다', () => {
    // given
    const searchParams = new URLSearchParams({
      code: 'PAY_PROCESS_CANCELED',
      message: '사용자가 결제를 취소했습니다',
      orderId: '20260905-ABC123',
      orderRef: '7',
    });

    // when
    const result = parsePaymentFailParams(searchParams);

    // then
    expect(result).toEqual({
      code: 'PAY_PROCESS_CANCELED',
      message: '사용자가 결제를 취소했습니다',
      orderId: '20260905-ABC123',
      orderRef: 7,
    });
  });

  it('값이 없으면 각 필드가 undefined 인 객체를 반환한다', () => {
    // given
    const searchParams = new URLSearchParams();

    // when
    const result = parsePaymentFailParams(searchParams);

    // then
    expect(result).toEqual({
      code: undefined,
      message: undefined,
      orderId: undefined,
      orderRef: undefined,
    });
  });
});

describe('buildPaymentRedirectUrls()', () => {
  it('orderId 와 origin 으로 성공/실패 URL 을 만든다', () => {
    // given & when
    const urls = buildPaymentRedirectUrls(7, 'https://groove-lp.duckdns.org');

    // then
    expect(urls).toEqual({
      successUrl: 'https://groove-lp.duckdns.org/payments/success?orderRef=7',
      failUrl: 'https://groove-lp.duckdns.org/payments/fail?orderRef=7',
    });
  });
});

describe('buildOrderName()', () => {
  it('상품이 1개면 상품명 그대로 반환한다', () => {
    // given & when & then
    expect(buildOrderName([{ productName: 'A Love Supreme' }])).toBe('A Love Supreme');
  });

  it('상품이 여러 개면 "외 N건" 을 붙인다', () => {
    // given & when & then
    expect(
      buildOrderName([
        { productName: 'A Love Supreme' },
        { productName: 'Kind of Blue' },
        { productName: 'Blue Train' },
      ]),
    ).toBe('A Love Supreme 외 2건');
  });

  it('상품이 없으면 빈 문자열을 반환한다', () => {
    // given & when & then
    expect(buildOrderName([])).toBe('');
  });
});

describe('getTossFailMessage()', () => {
  it('매핑된 코드면 정의된 문구를 반환한다', () => {
    // given & when & then
    expect(getTossFailMessage('REJECT_CARD_COMPANY')).toBe('카드사에서 결제를 거절했습니다.');
  });

  it('매핑에 없고 fallback 이 있으면 fallback 을 반환한다', () => {
    // given & when & then
    expect(getTossFailMessage('UNKNOWN_CODE', '토스 메시지')).toBe('토스 메시지');
  });

  it('매핑도 fallback 도 없으면 기본 문구를 반환한다', () => {
    // given & when & then
    expect(getTossFailMessage(undefined)).toBe('결제에 실패했습니다.');
  });
});
