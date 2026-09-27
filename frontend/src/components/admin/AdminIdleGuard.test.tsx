import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { AdminIdleGuard } from '@/components/admin/AdminIdleGuard';
import { useAdminIdleLogout } from '@/hooks/useAdminIdleLogout';

vi.mock('@/hooks/useAdminIdleLogout', () => ({
  useAdminIdleLogout: vi.fn(),
}));

const mockedUseAdminIdleLogout = vi.mocked(useAdminIdleLogout);

describe('AdminIdleGuard', () => {
  it('경고 상태면 남은 시간과 함께 모달을 보여준다', () => {
    // given
    mockedUseAdminIdleLogout.mockReturnValue({
      warningOpen: true,
      remainingSeconds: 42,
      extend: vi.fn(),
    });

    // when
    render(<AdminIdleGuard />);

    // then
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('42초 동안 활동이 없으면 로그아웃됩니다.')).toBeInTheDocument();
  });

  it('계속 사용 버튼을 누르면 extend 를 호출한다', () => {
    // given
    const extend = vi.fn();
    mockedUseAdminIdleLogout.mockReturnValue({
      warningOpen: true,
      remainingSeconds: 10,
      extend,
    });
    render(<AdminIdleGuard />);

    // when
    fireEvent.click(screen.getByRole('button', { name: '계속 사용' }));

    // then
    expect(extend).toHaveBeenCalledTimes(1);
  });

  it('경고 상태가 아니면 모달을 렌더하지 않는다', () => {
    // given
    mockedUseAdminIdleLogout.mockReturnValue({
      warningOpen: false,
      remainingSeconds: 0,
      extend: vi.fn(),
    });

    // when
    render(<AdminIdleGuard />);

    // then
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
