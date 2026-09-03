import { useState } from 'react';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { QuantitySelector } from '@/components/product/QuantitySelector';
import type { ProductDetail } from '@/types/product';
import { formatPrice } from '@/utils/formatPrice';

const LOW_STOCK_THRESHOLD = 5;

interface ProductPurchasePanelProps {
  product: ProductDetail;
}

export function ProductPurchasePanel({ product }: ProductPurchasePanelProps) {
  const isSoldOut = product.status === 'SOLD_OUT' || product.stockQuantity <= 0;
  const [quantity, setQuantity] = useState(1);

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

      {!isSoldOut && (
        <div className="flex items-center gap-3">
          <span className="text-sm text-content-muted">수량</span>
          <QuantitySelector value={quantity} onChange={setQuantity} max={product.stockQuantity} />
        </div>
      )}

      <div className="flex gap-2">
        <span title="장바구니·위시리스트는 이후 이슈에서 연결됩니다." className="flex-1">
          <Button className="w-full" disabled>
            장바구니
          </Button>
        </span>
        <span title="장바구니·위시리스트는 이후 이슈에서 연결됩니다." className="flex-1">
          <Button variant="secondary" className="w-full" disabled>
            위시리스트
          </Button>
        </span>
      </div>
      <p className="text-xs text-content-muted">
        장바구니·위시리스트는 이후 이슈에서 연결됩니다.
      </p>
    </div>
  );
}
