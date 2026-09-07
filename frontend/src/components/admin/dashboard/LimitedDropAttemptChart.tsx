import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { EmptyState } from '@/components/common/EmptyState';
import type { LimitedDropAttempts } from '@/types/adminStats';

interface LimitedDropAttemptChartProps {
  attempts: LimitedDropAttempts;
}

const CHART_HEIGHT = 180;

interface AttemptBar {
  label: string;
  count: number;
  color: string;
}

export function LimitedDropAttemptChart({ attempts }: LimitedDropAttemptChartProps) {
  const data: AttemptBar[] = [
    { label: '성공', count: attempts.successCount, color: 'var(--color-success)' },
    { label: '매진', count: attempts.soldOutCount, color: 'var(--color-accent)' },
    {
      label: '중복구매',
      count: attempts.alreadyPurchasedCount,
      color: 'var(--color-accent-hover)',
    },
    { label: '오픈전', count: attempts.notOpenCount, color: 'var(--color-content-muted)' },
    { label: '마감후', count: attempts.closedCount, color: 'var(--color-content-subtle)' },
  ];

  const isAllZero = data.every((item) => item.count === 0);
  if (isAllZero) {
    return <EmptyState title="집계된 시도가 없습니다." />;
  }

  return (
    <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
      <BarChart data={data} layout="vertical" margin={{ left: 8, right: 16 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-line)" horizontal={false} />
        <XAxis type="number" allowDecimals={false} stroke="var(--color-content-muted)" />
        <YAxis type="category" dataKey="label" width={64} stroke="var(--color-content-muted)" />
        <Tooltip
          formatter={(value) => {
            const numericValue = typeof value === 'number' ? value : Number(value);
            return [`${numericValue}건`, '시도'];
          }}
        />
        <Bar dataKey="count" radius={[0, 4, 4, 0]}>
          {data.map((item) => (
            <Cell key={item.label} fill={item.color} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
