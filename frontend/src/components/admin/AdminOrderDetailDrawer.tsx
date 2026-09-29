import { Drawer } from '@/components/common/Drawer';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { Spinner } from '@/components/common/Spinner';
import {
  OrderItemSummaryList,
  type OrderSummaryItem,
} from '@/components/order/OrderItemSummaryList';
import { OrderPriceSummary } from '@/components/order/OrderPriceSummary';
import { OrderStatusBadge } from '@/components/order/OrderStatusBadge';
import { ShippingAddressCard } from '@/components/order/ShippingAddressCard';
import { PaymentStatusBadge } from '@/components/payment/PaymentStatusBadge';
import { useAdminOrder } from '@/hooks/queries/useAdminOrder';
import { formatServerDateTime } from '@/utils/formatDate';

interface AdminOrderDetailDrawerProps {
  orderId?: number;
  onClose: () => void;
}

export function AdminOrderDetailDrawer({ orderId, onClose }: AdminOrderDetailDrawerProps) {
  const { data: detail, isPending, isError, error, refetch } = useAdminOrder(orderId ?? 0);

  const items: OrderSummaryItem[] =
    detail?.items.map((item) => ({
      key: item.productId,
      title: item.productName,
      thumbnailUrl: item.thumbnailUrl ?? undefined,
      price: item.price,
      quantity: item.quantity,
      lineAmount: item.lineAmount,
      productOrderNumber: item.productOrderNumber,
      status: item.status,
      claimStatus: item.claimStatus,
    })) ?? [];

  return (
    <Drawer
      open={orderId !== undefined}
      onClose={onClose}
      side="right"
      size="lg"
      title={detail?.orderNumber ?? '주문 상세'}
    >
      {isPending && (
        <div className="flex min-h-48 items-center justify-center">
          <Spinner />
        </div>
      )}

      {!isPending && isError && (
        <QueryErrorState error={error} onRetry={refetch} title="주문 정보를 불러오지 못했습니다" />
      )}

      {!isPending && !isError && detail && (
        <div className="flex flex-col gap-6">
          <div>
            <div className="flex items-center gap-2">
              <OrderStatusBadge status={detail.status} />
              {detail.paymentStatus && <PaymentStatusBadge status={detail.paymentStatus} />}
              <span className="text-xs text-content-muted">
                {formatServerDateTime(detail.createdAt)}
              </span>
            </div>
            <p className="mt-2 break-all text-sm text-content">{detail.memberEmail}</p>
            {detail.status === 'CANCELED' && (
              <p className="mt-2 text-sm text-danger">
                {detail.canceledAt && `${formatServerDateTime(detail.canceledAt)} 취소`}
                {detail.cancelReason && ` · ${detail.cancelReason}`}
              </p>
            )}
          </div>

          <OrderItemSummaryList items={items} />

          <ShippingAddressCard address={detail.shippingAddress} />

          <OrderPriceSummary
            totalAmount={detail.totalAmount}
            discountAmount={detail.discountAmount}
            finalAmount={detail.finalAmount}
            couponName={detail.couponName}
          />
        </div>
      )}
    </Drawer>
  );
}
