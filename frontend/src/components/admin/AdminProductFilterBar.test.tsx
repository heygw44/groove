import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminProductFilterBar } from '@/components/admin/AdminProductFilterBar';

describe('AdminProductFilterBar', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('키워드를 입력하면 디바운스 후 trim 된 값으로 replace 변경을 알린다', async () => {
    // given
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const handleChange = vi.fn();
    render(<AdminProductFilterBar filters={{ keyword: '' }} onChange={handleChange} />);

    // when
    await user.type(screen.getByPlaceholderText('제목 또는 아티스트'), '  miles ');
    expect(handleChange).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(300);

    // then
    expect(handleChange).toHaveBeenCalledWith({ keyword: 'miles' }, { replace: true });
  });

  it('상태를 고르면 상태만 변경을 알린다', async () => {
    // given
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const handleChange = vi.fn();
    render(<AdminProductFilterBar filters={{ keyword: '' }} onChange={handleChange} />);

    // when
    await user.selectOptions(screen.getByLabelText('상태 필터'), 'SOLD_OUT');

    // then
    expect(handleChange).toHaveBeenCalledWith({ status: 'SOLD_OUT' });
  });

  it('필터가 있으면 초기화 버튼이 키워드와 상태를 비운다', async () => {
    // given
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const handleChange = vi.fn();
    render(
      <AdminProductFilterBar
        filters={{ keyword: 'miles', status: 'ON_SALE' }}
        onChange={handleChange}
      />,
    );

    // when
    await user.click(screen.getByRole('button', { name: '초기화' }));

    // then
    expect(handleChange).toHaveBeenCalledWith({ keyword: '', status: undefined });
  });

  it('필터가 없으면 초기화 버튼을 보여주지 않는다', () => {
    // given & when
    render(<AdminProductFilterBar filters={{ keyword: '' }} onChange={vi.fn()} />);

    // then
    expect(screen.queryByRole('button', { name: '초기화' })).not.toBeInTheDocument();
  });
});
