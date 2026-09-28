import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { PageContainer } from '@/components/common/PageContainer';
import { useOrderFormDraftReturn } from '@/hooks/useOrderFormDraftReturn';
import { getTossFailMessage, parsePaymentFailParams } from '@/utils/paymentRedirect';

/**
 * 결제 전 주문은 본인에게도 보이지 않으므로(placed_at 없음) 주문번호를 보여주지 않고,
 * "결제 이어하기" 같은 재개 버튼도 두지 않는다. sessionStorage 에 남은 주문서 초안이
 * 있으면 그 입력값으로 주문서를 다시 열어준다.
 */
export default function PaymentFailPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { code, message } = parsePaymentFailParams(searchParams);
  const { draft, backToOrderForm } = useOrderFormDraftReturn();

  useEffect(() => {
    const previousTitle = document.title;
    document.title = '결제 실패 | GROOVE';
    return () => {
      document.title = previousTitle;
    };
  }, []);

  const failMessage = getTossFailMessage(code, message);

  return (
    <PageContainer size="sm">
      <div role="status" aria-live="polite">
        <EmptyState
          title="결제에 실패했습니다"
          titleAs="h1"
          description={failMessage}
          action={
            <div className="flex items-center gap-4">
              {draft ? (
                <>
                  <Button onClick={backToOrderForm}>주문서로 돌아가기</Button>
                  <Button variant="secondary" onClick={() => navigate('/cart')}>
                    장바구니
                  </Button>
                </>
              ) : (
                <>
                  <Button onClick={() => navigate('/cart')}>장바구니</Button>
                  <Button variant="secondary" onClick={() => navigate('/')}>
                    홈으로
                  </Button>
                </>
              )}
            </div>
          }
        />
      </div>
    </PageContainer>
  );
}
