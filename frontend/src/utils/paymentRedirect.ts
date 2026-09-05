import { TOSS_FAIL_MESSAGES } from '@/constants/paymentMessages';

const DEFAULT_FAIL_MESSAGE = '결제에 실패했습니다.';

export interface PaymentSuccessParams {
  paymentKey: string;
  orderId: string;
  amount: number;
  orderRef?: number;
}

export interface PaymentFailParams {
  code?: string;
  message?: string;
  orderId?: string;
  orderRef?: number;
}

const parseOrderRef = (searchParams: URLSearchParams): number | undefined => {
  const raw = searchParams.get('orderRef');
  if (raw === null) {
    return undefined;
  }
  const parsed = Number(raw);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
};

/** 토스 성공 리다이렉트 쿼리(paymentKey, orderId, amount)를 파싱한다. 누락·비정상 값이면 null. */
export function parsePaymentSuccessParams(searchParams: URLSearchParams): PaymentSuccessParams | null {
  const paymentKey = searchParams.get('paymentKey');
  const orderId = searchParams.get('orderId');
  const amountRaw = searchParams.get('amount');
  if (!paymentKey || !orderId || !amountRaw) {
    return null;
  }

  const amount = Number(amountRaw);
  if (!Number.isInteger(amount) || amount <= 0) {
    return null;
  }

  return { paymentKey, orderId, amount, orderRef: parseOrderRef(searchParams) };
}

/** 토스 실패 리다이렉트 쿼리(code, message, orderId)를 파싱한다. 값이 없어도 null 이 아닌 빈 필드로 반환한다. */
export function parsePaymentFailParams(searchParams: URLSearchParams): PaymentFailParams {
  return {
    code: searchParams.get('code') ?? undefined,
    message: searchParams.get('message') ?? undefined,
    orderId: searchParams.get('orderId') ?? undefined,
    orderRef: parseOrderRef(searchParams),
  };
}

export function buildPaymentRedirectUrls(
  orderId: number,
  origin: string = window.location.origin,
): { successUrl: string; failUrl: string } {
  return {
    successUrl: `${origin}/payments/success?orderRef=${orderId}`,
    failUrl: `${origin}/payments/fail?orderRef=${orderId}`,
  };
}

/** 토스 orderName 규칙("생수 외 1건")에 맞춘 주문명을 만든다. */
export function buildOrderName(items: { productName: string }[]): string {
  const [first, ...rest] = items;
  if (!first) {
    return '';
  }
  return rest.length === 0 ? first.productName : `${first.productName} 외 ${rest.length}건`;
}

export function getTossFailMessage(code?: string, fallback?: string): string {
  if (code && TOSS_FAIL_MESSAGES[code]) {
    return TOSS_FAIL_MESSAGES[code];
  }
  return fallback ?? DEFAULT_FAIL_MESSAGE;
}
