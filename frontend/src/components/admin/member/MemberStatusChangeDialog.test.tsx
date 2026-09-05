import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { MemberStatusChangeDialog } from '@/components/admin/member/MemberStatusChangeDialog';

describe('MemberStatusChangeDialog', () => {
  it('정지 대상이면 정지 라벨과 danger 버튼을 보여준다', () => {
    // given & when
    render(
      <MemberStatusChangeDialog
        open
        nextStatus="SUSPENDED"
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    );

    // then
    expect(screen.getByRole('button', { name: '정지' })).toHaveClass('bg-danger');
  });

  it('해제 대상이면 해제 라벨과 primary 버튼을 보여준다', () => {
    // given & when
    render(
      <MemberStatusChangeDialog open nextStatus="ACTIVE" onClose={vi.fn()} onConfirm={vi.fn()} />,
    );

    // then
    expect(screen.getByRole('button', { name: '해제' })).toHaveClass('bg-content');
  });

  it('사유를 입력하고 확인하면 trim 된 사유를 전달한다', async () => {
    // given
    const user = userEvent.setup();
    const handleConfirm = vi.fn();
    render(
      <MemberStatusChangeDialog
        open
        nextStatus="SUSPENDED"
        onClose={vi.fn()}
        onConfirm={handleConfirm}
      />,
    );

    // when
    await user.type(screen.getByLabelText('사유 (선택)'), '  반복 어뷰징 신고  ');
    await user.click(screen.getByRole('button', { name: '정지' }));

    // then
    expect(handleConfirm).toHaveBeenCalledWith('반복 어뷰징 신고');
  });

  it('사유를 입력하지 않으면 undefined 를 전달한다', async () => {
    // given
    const user = userEvent.setup();
    const handleConfirm = vi.fn();
    render(
      <MemberStatusChangeDialog open nextStatus="ACTIVE" onClose={vi.fn()} onConfirm={handleConfirm} />,
    );

    // when
    await user.click(screen.getByRole('button', { name: '해제' }));

    // then
    expect(handleConfirm).toHaveBeenCalledWith(undefined);
  });

  it('pending 이면 확인·닫기 버튼이 비활성화된다', () => {
    // given & when
    render(
      <MemberStatusChangeDialog
        open
        nextStatus="SUSPENDED"
        pending
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    );

    // then
    // 모달 우상단 아이콘 버튼도 aria-label 이 "닫기" 라 접근성 이름이 겹친다 - 텍스트가 있는 푸터 버튼을 찾는다.
    const closeButtons = screen.getAllByRole('button', { name: '닫기' });
    const footerCloseButton = closeButtons.find((button) => button.textContent === '닫기');

    expect(screen.getByRole('button', { name: '정지' })).toBeDisabled();
    expect(footerCloseButton).toBeDisabled();
  });
});
