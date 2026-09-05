import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { SellRateGauge } from '@/components/admin/dashboard/SellRateGauge';

describe('SellRateGauge', () => {
  it('0% 이면 바 너비가 0%이다', () => {
    // given & when
    const { container } = render(<SellRateGauge rate={0} />);

    // then
    expect(screen.getByText('0.0%')).toBeInTheDocument();
    expect((container.querySelector('.bg-accent') as HTMLElement).style.width).toBe('0%');
  });

  it('50% 이면 바 너비가 50%이다', () => {
    // given & when
    const { container } = render(<SellRateGauge rate={50} />);

    // then
    expect(screen.getByText('50.0%')).toBeInTheDocument();
    expect((container.querySelector('.bg-accent') as HTMLElement).style.width).toBe('50%');
  });

  it('100% 이면 바 너비가 100%이다', () => {
    // given & when
    const { container } = render(<SellRateGauge rate={100} />);

    // then
    expect(screen.getByText('100.0%')).toBeInTheDocument();
    expect((container.querySelector('.bg-accent') as HTMLElement).style.width).toBe('100%');
  });

  it('범위를 벗어난 값은 0~100 으로 잘라낸다', () => {
    // given & when
    const { container } = render(<SellRateGauge rate={120} />);

    // then
    expect((container.querySelector('.bg-accent') as HTMLElement).style.width).toBe('100%');
  });
});
