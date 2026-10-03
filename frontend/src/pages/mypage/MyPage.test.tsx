import type { UseQueryResult } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { useMe } from '@/hooks/queries/useMe';
import MyPage from '@/pages/mypage/MyPage';
import type { Member, MemberRole } from '@/types/member';

vi.mock('@/hooks/queries/useMe', () => ({
  useMe: vi.fn(),
}));

vi.mock('@/components/mypage/NicknameForm', () => ({
  NicknameForm: () => null,
}));

vi.mock('@/components/mypage/PasswordChangeForm', () => ({
  PasswordChangeForm: () => null,
}));

vi.mock('@/components/mypage/WithdrawSection', () => ({
  WithdrawSection: () => <div data-testid="withdraw-section" />,
}));

const mockMe = (role: MemberRole) => {
  const member: Member = {
    id: 1,
    email: 'user@example.com',
    nickname: '그루브',
    role,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00',
  };
  vi.mocked(useMe).mockReturnValue({
    data: member,
    isPending: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
  } as unknown as UseQueryResult<Member>);
};

afterEach(() => {
  vi.clearAllMocks();
});

describe('MyPage', () => {
  it('일반 회원이면 회원 탈퇴 영역을 보여준다', () => {
    // given
    mockMe('USER');

    // when
    render(<MyPage />);

    // then
    expect(screen.getByTestId('withdraw-section')).toBeInTheDocument();
  });

  it('관리자면 회원 탈퇴 영역을 보여주지 않는다', () => {
    // given
    mockMe('ADMIN');

    // when
    render(<MyPage />);

    // then
    expect(screen.getByText('계정 정보')).toBeInTheDocument();
    expect(screen.queryByTestId('withdraw-section')).not.toBeInTheDocument();
  });
});
