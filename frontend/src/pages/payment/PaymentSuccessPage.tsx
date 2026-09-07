import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { PageContainer } from '@/components/common/PageContainer';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { useConfirmPayment } from '@/hooks/mutations/usePaymentMutations';
import { getErrorMessage } from '@/utils/apiError';
import { parsePaymentSuccessParams } from '@/utils/paymentRedirect';

export default function PaymentSuccessPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { mutate: confirmPayment } = useConfirmPayment();
  const [confirmError, setConfirmError] = useState<unknown>(null);
  const hasRequestedRef = useRef(false);

  const params = useMemo(() => parsePaymentSuccessParams(searchParams), [searchParams]);

  useEffect(() => {
    document.title = confirmError ? '결제 승인 실패 | GROOVE' : '결제 승인 중 | GROOVE';
  }, [confirmError]);

  useEffect(() => {
    if (!params || hasRequestedRef.current) {
      return;
    }
    hasRequestedRef.current = true;
    confirmPayment(
      { paymentKey: params.paymentKey, orderId: params.orderId, amount: params.amount },
      {
        onSuccess: (data) => {
          showToast('success', '결제가 완료되었습니다.');
          navigate(`/orders/${data.orderId}`, { replace: true });
        },
        onError: (error) => {
          setConfirmError(error);
        },
      },
    );
    // 의존성이 바뀌어 effect 가 다시 돌아도(StrictMode 이중 실행 포함) ref 가드가 재요청을 막는다.
  }, [params, confirmPayment, navigate, showToast]);

  if (!params) {
    return (
      <PageContainer size="sm">
        <EmptyState
          title="잘못된 접근입니다"
          titleAs="h1"
          action={
            <Button variant="secondary" onClick={() => navigate('/orders')}>
              주문 내역으로
            </Button>
          }
        />
      </PageContainer>
    );
  }

  const backTo = params.orderRef ? `/orders/${params.orderRef}` : '/orders';

  // 승인 진행 중 → 실패로의 전환이 화면 갱신만으로는 무음이라, 같은 라이브 리전 안에서 내용만 바꾼다.
  return (
    <PageContainer size="sm">
      <div role="status" aria-live="polite">
        {confirmError ? (
          <EmptyState
            title="결제 승인에 실패했습니다"
            titleAs="h1"
            description={getErrorMessage(confirmError)}
            action={
              <Button variant="secondary" onClick={() => navigate(backTo)}>
                주문으로 돌아가기
              </Button>
            }
          />
        ) : (
          <div className="flex min-h-64 flex-col items-center justify-center gap-3">
            <Spinner size="lg" />
            <p className="text-sm text-content-muted">
              결제를 승인하고 있습니다. 창을 닫지 말아주세요.
            </p>
          </div>
        )}
      </div>
    </PageContainer>
  );
}
