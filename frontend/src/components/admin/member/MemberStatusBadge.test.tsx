import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { MemberStatusBadge } from '@/components/admin/member/MemberStatusBadge';

describe('MemberStatusBadge', () => {
  it('ACTIVE 면 success 배지로 "활성" 을 보여준다', () => {
    // given & when
    render(<MemberStatusBadge status="ACTIVE" />);

    // then
    expect(screen.getByText('활성')).toHaveClass('bg-success-soft');
  });

  it('SUSPENDED 면 danger 배지로 "정지" 를 보여준다', () => {
    // given & when
    render(<MemberStatusBadge status="SUSPENDED" />);

    // then
    expect(screen.getByText('정지')).toHaveClass('bg-danger-soft');
  });

  it('WITHDRAWN 이면 neutral 배지로 "탈퇴" 를 보여준다', () => {
    // given & when
    render(<MemberStatusBadge status="WITHDRAWN" />);

    // then
    expect(screen.getByText('탈퇴')).toHaveClass('bg-surface-muted');
  });
});
