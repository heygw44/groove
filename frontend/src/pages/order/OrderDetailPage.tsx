import axios from 'axios';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { OrderCancelDialog } from '@/components/order/OrderCancelDialog';
import { OrderItemSummaryList } from '@/components/order/OrderItemSummaryList';
import type { OrderSummaryItem } from '@/components/order/OrderItemSummaryList';
import { OrderPriceSummary } from '@/components/order/OrderPriceSummary';
import { OrderStatusBadge } from '@/components/order/OrderStatusBadge';
import { OrderStatusTimeline } from '@/components/order/OrderStatusTimeline';
import { ShippingAddressCard } from '@/components/order/ShippingAddressCard';
import { useCancelOrder } from '@/hooks/mutations/useOrderMutations';
import { useOrder } from '@/hooks/queries/useOrder';
import NotFoundPage from '@/pages/NotFoundPage';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';
import { formatDateTime } from '@/utils/formatDate';
import { isCancelableStatus } from '@/utils/orderStatus';

const NOT_FOUND_CODES = new Set(['ORDER_NOT_FOUND']);

const ID_PATTERN = /^\d+$/;

export default function OrderDetailPage() {
  const { id: idParam } = useParams();
  const isValidId = idParam !== undefined && ID_PATTERN.test(idParam);
  const id = isValidId ? Number(idParam) : -1;

  const { showToast } = useToast();
  const { data: order, isPending, isError, error, refetch } = useOrder(id);
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
    return (
      <EmptyState
        title="주문을 불러오지 못했습니다."
        description={getErrorMessage(error)}
        action={
          <Button variant="secondary" onClick={() => refetch()}>
            다시 시도
          </Button>
        }
      />
    );
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
    <div className="mx-auto max-w-3xl">
      <Link to="/orders" className="text-sm text-content-muted">
        ← 주문 내역
      </Link>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <h1 className="font-mono text-lg font-bold">{order.orderNumber}</h1>
        <OrderStatusBadge status={order.status} />
        <span className="text-sm text-content-muted">{formatDateTime(order.createdAt)}</span>
      </div>

      <div className="mt-6">
        <OrderStatusTimeline status={order.status} />
      </div>

      {order.status === 'PENDING' && (
        <div className="mt-6 rounded-lg border border-line bg-surface-muted px-5 py-4 text-sm text-content-muted">
          결제 대기 중입니다. {formatDateTime(order.expiresAt)} 까지 결제하지 않으면 자동
          취소됩니다.
        </div>
      )}

      {order.status === 'CANCELED' && order.canceledAt && (
        <div className="mt-6 rounded-lg border border-line bg-surface-muted px-5 py-4 text-sm text-content-muted">
          <p>{formatDateTime(order.canceledAt)} 취소</p>
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
        />
      </section>

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
      />
    </div>
  );
}
