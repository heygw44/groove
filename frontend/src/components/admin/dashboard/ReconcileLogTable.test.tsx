import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ReconcileLogTable } from '@/components/admin/dashboard/ReconcileLogTable';
import type { ReconcileLog } from '@/types/adminStats';

const buildLog = (overrides: Partial<ReconcileLog> = {}): ReconcileLog => ({
  id: 1,
  saleDate: '2026-09-07',
  metric: 'DAILY_SALES_AMOUNT',
  severity: 'CRITICAL',
  expectedValue: 120000,
  actualValue: 100000,
  repaired: false,
  createdAt: '2026-09-08T04:00:00+09:00',
  ...overrides,
});

describe('ReconcileLogTable', () => {
  it('목록이 비어 있으면 빈 상태를 보여준다', () => {
    // given & when
    render(<ReconcileLogTable logs={[]} />);

    // then
    expect(screen.getByText('불일치 내역이 없습니다')).toBeInTheDocument();
  });

  it('금액 지표는 원 단위로, 지표·심각도는 한국어 라벨로 표시한다', () => {
    // given & when
    render(<ReconcileLogTable logs={[buildLog()]} />);

    // then
    expect(screen.getByText('일별 매출액')).toBeInTheDocument();
    expect(screen.getByText('심각')).toBeInTheDocument();
    expect(screen.getByText('120,000원')).toBeInTheDocument();
    expect(screen.getByText('100,000원')).toBeInTheDocument();
    expect(screen.getByText('미복구')).toBeInTheDocument();
  });

  it('건수 지표는 "N건" 형식으로 표시하고 복구된 건은 복구됨으로 표시한다', () => {
    // given
    const log = buildLog({
      metric: 'DAILY_ORDER_COUNT',
      severity: 'WARN',
      expectedValue: 12,
      actualValue: 10,
      repaired: true,
    });

    // when
    render(<ReconcileLogTable logs={[log]} />);

    // then
    expect(screen.getByText('일별 주문 건수')).toBeInTheDocument();
    expect(screen.getByText('경고')).toBeInTheDocument();
    expect(screen.getByText('12건')).toBeInTheDocument();
    expect(screen.getByText('10건')).toBeInTheDocument();
    expect(screen.getByText('복구됨')).toBeInTheDocument();
  });
});
