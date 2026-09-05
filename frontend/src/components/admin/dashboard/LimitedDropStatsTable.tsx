import { SellRateGauge } from '@/components/admin/dashboard/SellRateGauge';
import { EmptyState } from '@/components/common/EmptyState';
import { DropStatusBadge } from '@/components/limited/DropStatusBadge';
import type { LimitedDropStats } from '@/types/adminStats';
import { formatDuration } from '@/utils/adminStatsFilters';
import { formatDateTime } from '@/utils/formatDate';

interface LimitedDropStatsTableProps {
  items: LimitedDropStats[];
}

export function LimitedDropStatsTable({ items }: LimitedDropStatsTableProps) {
  if (items.length === 0) {
    return <EmptyState title="등록된 한정반이 없습니다." />;
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-[820px] w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs text-content-muted">
            <th className="py-2 pr-3 font-medium">상품</th>
            <th className="py-2 pr-3 font-medium">상태</th>
            <th className="py-2 pr-3 font-medium">판매/총량</th>
            <th className="py-2 pr-3 font-medium">판매율</th>
            <th className="py-2 pr-3 font-medium">오픈</th>
            <th className="py-2 pr-3 font-medium">마감</th>
            <th className="py-2 pr-3 font-medium">매진 소요</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.dropId} className="border-b border-line last:border-0">
              <td className="py-2.5 pr-3 font-medium text-content">{item.productTitle}</td>
              <td className="py-2.5 pr-3">
                <DropStatusBadge status={item.status} />
              </td>
              <td className="py-2.5 pr-3">
                {item.soldQuantity} / {item.totalQuantity}
              </td>
              <td className="py-2.5 pr-3">
                <SellRateGauge rate={item.sellRate} />
              </td>
              <td className="py-2.5 pr-3 text-content-muted">{formatDateTime(item.openAt)}</td>
              <td className="py-2.5 pr-3 text-content-muted">{formatDateTime(item.closeAt)}</td>
              <td className="py-2.5 pr-3 text-content-muted">
                {item.soldOutSeconds !== undefined ? formatDuration(item.soldOutSeconds) : '-'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
