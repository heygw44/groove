import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { PendingExpiryBanner } from '@/components/order/PendingExpiryBanner';

describe('PendingExpiryBanner', () => {
  it('남은 시간을 mm:ss 로 보여준다', () => {
    // given
    const nowMs = new Date('2026-09-04T00:00:00+09:00').getTime();
    const expiresAtMs = nowMs + 9 * 60 * 1000 + 5 * 1000;

    // when
    render(
      <PendingExpiryBanner expiresAtMs={expiresAtMs} nowMs={nowMs} onExpired={vi.fn()} />,
    );

    // then
    expect(screen.getByText(/09:05/)).toBeInTheDocument();
    expect(screen.getByText(/결제 대기 중입니다/)).toBeInTheDocument();
  });

  it('만료 시각이 지나면 만료 처리 안내를 보여주고 onExpired 를 한 번 호출한다', () => {
    // given
    const nowMs = new Date('2026-09-04T00:10:01+09:00').getTime();
    const expiresAtMs = new Date('2026-09-04T00:10:00+09:00').getTime();
    const onExpired = vi.fn();

    // when
    render(<PendingExpiryBanner expiresAtMs={expiresAtMs} nowMs={nowMs} onExpired={onExpired} />);

    // then
    expect(screen.getByText('만료 처리 중…')).toBeInTheDocument();
    expect(onExpired).toHaveBeenCalledTimes(1);
  });
});
