import axios from 'axios';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { OrderCancelDialog } from '@/components/order/OrderCancelDialog';
import { OrderItemSummaryList } from '@/components/order/OrderItemSummaryList';
import type { OrderSummaryItem } from '@/components/order/OrderItemSummaryList';
import { OrderPriceSummary } from '@/components/order/OrderPriceSummary';
import { OrderStatusBadge } from '@/components/order/OrderStatusBadge';
import { OrderStatusTimeline } from '@/components/order/OrderStatusTimeline';
import { PendingExpiryBanner } from '@/components/order/PendingExpiryBanner';
import { ShippingAddressCard } from '@/components/order/ShippingAddressCard';
import { PaymentWidgetSection } from '@/components/payment/PaymentWidgetSection';
import { useCancelOrder } from '@/hooks/mutations/useOrderMutations';
import { useOrder } from '@/hooks/queries/useOrder';
import { useServerNow } from '@/hooks/useServerNow';
import NotFoundPage from '@/pages/NotFoundPage';
import { useAuthStore } from '@/store/authStore';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';
import { formatServerDateTime } from '@/utils/formatDate';
import { isCancelableStatus } from '@/utils/orderStatus';
import { buildOrderName } from '@/utils/paymentRedirect';
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
  const member = useAuthStore((s) => s.member);
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

  const orderItems: OrderSummaryItem[] = order.items.map((item) => ({
    key: item.productId,
    title: item.productName,
    price: item.price,
    quantity: item.quantity,
    lineAmount: item.lineAmount,
  }));

  const handleCancel = (reason?: string) => {
    cancelOrderMutation.mutate(
      { orderId: order.id, reason },
      {
        onSuccess: () => {
          showToast('success', '주문을 취소했습니다.');
          setIsCancelDialogOpen(false);
        },
        onError: (error) => {
          showToast('error', getErrorMessage(error));
        },
      },
    );
  };

  return (
    <div>
      <Link to="/orders" className="text-sm text-content-muted">
        ← 주문 내역
      </Link>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <h2 className="font-mono text-lg font-bold">{order.orderNumber}</h2>
        <OrderStatusBadge status={order.status} />
        {order.limitedDropId !== undefined && (
          <Link to={`/limited-drops/${order.limitedDropId}`}>
            <Badge variant="accent">한정반</Badge>
          </Link>
        )}
        <span className="text-sm text-content-muted">{formatServerDateTime(order.createdAt)}</span>
      </div>

      <div className="mt-6">
        <OrderStatusTimeline status={order.status} />
      </div>

      {order.status === 'PENDING' && (
        <PendingExpiryBanner
          expiresAtMs={toServerMs(order.expiresAt)}
          nowMs={nowMs}
          onExpired={handleExpired}
        />
      )}

      {order.status === 'CANCELED' && order.canceledAt && (
        <div className="mt-6 rounded-lg border border-line bg-surface-muted px-5 py-4 text-sm text-content-muted">
          <p>{formatServerDateTime(order.canceledAt)} 취소</p>
          {order.cancelReason && <p className="mt-1">사유: {order.cancelReason}</p>}
        </div>
      )}

      <section className="mt-8">
        <h2 className="mb-3 text-base font-bold">주문 상품</h2>
        <div className="rounded-lg border border-line bg-surface px-5 py-4">
          <OrderItemSummaryList items={orderItems} />
        </div>
      </section>

      <section className="mt-8">
        <h2 className="mb-3 text-base font-bold">배송지</h2>
        <ShippingAddressCard address={order.shippingAddress} />
      </section>

      <section className="mt-8 rounded-lg border border-line bg-surface p-5">
        <OrderPriceSummary
          totalAmount={order.totalAmount}
          discountAmount={order.discountAmount}
          finalAmount={order.finalAmount}
          couponName={order.couponName}
        />
      </section>

      {order.status === 'PENDING' && (
        <PaymentWidgetSection
          orderId={order.id}
          orderNumber={order.orderNumber}
          orderName={buildOrderName(order.items.map((item) => ({ productName: item.productName })))}
          amount={order.finalAmount}
          customerEmail={member?.email}
          disabled={isExpired}
        />
      )}

      {order.payment && (
        <section className="mt-8">
          <h2 className="mb-3 text-base font-bold">결제 정보</h2>
          <div className="space-y-1.5 rounded-lg border border-line bg-surface px-5 py-4 text-sm">
            <p>
              <span className="text-content-muted">결제 수단</span>{' '}
              <span className="font-medium">{order.payment.method}</span>
            </p>
            <p>
              <span className="text-content-muted">승인 시각</span>{' '}
              <span className="font-medium">{formatServerDateTime(order.payment.approvedAt)}</span>
            </p>
            {order.payment.status === 'CANCELED' && order.payment.canceledAt && (
              <p>
                <span className="text-content-muted">취소 시각</span>{' '}
                <span className="font-medium">
                  {formatServerDateTime(order.payment.canceledAt)}
                </span>
              </p>
            )}
          </div>
        </section>
      )}

      {isCancelableStatus(order.status) && (
        <div className="mt-6 flex justify-end">
          <Button variant="danger" onClick={() => setIsCancelDialogOpen(true)}>
            주문 취소
          </Button>
        </div>
      )}

      <OrderCancelDialog
        open={isCancelDialogOpen}
        onClose={() => setIsCancelDialogOpen(false)}
        onConfirm={handleCancel}
        pending={cancelOrderMutation.isPending}
        refundNotice={order.status === 'PAID'}
      />
    </div>
  );
}
