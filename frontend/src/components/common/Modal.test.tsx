import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { Modal } from '@/components/common/Modal';

function ModalHarness() {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <button type="button" onClick={() => setOpen(true)}>
        열기
      </button>
      <Modal open={open} onClose={() => setOpen(false)} title="제목">
        <button type="button">본문 버튼</button>
      </Modal>
    </div>
  );
}

describe('Modal', () => {
  it('열리면 패널로 포커스가 이동하고, 닫히면 열었던 버튼으로 복원된다', () => {
    // given
    render(<ModalHarness />);
    const trigger = screen.getByRole('button', { name: '열기' });
    trigger.focus();

    // when
    fireEvent.click(trigger);

    // then
    expect(screen.getByRole('dialog')).toHaveFocus();

    // when
    fireEvent.keyDown(document, { key: 'Escape' });

    // then
    expect(trigger).toHaveFocus();
  });

  it('마지막 포커스 가능 요소에서 Tab 을 누르면 첫 요소로 순환한다', () => {
    // given
    render(<ModalHarness />);
    fireEvent.click(screen.getByRole('button', { name: '열기' }));
    const closeButton = screen.getByRole('button', { name: '닫기' });
    const bodyButton = screen.getByRole('button', { name: '본문 버튼' });
    bodyButton.focus();

    // when
    fireEvent.keyDown(document, { key: 'Tab' });

    // then
    expect(closeButton).toHaveFocus();
  });

  it('첫 포커스 가능 요소에서 Shift+Tab 을 누르면 마지막 요소로 순환한다', () => {
    // given
    render(<ModalHarness />);
    fireEvent.click(screen.getByRole('button', { name: '열기' }));
    const closeButton = screen.getByRole('button', { name: '닫기' });
    const bodyButton = screen.getByRole('button', { name: '본문 버튼' });
    closeButton.focus();

    // when
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true });

    // then
    expect(bodyButton).toHaveFocus();
  });
});
