import { ANONYMOUS, loadTossPayments } from '@tosspayments/tosspayments-sdk';
import type {
  TossPaymentsWidgets,
  WidgetAgreementWidget,
  WidgetPaymentMethodWidget,
} from '@tosspayments/tosspayments-sdk';
import { useEffect, useRef, useState } from 'react';

import { Button } from '@/components/common/Button';
import { useToast } from '@/components/common/toastContext';
import { formatPrice } from '@/utils/formatPrice';
import { buildPaymentRedirectUrls, getTossFailMessage } from '@/utils/paymentRedirect';

const PAYMENT_METHODS_SELECTOR = '#payment-methods';
const PAYMENT_AGREEMENT_SELECTOR = '#payment-agreement';
const PAYMENT_METHODS_VARIANT_KEY = 'DEFAULT';
const PAYMENT_AGREEMENT_VARIANT_KEY = 'AGREEMENT';
const USER_CANCEL_CODE = 'USER_CANCEL';

interface PaymentWidgetSectionProps {
  orderId: number;
  orderNumber: string;
  orderName: string;
  amount: number;
  customerEmail?: string;
  disabled?: boolean;
}

interface RenderedWidgets {
  paymentMethod: WidgetPaymentMethodWidget;
  agreement: WidgetAgreementWidget;
}

// 페이지 이동마다 SDK 스크립트를 다시 불러오지 않도록 모듈 스코프에 캐시한다.
let tossPaymentsPromise: ReturnType<typeof loadTossPayments> | undefined;

const getTossPayments = (clientKey: string) => {
  if (!tossPaymentsPromise) {
    tossPaymentsPromise = loadTossPayments(clientKey);
  }
  return tossPaymentsPromise;
};

// 토스는 페이지당 약관 위젯을 하나만 허용한다. 이전 인스턴스의 destroy 가 끝나기 전에 새로 그리면
// AGREEMENT_WIDGET_ALREADY_RENDERED 가 나므로(StrictMode 이중 effect 포함) 마운트를 직렬화한다.
let previousTeardown: Promise<void> = Promise.resolve();

const destroyWidgets = async (rendered: RenderedWidgets) => {
  await Promise.all([rendered.paymentMethod.destroy(), rendered.agreement.destroy()]);
};

export function PaymentWidgetSection({
  orderId,
  orderNumber,
  orderName,
  amount,
  customerEmail,
  disabled = false,
}: PaymentWidgetSectionProps) {
  const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;
  const { showToast } = useToast();
  const [isReady, setIsReady] = useState(false);
  const [hasLoadError, setHasLoadError] = useState(false);
  const widgetsRef = useRef<TossPaymentsWidgets | null>(null);

  useEffect(() => {
    if (!clientKey) {
      return undefined;
    }

    let isDisposed = false;
    let rendered: RenderedWidgets | undefined;
    const waitForPrevious = previousTeardown;
    let finishTeardown = () => {};
    previousTeardown = new Promise<void>((resolve) => {
      finishTeardown = resolve;
    });

    const setup = async () => {
      await waitForPrevious;
      if (isDisposed) {
        return;
      }
      const tossPayments = await getTossPayments(clientKey);
      const widgets = tossPayments.widgets({ customerKey: ANONYMOUS });
      await widgets.setAmount({ currency: 'KRW', value: amount });
      const [paymentMethod, agreement] = await Promise.all([
        widgets.renderPaymentMethods({
          selector: PAYMENT_METHODS_SELECTOR,
          variantKey: PAYMENT_METHODS_VARIANT_KEY,
        }),
        widgets.renderAgreement({
          selector: PAYMENT_AGREEMENT_SELECTOR,
          variantKey: PAYMENT_AGREEMENT_VARIANT_KEY,
        }),
      ]);
      if (isDisposed) {
        await destroyWidgets({ paymentMethod, agreement });
        return;
      }
      rendered = { paymentMethod, agreement };
      widgetsRef.current = widgets;
      setIsReady(true);
    };

    setup()
      .catch(() => {
        if (!isDisposed) {
          setHasLoadError(true);
        }
      })
      .finally(() => {
        // 렌더 전에 언마운트됐거나 실패한 경우는 여기서, 렌더 후 언마운트는 cleanup 의 destroy 뒤에 해제한다.
        if (!rendered) {
          finishTeardown();
        }
      });

    return () => {
      isDisposed = true;
      widgetsRef.current = null;
      setIsReady(false);
      if (rendered) {
        void destroyWidgets(rendered).finally(finishTeardown);
      }
    };
  }, [clientKey, amount]);

  if (!clientKey) {
    return (
      <section className="mt-8 rounded-lg border border-dashed border-line-strong bg-surface px-5 py-4 text-sm text-content-muted">
        결제 설정이 없습니다. 관리자에게 문의해주세요.
      </section>
    );
  }

  const handlePay = async () => {
    const widgets = widgetsRef.current;
    if (!widgets) {
      return;
    }
    const { successUrl, failUrl } = buildPaymentRedirectUrls(orderId);
    try {
      await widgets.requestPayment({
        orderId: orderNumber,
        orderName,
        successUrl,
        failUrl,
        customerEmail,
      });
    } catch (error) {
      const code = (error as { code?: string } | undefined)?.code;
      showToast(code === USER_CANCEL_CODE ? 'info' : 'error', getTossFailMessage(code));
    }
  };

  return (
    <section className="mt-8 rounded-lg border border-line bg-surface p-5">
      <h2 className="mb-3 text-base font-bold">결제하기</h2>
      <div id="payment-methods" />
      <div id="payment-agreement" className="mt-2" />
      {hasLoadError && (
        <p className="mt-3 text-sm text-danger">
          결제 모듈을 불러오지 못했습니다. 새로고침해주세요.
        </p>
      )}
      {disabled && <p className="mt-3 text-sm text-danger">결제 기한이 지나 결제할 수 없습니다.</p>}
      <div className="mt-4 flex justify-end">
        <Button onClick={() => void handlePay()} disabled={disabled || !isReady}>
          {formatPrice(amount)} 결제하기
        </Button>
      </div>
    </section>
  );
}
