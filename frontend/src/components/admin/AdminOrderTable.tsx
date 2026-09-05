import { Button } from '@/components/common/Button';
import { OrderStatusBadge } from '@/components/order/OrderStatusBadge';
import type { AdminOrderSummary } from '@/types/order';
import { formatDateTime } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';

interface AdminOrderTableProps {
  orders: AdminOrderSummary[];
  onSelect: (order: AdminOrderSummary) => void;
}

export function AdminOrderTable({ orders, onSelect }: AdminOrderTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-[820px] w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs text-content-muted">
            <th className="py-2 pr-3 font-medium">주문번호</th>
            <th className="py-2 pr-3 font-medium">회원</th>
            <th className="py-2 pr-3 font-medium">금액</th>
            <th className="py-2 pr-3 font-medium">상품 수</th>
            <th className="py-2 pr-3 font-medium">상태</th>
            <th className="py-2 pr-3 font-medium">주문일시</th>
            <th className="py-2 pr-3 font-medium">상세</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => (
            <tr key={order.id} className="border-b border-line last:border-0">
              <td className="py-2.5 pr-3">
                <button
                  type="button"
                  onClick={() => onSelect(order)}
                  className="font-medium text-content hover:text-accent"
                >
                  {order.orderNumber}
                </button>
              </td>
              <td className="py-2.5 pr-3 text-content-muted">{order.memberEmail}</td>
              <td className="py-2.5 pr-3">{formatPrice(order.finalAmount)}</td>
              <td className="py-2.5 pr-3">{order.itemCount}건</td>
              <td className="py-2.5 pr-3">
                <OrderStatusBadge status={order.status} />
              </td>
              <td className="py-2.5 pr-3 text-content-muted">{formatDateTime(order.createdAt)}</td>
              <td className="py-2.5 pr-3">
                <Button variant="secondary" size="sm" onClick={() => onSelect(order)}>
                  상세
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
