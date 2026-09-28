import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { PageContainer } from '@/components/common/PageContainer';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { useConfirmPayment } from '@/hooks/mutations/usePaymentMutations';
import { useOrderFormDraftReturn } from '@/hooks/useOrderFormDraftReturn';
import { getErrorCode } from '@/utils/apiError';
import { clearOrderFormDraft } from '@/utils/orderDraft';
import { getPaymentConfirmErrorMessage, parsePaymentSuccessParams } from '@/utils/paymentRedirect';

/** 백엔드가 토스 승인 결과를 못 받았을 때(timeout·5xx) 내려주는 코드. 실패가 아니라 대사 대기 상태다. */
const PAYMENT_RESULT_UNKNOWN_CODE = 'PAYMENT_RESULT_UNKNOWN';

type ConfirmStatus = 'pending' | 'unknown' | 'failed';

const getConfirmStatus = (confirmError: unknown): ConfirmStatus => {
  if (!confirmError) {
    return 'pending';
  }
  return getErrorCode(confirmError) === PAYMENT_RESULT_UNKNOWN_CODE ? 'unknown' : 'failed';
};

const DOCUMENT_TITLE_BY_STATUS: Record<ConfirmStatus, string> = {
  pending: '결제 승인 중 | GROOVE',
  unknown: '결제 확인 중 | GROOVE',
  failed: '결제 승인 실패 | GROOVE',
};

interface ConfirmStatusContentProps {
  status: ConfirmStatus;
  confirmError: unknown;
  /** 주문서로 돌아갈 초안이 남아있는지. failed 상태의 버튼 분기에만 쓴다. */
  hasDraft: boolean;
  onBackToOrderForm: () => void;
  onGoToCart: () => void;
  onGoToOrders: () => void;
}

function ConfirmStatusContent({
  status,
  confirmError,
  hasDraft,
  onBackToOrderForm,
  onGoToCart,
  onGoToOrders,
}: ConfirmStatusContentProps) {
  if (status === 'pending') {
    return (
      <div className="flex min-h-64 flex-col items-center justify-center gap-3">
        <Spinner size="lg" />
        <p className="text-sm text-content-muted">
          결제를 승인하고 있습니다. 창을 닫지 말아주세요.
        </p>
      </div>
    );
  }

  if (status === 'unknown') {
    // 대사가 승인으로 확정하면 주문이 목록에 나타난다 - 지금은 아직 확정 전이라 상세로 못 간다.
    return (
      <EmptyState
        title="결제 결과를 확인하고 있습니다"
        titleAs="h1"
        description={getPaymentConfirmErrorMessage(confirmError)}
        action={
          <Button variant="secondary" onClick={onGoToOrders}>
            주문 내역 보기
          </Button>
        }
      />
    );
  }

  // failed: 승인이 명확히 실패해 주문은 계속 미확정이다(placed_at 없음) - PaymentFailPage 와 같은 규칙으로 복귀 경로를 고른다.
  return (
    <EmptyState
      title="결제 승인에 실패했습니다"
      titleAs="h1"
      description={getPaymentConfirmErrorMessage(confirmError)}
      action={
        <Button variant="secondary" onClick={hasDraft ? onBackToOrderForm : onGoToCart}>
          {hasDraft ? '주문서로 돌아가기' : '장바구니'}
        </Button>
      }
    />
  );
}

export default function PaymentSuccessPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { mutate: confirmPayment } = useConfirmPayment();
  const [confirmError, setConfirmError] = useState<unknown>(null);
  const hasRequestedRef = useRef(false);
  const { draft, backToOrderForm } = useOrderFormDraftReturn();

  const params = useMemo(() => parsePaymentSuccessParams(searchParams), [searchParams]);
  const confirmStatus = getConfirmStatus(confirmError);

  useEffect(() => {
    document.title = DOCUMENT_TITLE_BY_STATUS[confirmStatus];
  }, [confirmStatus]);

  useEffect(() => {
    if (!params || hasRequestedRef.current) {
      return;
    }
    hasRequestedRef.current = true;
    confirmPayment(
      { paymentKey: params.paymentKey, orderId: params.orderId, amount: params.amount },
      {
        onSuccess: (data) => {
          // 결제가 확정됐으니 실패 시 돌아갈 주문서 초안은 더 이상 필요 없다.
          clearOrderFormDraft();
          showToast('success', '결제가 완료되었습니다.');
          navigate(`/orders/${data.orderId}/complete`, { replace: true });
        },
        onError: (error) => {
          // 결과 불명(대사 대기)이든 명확한 실패든 아직 끝나지 않았을 수 있어 초안은 그대로 둔다.
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

  // 승인 진행 중 → 확인 중/실패로의 전환이 화면 갱신만으로는 무음이라, 같은 라이브 리전 안에서 내용만 바꾼다.
  return (
    <PageContainer size="sm">
      <div role="status" aria-live="polite">
        <ConfirmStatusContent
          status={confirmStatus}
          confirmError={confirmError}
          hasDraft={draft !== null}
          onBackToOrderForm={backToOrderForm}
          onGoToCart={() => navigate('/cart')}
          onGoToOrders={() => navigate('/orders')}
        />
      </div>
    </PageContainer>
  );
}
