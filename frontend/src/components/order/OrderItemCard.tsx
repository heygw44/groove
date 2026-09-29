import { Link, useNavigate } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import {
  BUTTON_BASE_CLASS,
  BUTTON_SIZE_CLASS,
  BUTTON_VARIANT_CLASS,
} from '@/components/common/buttonStyles';
import { LinkButton } from '@/components/common/LinkButton';
import { useToast } from '@/components/common/toastContext';
import { OrderItemStatusBadge } from '@/components/order/OrderItemStatusBadge';
import { COURIERS } from '@/constants/couriers';
import { useAddCartItem } from '@/hooks/mutations/useCartMutations';
import type { OrderItem } from '@/types/order';
import { getErrorMessage } from '@/utils/apiError';
import { formatPrice } from '@/utils/formatPrice';

interface OrderItemCardProps {
  item: OrderItem;
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
 * 주문 상품 카드 하나. 재구매(장바구니 담기/바로 구매하기)는 항상 보여주고, 배송조회·리뷰쓰기는
 * `availableActions` 에 있을 때만 보여준다. 취소·반품·요청철회·구매확정은 아직 처리 API 가 없어
 * `availableActions` 에 있어도 그리지 않는다(다음 이슈에서 API 와 함께 추가).
 */
export function OrderItemCard({ item }: OrderItemCardProps) {
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
  const canWriteReview = item.availableActions.includes('WRITE_REVIEW');

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
        </div>
      </div>

      <div className="mt-3 flex flex-wrap justify-end gap-2 border-t border-line pt-3">
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
    </div>
  );
}
