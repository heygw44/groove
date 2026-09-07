import { Link } from 'react-router-dom';

import { EmptyState } from '@/components/common/EmptyState';
import type { PopularProduct, PopularProductSort } from '@/types/adminStats';
import { formatPrice } from '@/utils/formatPrice';

interface PopularProductTableProps {
  items: PopularProduct[];
  sort: PopularProductSort;
  onSortChange: (sort: PopularProductSort) => void;
}

const SORT_LABEL: Record<PopularProductSort, string> = {
  quantity: '수량순',
  sales: '매출순',
};

export function PopularProductTable({ items, sort, onSortChange }: PopularProductTableProps) {
  return (
    <div>
      <div className="mb-3 flex gap-1">
        {(Object.keys(SORT_LABEL) as PopularProductSort[]).map((option) => (
          <button
            key={option}
            type="button"
            onClick={() => onSortChange(option)}
            className={`h-8 rounded-md px-3 text-xs font-medium ${
              sort === option
                ? 'bg-accent-soft text-accent-hover'
                : 'border border-line-strong text-content-muted hover:bg-surface-muted'
            }`}
          >
            {SORT_LABEL[option]}
          </button>
        ))}
      </div>

      {items.length === 0 ? (
        <EmptyState title="해당 기간의 판매 데이터가 없습니다" />
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-[720px] w-full text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs text-content-muted">
                <th scope="col" className="py-2 pr-3 text-right font-medium">
                  순위
                </th>
                <th scope="col" className="py-2 pr-3 font-medium">
                  상품
                </th>
                <th scope="col" className="py-2 pr-3 font-medium">
                  아티스트
                </th>
                <th scope="col" className="py-2 pr-3 text-right font-medium">
                  판매 수량
                </th>
                <th scope="col" className="py-2 pr-3 text-right font-medium">
                  매출
                </th>
                <th scope="col" className="py-2 pr-3 text-right font-medium">
                  주문 수
                </th>
              </tr>
            </thead>
            <tbody>
              {items.map((item, index) => (
                <tr key={item.productId} className="border-b border-line last:border-0">
                  <td className="py-2.5 pr-3 text-right tabular-nums text-content-muted">
                    {index + 1}
                  </td>
                  <td className="py-2.5 pr-3">
                    <Link
                      to={`/admin/products/${item.productId}/edit`}
                      className="font-medium text-content hover:text-accent-hover"
                    >
                      {item.productTitle}
                    </Link>
                  </td>
                  <td className="py-2.5 pr-3 text-content-muted">{item.artistName}</td>
                  <td className="py-2.5 pr-3 text-right tabular-nums">{item.soldQuantity}개</td>
                  <td className="py-2.5 pr-3 text-right tabular-nums">
                    {formatPrice(item.salesAmount)}
                  </td>
                  <td className="py-2.5 pr-3 text-right tabular-nums">{item.orderCount}건</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
