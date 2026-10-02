import { Link, useNavigate } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import {
  BUTTON_BASE_CLASS,
  BUTTON_SIZE_CLASS,
  BUTTON_VARIANT_CLASS,
} from '@/components/common/buttonStyles';
import { LinkButton } from '@/components/common/LinkButton';
import { useToast } from '@/components/common/toastContext';
import { OrderItemClaimActions } from '@/components/order/OrderItemClaimActions';
import { OrderItemStatusBadge } from '@/components/order/OrderItemStatusBadge';
import { COURIERS } from '@/constants/couriers';
import { useAddCartItem } from '@/hooks/mutations/useCartMutations';
import type { OrderItem } from '@/types/order';
import type { OrderPayment } from '@/types/payment';
import { getErrorMessage } from '@/utils/apiError';
import { formatPrice } from '@/utils/formatPrice';

interface OrderItemCardProps {
  orderId: number;
  item: OrderItem;
  /** 가상계좌 결제 여부에 따라 상품 취소 시 환불계좌 입력이 필요하다. */
  payment?: OrderPayment;
}

const ENDED_BY_REFUND_STATUSES: ReadonlyArray<OrderItem['status']> = [
  'CANCELED',
  'CANCELED_BY_NOPAYMENT',
  'RETURNED',
];

const CLAIM_ACTIONS: ReadonlyArray<OrderItem['availableActions'][number]> = [
  'CANCEL',
  'CANCEL_REQUEST',
  'RETURN_REQUEST',
  'CONFIRM',
];

function getAmountLabel(status: OrderItem['status'], payment?: OrderPayment) {
  if (status === 'CANCELED_BY_NOPAYMENT') {
    return '주문 금액';
  }
  // 입금 전에 취소된 가상계좌는 환불된 돈이 없다.
  if (status === 'CANCELED' && payment && !payment.approvedAt) {
    return '주문 금액';
  }
  if (ENDED_BY_REFUND_STATUSES.includes(status)) {
    return '환불 금액';
  }
  return '결제금액';
}

function OrderItemThumbnail({ url }: { url: string | null }) {
  return (
    <div className="h-20 w-20 shrink-0 overflow-hidden rounded-md bg-surface-muted">
      {url ? (
        <img src={url} alt="" loading="lazy" className="h-full w-full object-cover" />
      ) : (
        <div className="flex h-full w-full items-center justify-center text-content-subtle">
          <svg
            width="28"
            height="28"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.4"
            aria-hidden
          >
            <circle cx="12" cy="12" r="9" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        </div>
      )}
    </div>
  );
}

/**
 * 주문 상품 카드 하나. 재구매(장바구니 담기/바로 구매하기)는 취소·반품으로 끝난 상품에만 보여주고,
 * 나머지 액션(취소·반품·요청 철회·구매확정·배송조회·리뷰쓰기)은 서버가 내려준 `availableActions` 에
 * 있을 때만 보여준다.
 */
export function OrderItemCard({ orderId, item, payment }: OrderItemCardProps) {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const addCartItemMutation = useAddCartItem();

  const handleAddToCart = () => {
    addCartItemMutation.mutate(
      { productId: item.productId, quantity: item.quantity },
      {
        onSuccess: () => showToast('success', '장바구니에 담았습니다.'),
        onError: (error) => showToast('error', getErrorMessage(error)),
      },
    );
  };

  const handleBuyNow = () => {
    navigate('/orders/new', { state: { productId: item.productId, quantity: item.quantity } });
  };

  const canTrack =
    item.availableActions.includes('TRACK') &&
    item.courierCode !== undefined &&
    item.trackingNumber !== undefined;
  const trackingUrl =
    canTrack && item.courierCode && item.trackingNumber
      ? COURIERS[item.courierCode].trackingUrl(item.trackingNumber)
      : undefined;
  const isEndedByRefund = ENDED_BY_REFUND_STATUSES.includes(item.status);
  const canWriteReview = item.availableActions.includes('WRITE_REVIEW');
  const hasClaimActions = CLAIM_ACTIONS.some((action) => item.availableActions.includes(action));
  const canWithdrawClaim =
    item.availableActions.includes('WITHDRAW_CLAIM') && item.claimId !== undefined;
  const hasFooterContent =
    isEndedByRefund ||
    item.refundInProgress ||
    hasClaimActions ||
    canWithdrawClaim ||
    trackingUrl !== undefined ||
    canWriteReview;

  return (
    <div className="rounded-lg border border-line bg-surface px-5 py-4">
      <div className="flex min-w-0 gap-4">
        <OrderItemThumbnail url={item.thumbnailUrl} />

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <Link
              to={`/products/${item.productId}`}
              className="line-clamp-1 min-w-0 text-sm font-medium text-content hover:underline"
            >
              {item.productName}
            </Link>
            <OrderItemStatusBadge status={item.status} claimStatus={item.claimStatus} />
          </div>
          <p className="mt-0.5 font-mono text-xs text-content-muted">{item.productOrderNumber}</p>
          <p className="mt-1 text-xs text-content-muted">
            {formatPrice(item.price)} · 수량 {item.quantity}개
          </p>
          <p className="mt-1 text-sm font-bold text-content">{formatPrice(item.lineAmount)}</p>
          <p className="mt-0.5 text-xs text-content-muted">
            {getAmountLabel(item.status, payment)} {formatPrice(item.paidAmount)}
          </p>
        </div>
      </div>

      {hasFooterContent && (
        <div className="mt-3 flex flex-wrap justify-end gap-2 border-t border-line pt-3">
          {isEndedByRefund && (
            <>
              <Button
                variant="secondary"
                size="sm"
                onClick={handleAddToCart}
                loading={addCartItemMutation.isPending}
              >
                장바구니 담기
              </Button>
              <Button variant="secondary" size="sm" onClick={handleBuyNow}>
                바로 구매하기
              </Button>
            </>
          )}
          <OrderItemClaimActions orderId={orderId} item={item} payment={payment} />
          {trackingUrl && (
            <a
              href={trackingUrl}
              target="_blank"
              rel="noopener noreferrer"
              className={`${BUTTON_BASE_CLASS} ${BUTTON_VARIANT_CLASS.secondary} ${BUTTON_SIZE_CLASS.sm}`}
            >
              배송조회
            </a>
          )}
          {canWriteReview && (
            <LinkButton to={`/products/${item.productId}#reviews`} variant="secondary" size="sm">
              리뷰 쓰기
            </LinkButton>
          )}
        </div>
      )}
    </div>
  );
}
