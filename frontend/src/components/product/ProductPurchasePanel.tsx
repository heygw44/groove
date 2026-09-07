import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { LinkButton } from '@/components/common/LinkButton';
import { useToast } from '@/components/common/toastContext';
import { QuantitySelector } from '@/components/product/QuantitySelector';
import { WishButton } from '@/components/product/WishButton';
import { PRODUCT_STATUS_META } from '@/constants/product';
import { useAddCartItem } from '@/hooks/mutations/useCartMutations';
import { useChangeWishlistAlert } from '@/hooks/mutations/useWishlistMutations';
import { useAuthStore } from '@/store/authStore';
import type { ProductDetail } from '@/types/product';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';
import { formatPrice } from '@/utils/formatPrice';

const LOW_STOCK_THRESHOLD = 5;

interface ProductPurchasePanelProps {
  product: ProductDetail;
}

export function ProductPurchasePanel({ product }: ProductPurchasePanelProps) {
  const isSoldOut = product.status === 'SOLD_OUT' || product.stockQuantity <= 0;
  const [quantity, setQuantity] = useState(1);
  const navigate = useNavigate();
  const location = useLocation();
  const { showToast } = useToast();
  const isLoggedIn = useAuthStore((s) => Boolean(s.accessToken));
  const addCartItemMutation = useAddCartItem();
  const changeAlertMutation = useChangeWishlistAlert();

  const requireLogin = () => {
    if (isLoggedIn) {
      return true;
    }
    const redirect = encodeURIComponent(`${location.pathname}${location.search}`);
    navigate(`/login?redirect=${redirect}`);
    return false;
  };

  const handleAddToCart = () => {
    if (!requireLogin()) {
      return;
    }
    addCartItemMutation.mutate(
      { productId: product.id, quantity },
      {
        onSuccess: () => showToast('success', '장바구니에 담았습니다.'),
        onError: (error) => showToast('error', getErrorMessage(error)),
      },
    );
  };

  const handleBuyNow = () => {
    if (!requireLogin()) {
      return;
    }
    navigate('/orders/new', { state: { productId: product.id, quantity } });
  };

  const handleToggleAlert = () => {
    const nextAlertEnabled = !product.alertEnabled;
    changeAlertMutation.mutate(
      { productId: product.id, alertEnabled: nextAlertEnabled },
      {
        onError: (error) => {
          const code = getErrorCode(error);
          // 위시에서 이미 빠진 상태라 서버 상태가 곧 우리가 보여주려던 값이다.
          if (code !== 'WISHLIST_NOT_FOUND') {
            showToast('error', getErrorMessage(error));
          }
        },
      },
    );
  };

  return (
    <div className="mt-6 flex flex-col gap-4 border-t border-line pt-6">
      <div className="flex items-center gap-2">
        <p className="text-xl font-bold">{formatPrice(product.price)}</p>
        {isSoldOut ? (
          <Badge variant="danger">{PRODUCT_STATUS_META.SOLD_OUT.label}</Badge>
        ) : (
          product.stockQuantity <= LOW_STOCK_THRESHOLD && (
            <Badge variant="accent">재고 {product.stockQuantity}개 남음</Badge>
          )
        )}
      </div>

      {!isSoldOut && !product.limitedDrop && (
        <div className="flex items-center gap-3">
          <span className="text-sm text-content-muted">수량</span>
          <QuantitySelector value={quantity} onChange={setQuantity} max={product.stockQuantity} />
        </div>
      )}

      {product.limitedDrop ? (
        // 한정반 상품은 일반 주문 경로가 막혀 있다(PRODUCT_LIMITED_ONLY) - 전용 페이지로 보낸다.
        <div className="flex gap-2">
          <span className="flex-1">
            <LinkButton to={`/limited-drops/${product.limitedDrop.id}`} className="w-full">
              한정반 보러 가기
            </LinkButton>
          </span>
          <WishButton size="md" productId={product.id} wishlisted={product.wishlisted} />
        </div>
      ) : (
        <div className="flex gap-2">
          <span className="flex-1">
            <Button className="w-full" disabled={isSoldOut} onClick={handleBuyNow}>
              바로 구매
            </Button>
          </span>
          <span className="flex-1">
            <Button
              variant="secondary"
              className="w-full"
              disabled={isSoldOut || addCartItemMutation.isPending}
              onClick={handleAddToCart}
            >
              장바구니
            </Button>
          </span>
          <WishButton size="md" productId={product.id} wishlisted={product.wishlisted} />
        </div>
      )}

      {product.wishlisted && (
        <label className="flex items-center gap-2 text-sm text-content-muted">
          <input
            type="checkbox"
            checked={Boolean(product.alertEnabled)}
            disabled={changeAlertMutation.isPending}
            onChange={handleToggleAlert}
            className="h-4 w-4 rounded border-line-strong"
          />
          재입고·가격 인하 알림 받기
        </label>
      )}
    </div>
  );
}
