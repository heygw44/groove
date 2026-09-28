import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { usePaymentWindow } from '@/hooks/usePaymentWindow';

const requestPayment = vi.fn();
const payment = vi.fn(() => ({ requestPayment }));

vi.mock('@tosspayments/tosspayments-sdk', () => ({
  ANONYMOUS: '@@ANONYMOUS',
  loadTossPayments: vi.fn(() => Promise.resolve({ payment })),
}));

const wrapper = ({ children }: { children: ReactNode }) => (
  <ToastProvider>{children}</ToastProvider>
);

const baseParams = {
  orderId: 1,
  orderNumber: 'ORD-1',
  orderName: '오디오 앨범',
  amount: 10000,
} as const;

beforeEach(() => {
  vi.stubEnv('VITE_TOSS_CLIENT_KEY', 'test_ck_dummy');
  requestPayment.mockReset().mockResolvedValue(undefined);
  payment.mockClear();
});

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('usePaymentWindow()', () => {
  it('CARD 는 method:CARD, card.flowMode:DEFAULT 로 요청한다', async () => {
    // given
    const { result } = renderHook(() => usePaymentWindow(), { wrapper });

    // when
    await act(async () => {
      await result.current.openPaymentWindow({ ...baseParams, method: 'CARD' });
    });

    // then
    expect(requestPayment).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'CARD',
        orderId: 'ORD-1',
        amount: { currency: 'KRW', value: 10000 },
        card: { flowMode: 'DEFAULT' },
      }),
    );
  });

  it('간편결제(TOSSPAY/NAVERPAY/KAKAOPAY)는 card.flowMode:DIRECT 와 easyPay 코드로 요청한다', async () => {
    // given
    const { result } = renderHook(() => usePaymentWindow(), { wrapper });

    // when
    await act(async () => {
      await result.current.openPaymentWindow({ ...baseParams, method: 'NAVERPAY' });
    });

    // then
    expect(requestPayment).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'CARD',
        card: { flowMode: 'DIRECT', easyPay: 'NAVERPAY' },
      }),
    );
  });

  it('가상계좌는 method:VIRTUAL_ACCOUNT, validHours:24 로 요청한다', async () => {
    // given
    const { result } = renderHook(() => usePaymentWindow(), { wrapper });

    // when
    await act(async () => {
      await result.current.openPaymentWindow({ ...baseParams, method: 'VIRTUAL_ACCOUNT' });
    });

    // then
    expect(requestPayment).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'VIRTUAL_ACCOUNT',
        virtualAccount: { validHours: 24, cashReceipt: { type: '소득공제' } },
      }),
    );
  });

  it('USER_CANCEL 은 에러 없이 조용히 끝난다', async () => {
    // given
    requestPayment.mockRejectedValue(Object.assign(new Error('취소'), { code: 'USER_CANCEL' }));
    const { result } = renderHook(() => usePaymentWindow(), { wrapper });

    // when & then
    await act(async () => {
      await expect(
        result.current.openPaymentWindow({ ...baseParams, method: 'CARD' }),
      ).resolves.toBeUndefined();
    });
    expect(result.current.isOpening).toBe(false);
  });

  it('클라이언트 키가 없으면 SDK 를 부르지 않고 조용히 끝난다', async () => {
    // given
    vi.stubEnv('VITE_TOSS_CLIENT_KEY', '');
    const { result } = renderHook(() => usePaymentWindow(), { wrapper });

    // when
    await act(async () => {
      await result.current.openPaymentWindow({ ...baseParams, method: 'CARD' });
    });

    // then
    expect(payment).not.toHaveBeenCalled();
  });
});
