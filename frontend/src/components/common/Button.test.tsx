import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { Button } from '@/components/common/Button';

describe('Button', () => {
  it('loading 이면 스피너가 붙고 눌리지 않는다', async () => {
    // given
    const user = userEvent.setup();
    const onClick = vi.fn();

    // when
    render(
      <Button loading onClick={onClick}>
        저장
      </Button>,
    );
    const button = screen.getByRole('button', { name: '저장' });
    await user.click(button);

    // then
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    expect(onClick).not.toHaveBeenCalled();
  });

  it('버튼 안 스피너는 접근성 이름을 오염시키지 않는다', () => {
    // given & when
    render(<Button loading>저장</Button>);

    // then
    expect(screen.getByRole('button', { name: '저장' })).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('loading 이 아니면 평소처럼 눌린다', async () => {
    // given
    const user = userEvent.setup();
    const onClick = vi.fn();

    // when
    render(<Button onClick={onClick}>저장</Button>);
    await user.click(screen.getByRole('button', { name: '저장' }));

    // then
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
