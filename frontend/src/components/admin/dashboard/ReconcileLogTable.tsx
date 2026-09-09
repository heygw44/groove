import { Badge } from '@/components/common/Badge';
import { EmptyState } from '@/components/common/EmptyState';
import {
  RECONCILE_AMOUNT_METRICS,
  RECONCILE_METRIC_LABELS,
  RECONCILE_SEVERITY_LABELS,
} from '@/constants/adminReconcile';
import type { ReconcileLog } from '@/types/adminStats';
import { formatServerDateTime } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';

interface ReconcileLogTableProps {
  logs: ReconcileLog[];
}

const formatMetricValue = (metric: ReconcileLog['metric'], value: number): string =>
  RECONCILE_AMOUNT_METRICS.has(metric) ? formatPrice(value) : `${value}건`;

/** saleDate 는 'yyyy-MM-dd' 문자열이라 Date 로 파싱하면 타임존에 따라 날짜가 밀린다. */
const formatSaleDate = (saleDate: string): string => saleDate.replaceAll('-', '.');

export function ReconcileLogTable({ logs }: ReconcileLogTableProps) {
  if (logs.length === 0) {
    return <EmptyState title="불일치 내역이 없습니다" />;
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-[820px] w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs text-content-muted">
            <th scope="col" className="py-2 pr-3 font-medium">
              날짜
            </th>
            <th scope="col" className="py-2 pr-3 font-medium">
              지표
            </th>
            <th scope="col" className="py-2 pr-3 font-medium">
              심각도
            </th>
            <th scope="col" className="py-2 pr-3 font-medium">
              기대값(원본)
            </th>
            <th scope="col" className="py-2 pr-3 font-medium">
              실제값(집계)
            </th>
            <th scope="col" className="py-2 pr-3 font-medium">
              복구 여부
            </th>
            <th scope="col" className="py-2 pr-3 font-medium">
              발생 시각
            </th>
          </tr>
        </thead>
        <tbody>
          {logs.map((log) => (
            <tr key={log.id} className="border-b border-line last:border-0">
              <td className="py-2.5 pr-3 whitespace-nowrap text-content-muted">
                {formatSaleDate(log.saleDate)}
              </td>
              <td className="py-2.5 pr-3 whitespace-nowrap">{RECONCILE_METRIC_LABELS[log.metric]}</td>
              <td className="py-2.5 pr-3">
                <Badge variant={log.severity === 'CRITICAL' ? 'danger' : 'accent'}>
                  {RECONCILE_SEVERITY_LABELS[log.severity]}
                </Badge>
              </td>
              <td className="py-2.5 pr-3 whitespace-nowrap">
                {formatMetricValue(log.metric, log.expectedValue)}
              </td>
              <td className="py-2.5 pr-3 whitespace-nowrap">
                {formatMetricValue(log.metric, log.actualValue)}
              </td>
              <td className="py-2.5 pr-3">
                <Badge variant={log.repaired ? 'success' : 'danger'}>
                  {log.repaired ? '복구됨' : '미복구'}
                </Badge>
              </td>
              <td className="py-2.5 pr-3 whitespace-nowrap text-content-muted">
                {formatServerDateTime(log.createdAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
