import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { PaymentMethodSection } from '@/components/order/PaymentMethodSection';
import { usePaymentWindow } from '@/hooks/usePaymentWindow';
import { useAuthStore } from '@/store/authStore';
import type { OrderDetail } from '@/types/order';
import type { PaymentMethodOption } from '@/types/payment';
import { formatPrice } from '@/utils/formatPrice';
import { buildOrderName } from '@/utils/paymentRedirect';

interface PaymentResumeSectionProps {
  order: OrderDetail;
  disabled: boolean;
}

/**
 * 결제를 마치지 못한 PENDING 주문에 "결제 이어하기"를 둔다. 입금대기(WAITING_FOR_DEPOSIT)는
 * 이미 계좌가 발급된 상태라 다시 결제창을 열 필요가 없어 아무것도 그리지 않는다.
 */
export function PaymentResumeSection({ order, disabled }: PaymentResumeSectionProps) {
  const member = useAuthStore((s) => s.member);
  const { openPaymentWindow, isOpening } = usePaymentWindow();
  const [isOpen, setIsOpen] = useState(false);
  const [method, setMethod] = useState<PaymentMethodOption>('CARD');

  if (order.status !== 'PENDING' || order.payment?.status === 'WAITING_FOR_DEPOSIT') {
    return null;
  }

  const allowVirtualAccount = order.limitedDropId === undefined;

  const handlePay = async () => {
    await openPaymentWindow({
      orderId: order.id,
      orderNumber: order.orderNumber,
      orderName: buildOrderName(order.items.map((item) => ({ productName: item.productName }))),
      amount: order.finalAmount,
      method,
      customerEmail: member?.email,
    });
  };

  return (
    <section className="mt-8 rounded-lg border border-line bg-surface p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-content-muted">
          {disabled
            ? '결제 기한이 지나 곧 자동으로 취소됩니다.'
            : '아직 결제가 끝나지 않았습니다. 이어서 결제할 수 있습니다.'}
        </p>
        {!isOpen && (
          <Button variant="secondary" onClick={() => setIsOpen(true)} disabled={disabled}>
            결제 이어하기
          </Button>
        )}
      </div>

      {isOpen && (
        <div className="mt-4 flex flex-col gap-4 border-t border-line pt-4">
          <PaymentMethodSection
            method={method}
            onChange={setMethod}
            allowVirtualAccount={allowVirtualAccount}
          />
          <div className="flex justify-end">
            <Button onClick={() => void handlePay()} loading={isOpening} disabled={disabled}>
              {formatPrice(order.finalAmount)} 결제하기
            </Button>
          </div>
        </div>
      )}
    </section>
  );
}
