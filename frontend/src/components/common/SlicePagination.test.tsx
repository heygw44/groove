import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { SlicePagination } from '@/components/common/SlicePagination';

describe('SlicePagination', () => {
  it('첫 페이지면 이전 버튼이 비활성이고 다음 버튼은 활성이다', () => {
    // given & when
    render(<SlicePagination page={0} hasNext onChange={vi.fn()} />);

    // then
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeEnabled();
  });

  it('다음 페이지가 없으면 다음 버튼이 비활성이다', () => {
    // given & when
    render(<SlicePagination page={2} hasNext={false} onChange={vi.fn()} />);

    // then
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeEnabled();
  });

  it('이전·다음을 누르면 앞뒤 페이지 번호로 onChange 를 호출한다', async () => {
    // given
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<SlicePagination page={2} hasNext onChange={onChange} />);

    // when
    await user.click(screen.getByRole('button', { name: '이전 페이지' }));
    await user.click(screen.getByRole('button', { name: '다음 페이지' }));

    // then
    expect(onChange).toHaveBeenNthCalledWith(1, 1);
    expect(onChange).toHaveBeenNthCalledWith(2, 3);
  });

  it('disabled 면 두 버튼이 모두 비활성이다', () => {
    // given & when
    render(<SlicePagination page={2} hasNext disabled onChange={vi.fn()} />);

    // then
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeDisabled();
  });

  it('첫 페이지이고 다음도 없으면 렌더하지 않는다', () => {
    // given & when
    const { container } = render(<SlicePagination page={0} hasNext={false} onChange={vi.fn()} />);

    // then
    expect(container).toBeEmptyDOMElement();
  });
});
