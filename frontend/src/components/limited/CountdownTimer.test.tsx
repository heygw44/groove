import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { CountdownTimer } from '@/components/limited/CountdownTimer';

describe('CountdownTimer', () => {
  it('남은 시간을 일/시/분/초로 표시한다', () => {
    // given
    const nowMs = new Date('2026-09-04T00:00:00+09:00').getTime();
    const targetMs = new Date('2026-09-05T04:05:06+09:00').getTime();

    // when
    render(<CountdownTimer targetMs={targetMs} nowMs={nowMs} />);

    // then
    expect(screen.getByText('01')).toBeInTheDocument();
    expect(screen.getByText('04')).toBeInTheDocument();
    expect(screen.getByText('05')).toBeInTheDocument();
    expect(screen.getByText('06')).toBeInTheDocument();
    expect(screen.getByText('일')).toBeInTheDocument();
  });

  it('목표 시각을 지났으면 전부 0으로 표시한다', () => {
    // given
    const nowMs = new Date('2026-09-05T00:00:00+09:00').getTime();
    const targetMs = new Date('2026-09-04T00:00:00+09:00').getTime();

    // when
    render(<CountdownTimer targetMs={targetMs} nowMs={nowMs} />);

    // then
    expect(screen.getAllByText('00')).toHaveLength(4);
  });
});
