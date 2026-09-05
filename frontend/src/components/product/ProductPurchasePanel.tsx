import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { useToast } from '@/components/common/toastContext';
import { QuantitySelector } from '@/components/product/QuantitySelector';
import { WishButton } from '@/components/product/WishButton';
import { useAddCartItem } from '@/hooks/mutations/useCartMutations';
import { useAuthStore } from '@/store/authStore';
import type { ProductDetail } from '@/types/product';
import { getErrorMessage } from '@/utils/apiError';
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

  return (
    <div className="mt-6 flex flex-col gap-4 border-t border-line pt-6">
      <div className="flex items-center gap-2">
        <p className="text-xl font-bold">{formatPrice(product.price)}</p>
        {isSoldOut ? (
          <Badge variant="danger">품절</Badge>
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
            <Link to={`/limited-drops/${product.limitedDrop.id}`} className="block">
              <Button className="w-full">한정반 상세로</Button>
            </Link>
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
    </div>
  );
}
