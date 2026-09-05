import { Link } from 'react-router-dom';

import { ProductStatusBadge } from '@/components/admin/ProductStatusBadge';
import { Button } from '@/components/common/Button';
import type { AdminProductSummary } from '@/types/product';
import { formatDate } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';

interface AdminProductTableProps {
  products: AdminProductSummary[];
  onAdjustStock: (product: AdminProductSummary) => void;
  onHide: (product: AdminProductSummary) => void;
  onRestore: (product: AdminProductSummary) => void;
  disabled?: boolean;
}

export function AdminProductTable({
  products,
  onAdjustStock,
  onHide,
  onRestore,
  disabled = false,
}: AdminProductTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-[760px] w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs text-content-muted">
            <th className="py-2 pr-3 font-medium">썸네일</th>
            <th className="py-2 pr-3 font-medium">상품</th>
            <th className="py-2 pr-3 font-medium">가격</th>
            <th className="py-2 pr-3 font-medium">상태</th>
            <th className="py-2 pr-3 font-medium">재고</th>
            <th className="py-2 pr-3 font-medium">등록일</th>
            <th className="py-2 pr-3 font-medium">액션</th>
          </tr>
        </thead>
        <tbody>
          {products.map((product) => (
            <tr key={product.id} className="border-b border-line last:border-0">
              <td className="py-2.5 pr-3">
                {product.thumbnailUrl ? (
                  <img
                    src={product.thumbnailUrl}
                    alt=""
                    className="h-12 w-12 rounded-md object-cover"
                  />
                ) : (
                  <div className="h-12 w-12 rounded-md bg-surface-muted" aria-hidden />
                )}
              </td>
              <td className="py-2.5 pr-3">
                <p className="font-medium text-content">{product.title}</p>
                <p className="text-xs text-content-muted">{product.artistName}</p>
              </td>
              <td className="py-2.5 pr-3">{formatPrice(product.price)}</td>
              <td className="py-2.5 pr-3">
                <ProductStatusBadge status={product.status} />
              </td>
              <td className="py-2.5 pr-3">{product.stockQuantity ?? '—'}</td>
              <td className="py-2.5 pr-3 text-content-muted">{formatDate(product.createdAt)}</td>
              <td className="py-2.5 pr-3">
                <div className="flex items-center gap-1.5">
                  <Link
                    to={`/admin/products/${product.id}/edit`}
                    className="text-sm text-content hover:text-accent"
                  >
                    수정
                  </Link>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => onAdjustStock(product)}
                    disabled={disabled || product.status === 'HIDDEN'}
                  >
                    재고
                  </Button>
                  {product.status === 'HIDDEN' ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onRestore(product)}
                      disabled={disabled}
                    >
                      복구
                    </Button>
                  ) : (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onHide(product)}
                      disabled={disabled}
                    >
                      숨김
                    </Button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
