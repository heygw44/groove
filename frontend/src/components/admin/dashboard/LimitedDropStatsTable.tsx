import { Fragment, useState } from 'react';

import { LimitedDropAttemptChart } from '@/components/admin/dashboard/LimitedDropAttemptChart';
import { SellRateGauge } from '@/components/admin/dashboard/SellRateGauge';
import { EmptyState } from '@/components/common/EmptyState';
import { DropStatusBadge } from '@/components/limited/DropStatusBadge';
import type { LimitedDropStats } from '@/types/adminStats';
import { formatDuration } from '@/utils/adminStatsFilters';
import { formatServerDateTime } from '@/utils/formatDate';

interface LimitedDropStatsTableProps {
  items: LimitedDropStats[];
}

const COLUMN_COUNT = 8;

export function LimitedDropStatsTable({ items }: LimitedDropStatsTableProps) {
  const [expandedDropId, setExpandedDropId] = useState<number | null>(null);

  if (items.length === 0) {
    return <EmptyState title="등록된 한정반이 없습니다." />;
  }

  const toggleExpanded = (dropId: number) => {
    setExpandedDropId((current) => (current === dropId ? null : dropId));
  };

  return (
    <div className="overflow-x-auto">
      <table className="min-w-[820px] w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs text-content-muted">
            <th className="py-2 pr-3 font-medium">상품</th>
            <th className="py-2 pr-3 font-medium">상태</th>
            <th className="py-2 pr-3 text-right font-medium">판매/총량</th>
            <th className="py-2 pr-3 font-medium">판매율</th>
            <th className="py-2 pr-3 text-right font-medium">경쟁률</th>
            <th className="py-2 pr-3 font-medium">오픈</th>
            <th className="py-2 pr-3 font-medium">마감</th>
            <th className="py-2 pr-3 font-medium">매진 소요</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => {
            const { attempts } = item;
            const isExpandable = attempts !== undefined;
            const isExpanded = isExpandable && expandedDropId === item.dropId;

            return (
              <Fragment key={item.dropId}>
                <tr className="border-b border-line last:border-0">
                  <td className="py-2.5 pr-3 font-medium text-content">
                    {isExpandable ? (
                      <button
                        type="button"
                        className="text-left hover:text-accent"
                        aria-expanded={isExpanded}
                        onClick={() => toggleExpanded(item.dropId)}
                      >
                        {item.productTitle}
                      </button>
                    ) : (
                      item.productTitle
                    )}
                  </td>
                  <td className="py-2.5 pr-3">
                    <DropStatusBadge status={item.status} />
                  </td>
                  <td className="py-2.5 pr-3 text-right tabular-nums">
                    {item.soldQuantity} / {item.totalQuantity}
                  </td>
                  <td className="py-2.5 pr-3">
                    <SellRateGauge rate={item.sellRate} />
                  </td>
                  <td className="py-2.5 pr-3 text-right tabular-nums text-content-muted">
                    {attempts !== undefined ? `${attempts.competitionRate.toFixed(1)}:1` : '-'}
                  </td>
                  <td className="py-2.5 pr-3 whitespace-nowrap text-content-muted">
                    {formatServerDateTime(item.openAt)}
                  </td>
                  <td className="py-2.5 pr-3 whitespace-nowrap text-content-muted">
                    {formatServerDateTime(item.closeAt)}
                  </td>
                  <td className="py-2.5 pr-3 text-content-muted">
                    {item.soldOutSeconds !== undefined ? formatDuration(item.soldOutSeconds) : '-'}
                  </td>
                </tr>
                {isExpanded && attempts !== undefined && (
                  <tr className="border-b border-line last:border-0 bg-surface-muted">
                    <td colSpan={COLUMN_COUNT} className="px-3 py-4">
                      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
                        <div className="sm:w-2/3">
                          <LimitedDropAttemptChart attempts={attempts} />
                        </div>
                        <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-xs text-content-muted sm:w-1/3">
                          <div className="flex justify-between gap-2">
                            <dt>성공</dt>
                            <dd className="font-medium text-content">{attempts.successCount}</dd>
                          </div>
                          <div className="flex justify-between gap-2">
                            <dt>매진</dt>
                            <dd className="font-medium text-content">{attempts.soldOutCount}</dd>
                          </div>
                          <div className="flex justify-between gap-2">
                            <dt>중복구매</dt>
                            <dd className="font-medium text-content">
                              {attempts.alreadyPurchasedCount}
                            </dd>
                          </div>
                          <div className="flex justify-between gap-2">
                            <dt>오픈전</dt>
                            <dd className="font-medium text-content">{attempts.notOpenCount}</dd>
                          </div>
                          <div className="flex justify-between gap-2">
                            <dt>마감후</dt>
                            <dd className="font-medium text-content">{attempts.closedCount}</dd>
                          </div>
                          <div className="flex justify-between gap-2">
                            <dt>총 시도</dt>
                            <dd className="font-medium text-content">{attempts.attemptCount}</dd>
                          </div>
                        </dl>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
