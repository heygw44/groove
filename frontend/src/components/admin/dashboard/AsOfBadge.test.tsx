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

  it('aggregatedAt 이 있으면 상대 시간을 "N분 전 기준" 형태로 보여준다', () => {
    // given
    vi.spyOn(serverTime, 'getServerNowMs').mockReturnValue(
      new Date('2026-09-05T12:10:00+09:00').getTime(),
    );

    // when
    render(<AsOfBadge aggregatedAt="2026-09-05T12:00:00+09:00" />);

    // then
    expect(screen.getByText('10분 전 기준')).toBeInTheDocument();
  });
});
