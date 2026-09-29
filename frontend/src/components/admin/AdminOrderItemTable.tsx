import { Button } from '@/components/common/Button';
import { OrderItemStatusBadge } from '@/components/order/OrderItemStatusBadge';
import { COURIERS } from '@/constants/couriers';
import type { AdminOrderItemSummary } from '@/types/adminOrder';
import { canCancelItem } from '@/utils/adminOrderActions';
import { formatServerDateTime } from '@/utils/formatDate';

interface AdminOrderItemTableProps {
  items: AdminOrderItemSummary[];
  selectedIds: number[];
  onToggle: (id: number) => void;
  onToggleAll: (checked: boolean) => void;
  onCancel: (item: AdminOrderItemSummary) => void;
  onOpenOrder: (orderId: number) => void;
}

const HEADERS = [
  '상품주문번호',
  '주문번호',
  '회원',
  '상품',
  '수량',
  '상태',
  '송장',
  '주문일시',
  '관리',
];

export function AdminOrderItemTable({
  items,
  selectedIds,
  onToggle,
  onToggleAll,
  onCancel,
  onOpenOrder,
}: AdminOrderItemTableProps) {
  const isAllSelected = items.length > 0 && items.every((item) => selectedIds.includes(item.id));

  return (
    <div className="overflow-x-auto">
      <table className="min-w-[1080px] w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs text-content-muted">
            <th scope="col" className="w-8 py-2 pr-3">
              <input
                type="checkbox"
                aria-label="현재 페이지 전체 선택"
                checked={isAllSelected}
                onChange={(event) => onToggleAll(event.target.checked)}
              />
            </th>
            {HEADERS.map((header) => (
              <th key={header} scope="col" className="py-2 pr-3 font-medium">
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr
              key={item.id}
              className="cursor-pointer border-b border-line last:border-0 hover:bg-surface-muted"
              onClick={() => onOpenOrder(item.orderId)}
            >
              <td className="py-2.5 pr-3" onClick={(event) => event.stopPropagation()}>
                <input
                  type="checkbox"
                  aria-label={`상품주문 ${item.productOrderNumber} 선택`}
                  checked={selectedIds.includes(item.id)}
                  onChange={() => onToggle(item.id)}
                />
              </td>
              <td className="py-2.5 pr-3 font-medium">{item.productOrderNumber}</td>
              <td className="py-2.5 pr-3">
                <button
                  type="button"
                  aria-label={`주문 ${item.orderNumber} 상세 보기`}
                  className="text-content-muted underline-offset-2 hover:text-content hover:underline"
                  onClick={(event) => {
                    event.stopPropagation();
                    onOpenOrder(item.orderId);
                  }}
                >
                  {item.orderNumber}
                </button>
              </td>
              <td className="py-2.5 pr-3 text-content-muted">{item.memberEmail}</td>
              <td className="py-2.5 pr-3">{item.productName}</td>
              <td className="py-2.5 pr-3 tabular-nums">{item.quantity}개</td>
              <td className="py-2.5 pr-3">
                <OrderItemStatusBadge status={item.status} claimStatus={item.claimStatus} />
              </td>
              <td className="py-2.5 pr-3 text-content-muted">
                {item.courierCode && item.trackingNumber
                  ? `${COURIERS[item.courierCode].name} ${item.trackingNumber}`
                  : '-'}
              </td>
              <td className="py-2.5 pr-3 whitespace-nowrap text-content-muted">
                {formatServerDateTime(item.createdAt)}
              </td>
              <td className="py-2.5 pr-3" onClick={(event) => event.stopPropagation()}>
                {canCancelItem(item) && (
                  <Button
                    variant="secondary"
                    size="sm"
                    aria-label={`상품주문 ${item.productOrderNumber} 판매취소`}
                    onClick={() => onCancel(item)}
                  >
                    판매취소
                  </Button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
