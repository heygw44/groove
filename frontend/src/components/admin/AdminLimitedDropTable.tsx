import { Button } from '@/components/common/Button';
import { DropStatusBadge } from '@/components/limited/DropStatusBadge';
import type { AdminLimitedDropSummary } from '@/types/limitedDrop';
import { formatDateTime } from '@/utils/formatDate';

interface AdminLimitedDropTableProps {
  drops: AdminLimitedDropSummary[];
  onSelect: (drop: AdminLimitedDropSummary) => void;
  onEdit: (drop: AdminLimitedDropSummary) => void;
  onOpen: (drop: AdminLimitedDropSummary) => void;
  onClose: (drop: AdminLimitedDropSummary) => void;
}

export function AdminLimitedDropTable({
  drops,
  onSelect,
  onEdit,
  onOpen,
  onClose,
}: AdminLimitedDropTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-[860px] w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs text-content-muted">
            <th className="py-2 pr-3 font-medium">상품</th>
            <th className="py-2 pr-3 font-medium">수량</th>
            <th className="py-2 pr-3 font-medium">1인 한도</th>
            <th className="py-2 pr-3 font-medium">상태</th>
            <th className="py-2 pr-3 font-medium">오픈</th>
            <th className="py-2 pr-3 font-medium">마감</th>
            <th className="py-2 pr-3 font-medium">액션</th>
          </tr>
        </thead>
        <tbody>
          {drops.map((drop) => {
            const remaining = drop.totalQuantity - drop.soldCount;
            return (
              <tr key={drop.id} className="border-b border-line last:border-0">
                <td className="py-2.5 pr-3 text-content">{drop.productTitle}</td>
                <td className="py-2.5 pr-3">
                  <p>
                    {drop.soldCount} / {drop.totalQuantity}
                  </p>
                  <p className="text-xs text-content-muted">남은 {remaining}</p>
                </td>
                <td className="py-2.5 pr-3">{drop.perMemberLimit}</td>
                <td className="py-2.5 pr-3">
                  <DropStatusBadge status={drop.status} />
                </td>
                <td className="py-2.5 pr-3 text-content-muted">{formatDateTime(drop.openAt)}</td>
                <td className="py-2.5 pr-3 text-content-muted">{formatDateTime(drop.closeAt)}</td>
                <td className="py-2.5 pr-3">
                  <div className="flex items-center gap-1.5">
                    <Button variant="secondary" size="sm" onClick={() => onSelect(drop)}>
                      상세
                    </Button>
                    {drop.status === 'SCHEDULED' && (
                      <Button variant="secondary" size="sm" onClick={() => onEdit(drop)}>
                        수정
                      </Button>
                    )}
                    {drop.status === 'SCHEDULED' && (
                      <Button size="sm" onClick={() => onOpen(drop)}>
                        오픈
                      </Button>
                    )}
                    {(drop.status === 'OPEN' || drop.status === 'SOLD_OUT') && (
                      <Button variant="danger" size="sm" onClick={() => onClose(drop)}>
                        마감
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
