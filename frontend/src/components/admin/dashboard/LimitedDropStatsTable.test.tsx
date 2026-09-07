import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { LimitedDropStatsTable } from '@/components/admin/dashboard/LimitedDropStatsTable';
import type { LimitedDropStats } from '@/types/adminStats';

const baseItem: LimitedDropStats = {
  dropId: 15,
  productTitle: 'A Love Supreme',
  status: 'SOLD_OUT',
  totalQuantity: 300,
  soldQuantity: 300,
  sellRate: 100,
  openAt: '2026-09-05T20:00:00',
  closeAt: '2026-09-05T20:30:00',
  soldOutAt: '2026-09-05T20:03:41',
  soldOutSeconds: 221,
};

const withAttempts: LimitedDropStats = {
  ...baseItem,
  attempts: {
    attemptCount: 1240,
    successCount: 300,
    soldOutCount: 902,
    alreadyPurchasedCount: 30,
    notOpenCount: 6,
    closedCount: 2,
    competitionRate: 4.1,
  },
};

describe('LimitedDropStatsTable', () => {
  it('경쟁률이 표시된다', () => {
    // given & when
    render(<LimitedDropStatsTable items={[withAttempts]} />);

    // then
    expect(screen.getByText('4.1:1')).toBeInTheDocument();
  });

  it('attempts 가 없으면 경쟁률이 - 이고 펼칠 수 없다', async () => {
    // given
    const user = userEvent.setup();
    render(<LimitedDropStatsTable items={[baseItem]} />);

    // when
    await user.click(screen.getByText('A Love Supreme'));

    // then
    expect(screen.getAllByText('-').length).toBeGreaterThan(0);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByText('총 시도')).not.toBeInTheDocument();
  });

  it('행을 클릭하면 결과별 숫자가 나타난다', async () => {
    // given
    const user = userEvent.setup();
    render(<LimitedDropStatsTable items={[withAttempts]} />);

    // when
    await user.click(screen.getByRole('button', { name: 'A Love Supreme' }));

    // then
    expect(screen.getByText('총 시도')).toBeInTheDocument();
    expect(screen.getByText('902')).toBeInTheDocument();
    expect(screen.getByText('30')).toBeInTheDocument();
    expect(screen.getByText('6')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('soldOutSeconds 가 없으면 매진까지가 - 다', () => {
    // given
    const item: LimitedDropStats = { ...baseItem, soldOutSeconds: undefined };

    // when
    render(<LimitedDropStatsTable items={[item]} />);

    // then
    expect(screen.getAllByText('-').length).toBeGreaterThan(0);
  });
});
