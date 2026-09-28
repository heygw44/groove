import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { PageContainer } from '@/components/common/PageContainer';
import { PaymentMethodSection } from '@/components/order/PaymentMethodSection';
import { useOrder } from '@/hooks/queries/useOrder';
import { usePaymentWindow } from '@/hooks/usePaymentWindow';
import { useServerNow } from '@/hooks/useServerNow';
import { useAuthStore } from '@/store/authStore';
import type { PaymentMethodOption } from '@/types/payment';
import {
  buildOrderName,
  getTossFailMessage,
  parsePaymentFailParams,
} from '@/utils/paymentRedirect';
import { toServerMs } from '@/utils/serverTime';

export default function PaymentFailPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { code, message, orderId, orderRef } = parsePaymentFailParams(searchParams);
  const member = useAuthStore((s) => s.member);
  const nowMs = useServerNow();
  const { openPaymentWindow, isOpening } = usePaymentWindow();
  const [method, setMethod] = useState<PaymentMethodOption>('CARD');

  const { data: order } = useOrder(orderRef ?? -1);

  useEffect(() => {
    document.title = '결제 실패 | GROOVE';
  }, []);

  const failMessage = getTossFailMessage(code, message);
  const description = orderId ? `주문번호 ${orderId} · ${failMessage}` : failMessage;
  const backTo = orderRef ? `/orders/${orderRef}` : '/orders';

  const isExpired = order !== undefined && toServerMs(order.expiresAt) <= nowMs;
  const canRetry = order !== undefined && order.status === 'PENDING' && !isExpired;

  const handleRetry = async () => {
    if (!order) {
      return;
    }
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
    <PageContainer size="sm">
      <div role="status" aria-live="polite">
        <EmptyState
          title="결제에 실패했습니다"
          titleAs="h1"
          description={description}
          action={
            <div className="flex flex-col items-center gap-4">
              {canRetry && (
                <div className="w-full max-w-sm text-left">
                  <PaymentMethodSection
                    method={method}
                    onChange={setMethod}
                    allowVirtualAccount={order.limitedDropId === undefined}
                  />
                  <Button
                    className="mt-4 w-full"
                    onClick={() => void handleRetry()}
                    loading={isOpening}
                  >
                    다시 결제하기
                  </Button>
                </div>
              )}
              <div className="flex items-center gap-4">
                <Button variant="secondary" onClick={() => navigate(backTo)}>
                  주문으로 돌아가기
                </Button>
                <Link to="/orders" className="text-sm text-content-muted underline">
                  주문 내역
                </Link>
              </div>
            </div>
          }
        />
      </div>
    </PageContainer>
  );
}
