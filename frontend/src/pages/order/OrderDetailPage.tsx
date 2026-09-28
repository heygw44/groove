import axios from 'axios';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { OrderCancelDialog } from '@/components/order/OrderCancelDialog';
import { OrderItemCard } from '@/components/order/OrderItemCard';
import { OrderStatusBadge } from '@/components/order/OrderStatusBadge';
import { OrderStatusTimeline } from '@/components/order/OrderStatusTimeline';
import { PaymentInfoCard } from '@/components/order/PaymentInfoCard';
import { PaymentResumeSection } from '@/components/order/PaymentResumeSection';
import { PendingExpiryBanner } from '@/components/order/PendingExpiryBanner';
import { ShippingAddressCard } from '@/components/order/ShippingAddressCard';
import { VirtualAccountNotice } from '@/components/order/VirtualAccountNotice';
import { useCancelOrder } from '@/hooks/mutations/useOrderMutations';
import { useOrder } from '@/hooks/queries/useOrder';
import { useServerNow } from '@/hooks/useServerNow';
import NotFoundPage from '@/pages/NotFoundPage';
import type { RefundAccount } from '@/types/order';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';
import { formatServerDate, formatServerDateTime } from '@/utils/formatDate';
import { isCancelableStatus } from '@/utils/orderStatus';
import {
  CANCEL_REQUESTED_MESSAGES,
  getOrderCancelSuccessMessage,
  isCancellationPending,
} from '@/utils/paymentStatus';
import { toServerMs } from '@/utils/serverTime';

const NOT_FOUND_CODES = new Set(['ORDER_NOT_FOUND']);

const ID_PATTERN = /^\d+$/;

// 만료 스케줄러 반영을 기다리는 동안 짧은 간격으로 다시 확인한다. 상태가 PENDING 을 벗어나면 멈춘다.
const EXPIRY_POLL_MS = 5_000;

