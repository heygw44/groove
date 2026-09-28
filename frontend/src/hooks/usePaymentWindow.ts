import { ANONYMOUS, loadTossPayments } from '@tosspayments/tosspayments-sdk';
import { useState } from 'react';

import { useToast } from '@/components/common/toastContext';
import type { PaymentMethodOption } from '@/types/payment';
import { buildPaymentRedirectUrls, getTossFailMessage } from '@/utils/paymentRedirect';

const USER_CANCEL_CODE = 'USER_CANCEL';
const VIRTUAL_ACCOUNT_VALID_HOURS = 24;
const CASH_RECEIPT_TYPE = '소득공제';

/** 간편결제 3종의 method:'CARD', card:{flowMode:'DIRECT'} 호출용 간편결제 코드. */
const EASY_PAY_CODES: Partial<Record<PaymentMethodOption, string>> = {
  TOSSPAY: 'TOSSPAY',
  NAVERPAY: 'NAVERPAY',
  KAKAOPAY: 'KAKAOPAY',
};

interface OpenPaymentWindowParams {
  orderId: number;
  orderNumber: string;
  orderName: string;
  amount: number;
  method: PaymentMethodOption;
  customerEmail?: string;
}

// 페이지 이동마다 SDK 스크립트를 다시 불러오지 않도록 모듈 스코프에 캐시한다.
let tossPaymentsPromise: ReturnType<typeof loadTossPayments> | undefined;

const getTossPayments = (clientKey: string) => {
  if (!tossPaymentsPromise) {
    // 네트워크 오류로 한 번 실패한 Promise 가 남으면 새로고침 전까지 결제가 막히므로 실패 시 비운다.
    tossPaymentsPromise = loadTossPayments(clientKey).catch((error: unknown) => {
      tossPaymentsPromise = undefined;
      throw error;
    });
  }
  return tossPaymentsPromise;
};

/**
 * 결제창 SDK(`tossPayments.payment().requestPayment()`)를 열어 수단별 결제를 요청한다.
 * successUrl/failUrl 로 리다이렉트되므로 성공 시 반환값은 없다. USER_CANCEL 은 호출부가
 * 주문서/주문 상세에 그대로 머무를 수 있도록 조용히 끝낸다.
 */
export function usePaymentWindow() {
  const { showToast } = useToast();
  const [isOpening, setIsOpening] = useState(false);

  const openPaymentWindow = async ({
    orderId,
    orderNumber,
    orderName,
    amount,
    method,
    customerEmail,
  }: OpenPaymentWindowParams): Promise<void> => {
    const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;
    if (!clientKey) {
      showToast('error', '지금은 결제를 진행할 수 없습니다. 잠시 후 다시 시도해주세요.');
      return;
    }

    setIsOpening(true);
    try {
      const tossPayments = await getTossPayments(clientKey);
      const payment = tossPayments.payment({ customerKey: ANONYMOUS });
      const { successUrl, failUrl } = buildPaymentRedirectUrls(orderId);

      if (method === 'VIRTUAL_ACCOUNT') {
        await payment.requestPayment({
          method: 'VIRTUAL_ACCOUNT',
          amount: { currency: 'KRW', value: amount },
          orderId: orderNumber,
          orderName,
          customerEmail,
          successUrl,
          failUrl,
          virtualAccount: {
            validHours: VIRTUAL_ACCOUNT_VALID_HOURS,
            cashReceipt: { type: CASH_RECEIPT_TYPE },
          },
        });
        return;
      }

      const easyPay = EASY_PAY_CODES[method];
      await payment.requestPayment({
        method: 'CARD',
        amount: { currency: 'KRW', value: amount },
        orderId: orderNumber,
        orderName,
        customerEmail,
        successUrl,
        failUrl,
        card: easyPay ? { flowMode: 'DIRECT', easyPay } : { flowMode: 'DEFAULT' },
      });
    } catch (error) {
      const code = (error as { code?: string } | undefined)?.code;
      if (code !== USER_CANCEL_CODE) {
        showToast('error', getTossFailMessage(code));
      }
    } finally {
      setIsOpening(false);
    }
  };

  return { openPaymentWindow, isOpening };
}
