import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { Drawer } from '@/components/common/Drawer';

function DrawerHarness() {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <button type="button" onClick={() => setOpen(true)}>
        열기
      </button>
      <Drawer open={open} onClose={() => setOpen(false)} title="메뉴">
        <button type="button">메뉴 항목</button>
      </Drawer>
    </div>
  );
}

describe('Drawer', () => {
  it('ESC 로 닫으면 열었던 트리거로 포커스가 복원된다', () => {
    // given
    render(<DrawerHarness />);
    const trigger = screen.getByRole('button', { name: '열기' });
    trigger.focus();
    fireEvent.click(trigger);
    expect(screen.getByRole('dialog')).toHaveFocus();

    // when
    fireEvent.keyDown(document, { key: 'Escape' });

    // then
    expect(trigger).toHaveFocus();
  });

  it('마지막 요소에서 Tab 을 누르면 첫 요소로 순환한다', () => {
    // given
    render(<DrawerHarness />);
    fireEvent.click(screen.getByRole('button', { name: '열기' }));
    const closeButton = screen.getByRole('button', { name: '닫기' });
    const menuItem = screen.getByRole('button', { name: '메뉴 항목' });
    menuItem.focus();

    // when
    fireEvent.keyDown(document, { key: 'Tab' });

    // then
    expect(closeButton).toHaveFocus();
  });
});
