import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { Drawer } from '@/components/common/Drawer';
import { EmptyState } from '@/components/common/EmptyState';
import { Select } from '@/components/common/Select';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import {
  OrderItemSummaryList,
  type OrderSummaryItem,
} from '@/components/order/OrderItemSummaryList';
import { OrderPriceSummary } from '@/components/order/OrderPriceSummary';
import { OrderStatusBadge } from '@/components/order/OrderStatusBadge';
import { ShippingAddressCard } from '@/components/order/ShippingAddressCard';
import { useChangeAdminOrderStatus } from '@/hooks/mutations/useAdminOrderMutations';
import { adminOrderKeys } from '@/hooks/queries/queryKeys';
import { useAdminOrder } from '@/hooks/queries/useAdminOrder';
import type { OrderStatus } from '@/types/order';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';
import { formatDateTime } from '@/utils/formatDate';
import { ADMIN_ORDER_TRANSITIONS, ORDER_STATUS_LABEL } from '@/utils/orderStatus';

interface AdminOrderDetailDrawerProps {
  orderId?: number;
  onClose: () => void;
}

const STALE_STATUS_CODES = new Set(['ORDER_INVALID_STATUS_TRANSITION', 'ORDER_NOT_FOUND']);

export function AdminOrderDetailDrawer({ orderId, onClose }: AdminOrderDetailDrawerProps) {
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const { data: detail, isPending, isError, refetch } = useAdminOrder(orderId ?? 0);
  const changeStatusMutation = useChangeAdminOrderStatus();

  const [nextStatus, setNextStatus] = useState<OrderStatus | ''>('');
  const [confirming, setConfirming] = useState(false);

  // orderId 가 바뀌면(다른 주문 선택, 드로어 닫힘) 이전 선택값을 들고 있지 않게 한다.
  const [prevOrderId, setPrevOrderId] = useState(orderId);
  if (orderId !== prevOrderId) {
    setPrevOrderId(orderId);
    setNextStatus('');
    setConfirming(false);
  }

  const transitions = detail ? ADMIN_ORDER_TRANSITIONS[detail.status] : [];

  const handleConfirm = () => {
    if (!orderId || !nextStatus) {
      return;
    }
    changeStatusMutation.mutate(
      { orderId, status: nextStatus },
      {
        onSuccess: () => {
          showToast('success', '상태를 변경했습니다.');
          setNextStatus('');
          setConfirming(false);
        },
        onError: (error) => {
          setConfirming(false);
          showToast('error', getErrorMessage(error));

          const code = getErrorCode(error);
          if (code && STALE_STATUS_CODES.has(code)) {
            queryClient.invalidateQueries({ queryKey: adminOrderKeys.detail(orderId) });
            queryClient.invalidateQueries({ queryKey: adminOrderKeys.lists });
          }
        },
      },
    );
  };

  const items: OrderSummaryItem[] =
    detail?.items.map((item) => ({
      key: item.productId,
      title: item.productName,
      price: item.price,
      quantity: item.quantity,
      lineAmount: item.lineAmount,
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
        <EmptyState
          title="주문 정보를 불러오지 못했습니다."
          description="잠시 후 다시 시도해주세요."
          action={
            <Button variant="secondary" onClick={() => refetch()}>
              다시 시도
            </Button>
          }
        />
      )}

      {!isPending && !isError && detail && (
        <div className="flex flex-col gap-6">
          <div>
            <div className="flex items-center gap-2">
              <OrderStatusBadge status={detail.status} />
              <span className="text-xs text-content-muted">{formatDateTime(detail.createdAt)}</span>
            </div>
            <p className="mt-2 text-sm text-content">
              {detail.memberEmail}
              <span className="ml-1.5 text-content-muted">(회원 ID {detail.memberId})</span>
            </p>
            {detail.status === 'CANCELED' && (
              <p className="mt-2 text-sm text-danger">
                {detail.canceledAt && `${formatDateTime(detail.canceledAt)} 취소`}
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

          <div className="border-t border-line pt-5">
            <p className="mb-2 text-sm font-bold">상태 변경</p>
            {transitions.length === 0 ? (
              <p className="text-sm text-content-muted">변경 가능한 상태가 없습니다.</p>
            ) : (
              <div className="flex items-center gap-2">
                <Select
                  aria-label="변경할 상태"
                  value={nextStatus}
                  onChange={(event) => setNextStatus(event.target.value as OrderStatus | '')}
                  className="w-36"
                >
                  <option value="">상태 선택</option>
                  {transitions.map((status) => (
                    <option key={status} value={status}>
                      {ORDER_STATUS_LABEL[status]}
                    </option>
                  ))}
                </Select>
                <Button
                  onClick={() => setConfirming(true)}
                  disabled={!nextStatus || changeStatusMutation.isPending}
                >
                  변경
                </Button>
              </div>
            )}
          </div>
        </div>
      )}

      {detail && nextStatus && (
        <ConfirmDialog
          open={confirming}
          onClose={() => setConfirming(false)}
          onConfirm={handleConfirm}
          title={`${ORDER_STATUS_LABEL[detail.status]} → ${ORDER_STATUS_LABEL[nextStatus]} 로 변경할까요?`}
          description={
            nextStatus === 'CANCELED'
              ? '재고가 복구되고 결제가 있으면 함께 취소됩니다.'
              : '주문 상태가 즉시 변경됩니다.'
          }
          confirmLabel={nextStatus === 'CANCELED' ? '취소 처리' : '변경'}
          variant={nextStatus === 'CANCELED' ? 'danger' : 'primary'}
          pending={changeStatusMutation.isPending}
        />
      )}
    </Drawer>
  );
}
