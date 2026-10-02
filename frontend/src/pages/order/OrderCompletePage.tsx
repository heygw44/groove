import axios from 'axios';
import { useParams } from 'react-router-dom';

import { LinkButton } from '@/components/common/LinkButton';
import { PageContainer } from '@/components/common/PageContainer';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { getBankName } from '@/constants/banks';
import { useOrder } from '@/hooks/queries/useOrder';
import NotFoundPage from '@/pages/NotFoundPage';
import type { OrderStatus } from '@/types/order';
import type { PaymentStatus } from '@/types/payment';
import { getErrorCode } from '@/utils/apiError';
import { formatServerDateTime } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';
import { getPaymentMethodLabel } from '@/utils/paymentStatus';

const NOT_FOUND_CODES = new Set(['ORDER_NOT_FOUND']);
const ID_PATTERN = /^\d+$/;

/** 완료 페이지는 나중에 다시 열 수도 있어 주문·결제 상태에 맞는 제목을 고른다. */
function getCompleteTitle(orderStatus: OrderStatus, paymentStatus?: PaymentStatus): string {
  if (orderStatus === 'CANCELED') {
    return '취소된 주문입니다';
  }
  if (paymentStatus === 'WAITING_FOR_DEPOSIT') {
    return '주문이 접수되었습니다. 입금을 기다리고 있습니다.';
  }
  return '주문이 완료되었습니다';
}

export default function OrderCompletePage() {
  const { id: idParam } = useParams();
  const isValidId = idParam !== undefined && ID_PATTERN.test(idParam);
  const id = isValidId ? Number(idParam) : -1;
  const { showToast } = useToast();

  const { data: order, isPending, isError, error, refetch } = useOrder(id);

  if (!isValidId) {
    return <NotFoundPage />;
  }

  if (isPending) {
    return (
      <PageContainer size="sm">
        <div className="flex min-h-64 items-center justify-center">
          <Spinner size="lg" />
        </div>
      </PageContainer>
    );
  }

  const isNotFoundStatus = axios.isAxiosError(error) && error.response?.status === 404;
  if (isError && (isNotFoundStatus || NOT_FOUND_CODES.has(getErrorCode(error) ?? ''))) {
    return <NotFoundPage />;
  }

  if (isError || !order) {
    return (
      <PageContainer size="sm">
        <QueryErrorState error={error} onRetry={refetch} title="주문 정보를 불러오지 못했습니다." />
      </PageContainer>
    );
  }

  const handleCopy = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      showToast('success', '계좌번호를 복사했습니다.');
    } catch {
      showToast('error', '복사하지 못했습니다.');
    }
  };

  const payment = order.payment;
  const virtualAccount = payment?.status === 'WAITING_FOR_DEPOSIT' ? payment.virtualAccount : null;
  const title = getCompleteTitle(order.status, payment?.status);

  return (
    <PageContainer size="sm">
      <div className="flex flex-col items-center gap-2 py-8 text-center">
        <h1 className="text-xl font-bold">{title}</h1>
        <p className="font-mono text-sm text-content-muted">{order.orderNumber}</p>
      </div>

      <div className="space-y-1.5 rounded-lg border border-line bg-surface px-5 py-4 text-sm">
        <p>
          <span className="text-content-muted">결제금액</span>{' '}
          <span className="font-bold">{formatPrice(order.finalAmount)}</span>
        </p>
        {payment ? (
          <p>
            <span className="text-content-muted">결제수단</span>{' '}
            <span className="font-medium">{getPaymentMethodLabel(payment)}</span>
          </p>
        ) : (
          <p className="text-content-muted">
            결제 결과를 확인하고 있습니다. 잠시 후 다시 확인해주세요.
          </p>
        )}
      </div>

      {virtualAccount && (
        <div className="mt-4 space-y-1.5 rounded-lg border border-line-strong bg-surface-muted px-5 py-4 text-sm">
          <h2 className="mb-1 text-sm font-bold">무통장입금 계좌 안내</h2>
          <p>
            <span className="text-content-muted">은행</span>{' '}
            <span className="font-medium">{getBankName(virtualAccount.bankCode)}</span>
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-content-muted">계좌번호</span>
            <span className="font-mono font-medium">{virtualAccount.accountNumber}</span>
            <button
              type="button"
              onClick={() => void handleCopy(virtualAccount.accountNumber)}
              className="rounded-md border border-line px-2 py-0.5 text-xs text-content-muted hover:text-content"
            >
              복사
            </button>
          </div>
          <p>
            <span className="text-content-muted">입금기한</span>{' '}
            <span className="font-medium">{formatServerDateTime(virtualAccount.dueDate)}</span>
          </p>
          <p>
            <span className="text-content-muted">입금금액</span>{' '}
            <span className="font-medium">{formatPrice(order.finalAmount)}</span>
          </p>
          <p className="mt-2 text-xs text-content-muted">
            입금기한이 지나면 주문이 자동으로 취소됩니다.
          </p>
        </div>
      )}

      <div className="mt-6 flex justify-center gap-3">
        <LinkButton to={`/orders/${order.id}`} variant="secondary">
          주문 상세 보기
        </LinkButton>
        <LinkButton to="/">쇼핑 계속하기</LinkButton>
      </div>
    </PageContainer>
  );
}
