import { Link } from 'react-router-dom';

import { ProductStatusBadge } from '@/components/admin/ProductStatusBadge';
import { Button } from '@/components/common/Button';
import type { AdminProductSummary } from '@/types/product';
import { formatServerDate } from '@/utils/formatDate';
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
            <th scope="col" className="py-2 pr-3 font-medium">
              썸네일
            </th>
            <th scope="col" className="py-2 pr-3 font-medium">
              상품
            </th>
            <th scope="col" className="py-2 pr-3 text-right font-medium">
              가격
            </th>
            <th scope="col" className="py-2 pr-3 font-medium">
              상태
            </th>
            <th scope="col" className="py-2 pr-3 text-right font-medium">
              재고
            </th>
            <th scope="col" className="py-2 pr-3 font-medium">
              등록일
            </th>
            <th scope="col" className="py-2 pr-3 font-medium">
              관리
            </th>
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
              <td className="py-2.5 pr-3 text-right tabular-nums">{formatPrice(product.price)}</td>
              <td className="py-2.5 pr-3">
                <ProductStatusBadge status={product.status} />
              </td>
              <td className="py-2.5 pr-3 text-right tabular-nums">
                {product.stockQuantity ?? '—'}
              </td>
              <td className="py-2.5 pr-3 whitespace-nowrap text-content-muted">
                {formatServerDate(product.createdAt)}
              </td>
              <td className="py-2.5 pr-3">
                <div className="flex items-center gap-1.5">
                  <Link
                    to={`/admin/products/${product.id}/edit`}
                    aria-label={`${product.title} 수정`}
                    className="text-sm text-content hover:text-accent-hover"
                  >
                    수정
                  </Link>
                  <Button
                    variant="secondary"
                    size="sm"
                    aria-label={`${product.title} 재고 조정`}
                    onClick={() => onAdjustStock(product)}
                    disabled={disabled || product.status === 'HIDDEN'}
                  >
                    재고
                  </Button>
                  {product.status === 'HIDDEN' ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={`${product.title} 복구`}
                      onClick={() => onRestore(product)}
                      disabled={disabled}
                    >
                      복구
                    </Button>
                  ) : (
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={`${product.title} 숨김`}
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