export default function OrderDetailPage() {
  const { id: idParam } = useParams();
  const isValidId = idParam !== undefined && ID_PATTERN.test(idParam);
  const id = isValidId ? Number(idParam) : -1;

  const { showToast } = useToast();
  const nowMs = useServerNow();
  const [isExpired, setIsExpired] = useState(false);
  const {
    data: order,
    isPending,
    isError,
    error,
    refetch,
  } = useOrder(id, (query) =>
    isExpired && query.state.data?.status === 'PENDING' ? EXPIRY_POLL_MS : false,
  );
  const cancelOrderMutation = useCancelOrder();
  const [isCancelDialogOpen, setIsCancelDialogOpen] = useState(false);

  useEffect(() => {
    if (!order) {
      return undefined;
    }
    const previousTitle = document.title;
    document.title = `주문 ${order.orderNumber} | GROOVE`;
    return () => {
      document.title = previousTitle;
    };
  }, [order]);

  const handleExpired = () => {
    setIsExpired(true);
    void refetch();
  };

  // enabled:false 여도 isPending 은 true 이므로, 잘못된 id 분기를 로딩 분기보다 먼저 둔다.
  if (!isValidId) {
    return <NotFoundPage />;
  }

  if (isPending) {
    return (
      <div className="flex min-h-64 items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  const isNotFoundStatus = axios.isAxiosError(error) && error.response?.status === 404;
  if (isError && (isNotFoundStatus || NOT_FOUND_CODES.has(getErrorCode(error) ?? ''))) {
    return <NotFoundPage />;
  }

  if (isError || !order) {
    return <QueryErrorState error={error} onRetry={refetch} title="주문을 불러오지 못했습니다." />;
  }

  const handleCopyOrderNumber = async () => {
    try {
      await navigator.clipboard.writeText(order.orderNumber);
      showToast('success', '주문번호를 복사했습니다.');
    } catch {
      showToast('error', '복사에 실패했습니다.');
    }
  };

  const handleCancel = (reason?: string, refundAccount?: RefundAccount) => {
    cancelOrderMutation.mutate(
      { orderId: order.id, reason, refundAccount },
      {
        onSuccess: (response) => {
          showToast('success', getOrderCancelSuccessMessage(response.payment?.status));
          setIsCancelDialogOpen(false);
        },
        onError: (error) => {
          showToast('error', getErrorMessage(error));
        },
      },
    );
  };

  const cancellationPending = isCancellationPending(order.payment?.status);
  const isWaitingForDeposit = order.payment?.status === 'WAITING_FOR_DEPOSIT';

  return (
    <div>
      <Link to="/orders" className="text-sm text-content-muted">
        ← 주문 내역
      </Link>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <span className="text-sm text-content-muted">{formatServerDate(order.createdAt)}</span>
        <div className="flex min-w-0 items-center gap-1.5">
          <span className="truncate font-mono text-sm font-bold">{order.orderNumber}</span>
          <button
            type="button"
            onClick={handleCopyOrderNumber}
            className="shrink-0 text-xs text-accent hover:text-accent-hover"
          >
            복사
          </button>
        </div>
        <OrderStatusBadge status={order.status} paymentStatus={order.payment?.status} />
        {order.limitedDropId !== undefined && (
          <Link to={`/limited-drops/${order.limitedDropId}`}>
            <Badge variant="accent">한정반</Badge>
          </Link>
        )}
      </div>

      <div className="mt-6">
        <OrderStatusTimeline status={order.status} />
      </div>

      {order.status === 'PENDING' && !isWaitingForDeposit && (
        <div className="mt-6">
          <PendingExpiryBanner
            expiresAtMs={toServerMs(order.expiresAt)}
            nowMs={nowMs}
            onExpired={handleExpired}
          />
        </div>
      )}

      {isWaitingForDeposit && order.payment?.virtualAccount && (
        <div className="mt-6">
          <VirtualAccountNotice
            virtualAccount={order.payment.virtualAccount}
            amount={order.finalAmount}
          />
        </div>
      )}

      {order.status === 'CANCELED' && order.canceledAt && (
        <div className="mt-6 rounded-lg border border-line bg-surface-muted px-5 py-4 text-sm text-content-muted">
          <p>{formatServerDateTime(order.canceledAt)} 취소</p>
          {order.cancelReason && <p className="mt-1">사유: {order.cancelReason}</p>}
        </div>
      )}

      <section className="mt-8">
        <h2 className="mb-3 text-base font-bold">주문 상품</h2>
        <div className="flex flex-col gap-3">
          {order.items.map((item, index) => (
            <OrderItemCard
              key={`${item.productId}-${index}`}
              item={item}
              orderStatus={order.status}
              paymentStatus={order.payment?.status}
            />
          ))}
        </div>
      </section>

      <section className="mt-8">
        <h2 className="mb-3 text-base font-bold">배송지</h2>
        <ShippingAddressCard address={order.shippingAddress} />
      </section>

      <PaymentResumeSection order={order} disabled={isExpired} />

      <PaymentInfoCard
        totalAmount={order.totalAmount}
        discountAmount={order.discountAmount}
        finalAmount={order.finalAmount}
        couponName={order.couponName}
        payment={order.payment}
      />

      {isCancelableStatus(order.status) && (
        <div className="mt-6 flex flex-col items-end gap-2">
          <Button
            variant="danger"
            onClick={() => setIsCancelDialogOpen(true)}
            disabled={cancellationPending}
          >
            주문 취소
          </Button>
          {cancellationPending && (
            <p className="text-sm text-content-muted">{CANCEL_REQUESTED_MESSAGES.memberReason}</p>
          )}
        </div>
      )}

      <OrderCancelDialog
        open={isCancelDialogOpen}
        onClose={() => setIsCancelDialogOpen(false)}
        onConfirm={handleCancel}
        pending={cancelOrderMutation.isPending}
        payment={order.payment}
      />
    </div>
  );
}
