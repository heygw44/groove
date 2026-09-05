import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { EmptyState } from '@/components/common/EmptyState';
import type { DailySales } from '@/types/adminStats';
import { formatPrice } from '@/utils/formatPrice';

interface DailySalesChartProps {
  data: DailySales[];
}

const CHART_HEIGHT = 280;

/** yyyy-MM-dd 를 축 눈금용 MM.DD 로 줄인다. */
const formatTick = (date: string): string => date.slice(5).replace('-', '.');

export function DailySalesChart({ data }: DailySalesChartProps) {
  const isAllZero = data.every(
    (item) => item.salesAmount === 0 && item.cancelAmount === 0 && item.orderCount === 0,
  );

  if (data.length === 0 || isAllZero) {
    return <EmptyState title="해당 기간의 매출 데이터가 없습니다." />;
  }

  return (
    <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
      <ComposedChart data={data}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-line)" />
        <XAxis dataKey="date" tickFormatter={formatTick} stroke="var(--color-content-muted)" />
        <YAxis stroke="var(--color-content-muted)" />
        <YAxis yAxisId="right" orientation="right" stroke="var(--color-content-muted)" />
        <Tooltip
          formatter={(value, name) => {
            const numericValue = typeof value === 'number' ? value : Number(value);
            const label = String(name);
            return label === '주문 수'
              ? [`${numericValue}건`, label]
              : [formatPrice(numericValue), label];
          }}
          labelFormatter={(label) => formatTick(String(label))}
        />
        <Legend />
        <Bar dataKey="salesAmount" name="매출" fill="var(--color-accent)" />
        <Bar dataKey="cancelAmount" name="취소" fill="var(--color-danger-soft)" />
        <Line
          yAxisId="right"
          type="monotone"
          dataKey="orderCount"
          name="주문 수"
          stroke="var(--color-success)"
          dot={false}
        />
      </ComposedChart>
    </ResponsiveContainer>
  );
}
