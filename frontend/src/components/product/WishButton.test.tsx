import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { WishToggle } from '@/components/product/WishButton';

describe('WishToggle', () => {
  it('담기지 않은 상태면 aria-pressed 가 false 이고 담기 라벨을 보여준다', () => {
    // given & when
    render(<WishToggle pressed={false} onClick={vi.fn()} />);

    // then
    const button = screen.getByRole('button', { name: '위시리스트에 담기' });
    expect(button).toHaveAttribute('aria-pressed', 'false');
  });

  it('담긴 상태면 aria-pressed 가 true 이고 빼기 라벨을 보여준다', () => {
    // given & when
    render(<WishToggle pressed onClick={vi.fn()} />);

    // then
    const button = screen.getByRole('button', { name: '위시리스트에서 빼기' });
    expect(button).toHaveAttribute('aria-pressed', 'true');
  });

  it('클릭하면 onClick 을 호출한다', async () => {
    // given
    const user = userEvent.setup();
    const handleClick = vi.fn();
    render(<WishToggle pressed={false} onClick={handleClick} />);

    // when
    await user.click(screen.getByRole('button', { name: '위시리스트에 담기' }));

    // then
    expect(handleClick).toHaveBeenCalledTimes(1);
  });
});
