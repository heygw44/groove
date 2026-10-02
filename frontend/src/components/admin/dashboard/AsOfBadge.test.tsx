import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AsOfBadge } from '@/components/admin/dashboard/AsOfBadge';
import * as serverTime from '@/utils/serverTime';

describe('AsOfBadge', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('aggregatedAt 이 null 이면 아무것도 렌더하지 않는다', () => {
    // given & when
    const { container } = render(<AsOfBadge aggregatedAt={null} />);

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it('aggregatedAt 이 있으면 "최근 갱신 N분 전"과 집계 주기 안내를 보여준다', () => {
    // given
    vi.spyOn(serverTime, 'getServerNowMs').mockReturnValue(
      new Date('2026-09-05T12:10:00+09:00').getTime(),
    );

    // when
    render(<AsOfBadge aggregatedAt="2026-09-05T12:00:00+09:00" />);

    // then
    expect(screen.getByText('최근 갱신 10분 전')).toBeInTheDocument();
    expect(
      screen.getByText('오늘은 15분마다, 최근 7일은 매일 새벽 다시 집계합니다.'),
    ).toBeInTheDocument();
  });
});
