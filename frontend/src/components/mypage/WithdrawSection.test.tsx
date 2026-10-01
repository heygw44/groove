import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { WithdrawSection } from '@/components/mypage/WithdrawSection';
import { useWithdraw } from '@/hooks/mutations/useMemberMutations';

vi.mock('@/hooks/mutations/useMemberMutations', () => ({
  useWithdraw: vi.fn(),
}));

const mockWithdraw = (mutate: ReturnType<typeof vi.fn>) => {
  vi.mocked(useWithdraw).mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof useWithdraw>);
};

const openDialog = async () => {
  const user = userEvent.setup();
  render(<WithdrawSection />);
  await user.click(screen.getByRole('button', { name: '회원 탈퇴' }));
  return user;
};

afterEach(() => {
  vi.clearAllMocks();
});

describe('WithdrawSection', () => {
  it('비밀번호를 입력하지 않으면 탈퇴를 요청하지 않는다', async () => {
    // given
    const mutate = vi.fn();
    mockWithdraw(mutate);
    const user = await openDialog();

    // when
    await user.click(screen.getByRole('button', { name: '탈퇴하기' }));

    // then
    expect(await screen.findByText('비밀번호를 입력해주세요.')).toBeInTheDocument();
    expect(mutate).not.toHaveBeenCalled();
  });

  it('입력한 비밀번호로 탈퇴를 요청한다', async () => {
    // given
    const mutate = vi.fn();
    mockWithdraw(mutate);
    const user = await openDialog();

    // when
    await user.type(screen.getByLabelText('비밀번호 확인'), 'password1!');
    await user.click(screen.getByRole('button', { name: '탈퇴하기' }));

    // then
    await waitFor(() => expect(mutate).toHaveBeenCalledTimes(1));
    expect(mutate.mock.calls[0][0]).toBe('password1!');
  });

  it('서버 오류가 오면 다이얼로그 안에 오류 문구를 보여준다', async () => {
    // given
    const mutate = vi.fn((_password: string, options: { onError: (error: unknown) => void }) => {
      options.onError(new Error('boom'));
    });
    mockWithdraw(mutate);
    const user = await openDialog();

    // when
    await user.type(screen.getByLabelText('비밀번호 확인'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: '탈퇴하기' }));

    // then
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
