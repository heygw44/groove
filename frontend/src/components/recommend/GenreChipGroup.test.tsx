import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { GenreChipGroup } from '@/components/recommend/GenreChipGroup';
import type { Genre } from '@/types/product';

const GENRES: Genre[] = [
  { id: 1, name: '록' },
  { id: 2, name: '재즈' },
  { id: 3, name: '팝' },
];

describe('GenreChipGroup', () => {
  it('최대 개수에 도달하면 선택되지 않은 칩을 비활성화한다', () => {
    // given & when
    render(<GenreChipGroup value={[1, 2]} onChange={vi.fn()} genres={GENRES} max={2} />);

    // then
    expect(screen.getByRole('button', { name: '록' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '재즈' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '팝' })).toBeDisabled();
  });

  it('선택하지 않은 칩을 누르면 추가된 목록으로 onChange 를 호출한다', async () => {
    // given
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<GenreChipGroup value={[1]} onChange={onChange} genres={GENRES} max={3} />);

    // when
    await user.click(screen.getByRole('button', { name: '재즈' }));

    // then
    expect(onChange).toHaveBeenCalledWith([1, 2]);
  });

  it('선택된 칩을 누르면 제거된 목록으로 onChange 를 호출한다', async () => {
    // given
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<GenreChipGroup value={[1, 2]} onChange={onChange} genres={GENRES} max={3} />);

    // when
    await user.click(screen.getByRole('button', { name: '록' }));

    // then
    expect(onChange).toHaveBeenCalledWith([2]);
  });
});
