import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { getTossFailMessage, parsePaymentFailParams } from '@/utils/paymentRedirect';

export default function PaymentFailPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { code, message, orderId, orderRef } = parsePaymentFailParams(searchParams);

  useEffect(() => {
    document.title = '결제 실패 | GROOVE';
  }, []);

  const failMessage = getTossFailMessage(code, message);
  const description = orderId ? `주문번호 ${orderId} · ${failMessage}` : failMessage;
  const backTo = orderRef ? `/orders/${orderRef}` : '/orders';

  return (
    <EmptyState
      title="결제에 실패했습니다"
      description={description}
      action={
        <Button variant="secondary" onClick={() => navigate(backTo)}>
          주문으로 돌아가 다시 시도
        </Button>
      }
    />
  );
}
