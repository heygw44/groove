import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { PageContainer } from '@/components/common/PageContainer';
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
    <PageContainer size="sm">
      <div role="status" aria-live="polite">
        <EmptyState
          title="결제에 실패했습니다"
          titleAs="h1"
          description={description}
          action={
            <Button variant="secondary" onClick={() => navigate(backTo)}>
              주문으로 돌아가기
            </Button>
          }
        />
      </div>
    </PageContainer>
  );
}
