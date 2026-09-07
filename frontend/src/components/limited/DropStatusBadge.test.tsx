import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { DropStatusBadge } from '@/components/limited/DropStatusBadge';

describe('DropStatusBadge', () => {
  it('phase 가 OPENING 이면 오픈 중을 표시한다', () => {
    // given & when
    render(<DropStatusBadge phase="OPENING" />);

    // then
    expect(screen.getByText('오픈 중')).toBeInTheDocument();
  });

  it('phase 로 SCHEDULED/OPEN 을 넘기면 기존 라벨을 그대로 표시한다', () => {
    // given & when
    render(<DropStatusBadge phase="SCHEDULED" />);

    // then
    expect(screen.getByText('예정')).toBeInTheDocument();
  });

  it('status 로 넘겨도(기존 호출부) 같은 라벨을 표시한다', () => {
    // given & when
    render(<DropStatusBadge status="OPEN" />);

    // then
    expect(screen.getByText('진행중')).toBeInTheDocument();
  });
});
