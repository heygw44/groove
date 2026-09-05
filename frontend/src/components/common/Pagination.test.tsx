import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { Pagination } from '@/components/common/Pagination';

const pageButtonLabels = () =>
  screen
    .getAllByRole('button')
    .map((button) => button.textContent)
    .filter((text) => text !== '‹' && text !== '›');

describe('Pagination', () => {
  it('페이지가 하나뿐이면 아무것도 렌더하지 않는다', () => {
    // given & when
    const { container } = render(<Pagination page={0} totalPages={1} onChange={vi.fn()} />);

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it('현재 페이지 주변으로 모든 페이지가 이어지면 생략 표시가 없다', () => {
    // given & when
    render(<Pagination page={1} totalPages={3} onChange={vi.fn()} />);

    // then
    expect(pageButtonLabels()).toEqual(['1', '2', '3']);
    expect(screen.queryByText('…')).not.toBeInTheDocument();
  });

  it('건너뛰는 페이지가 하나뿐이면 접지 않고 번호로 보여준다', () => {
    // given & when
    render(<Pagination page={0} totalPages={4} onChange={vi.fn()} />);

    // then
    expect(pageButtonLabels()).toEqual(['1', '2', '3', '4']);
    expect(screen.queryByText('…')).not.toBeInTheDocument();
  });

  it('건너뛰는 페이지가 둘 이상이면 생략 표시로 접는다', () => {
    // given & when
    render(<Pagination page={0} totalPages={5} onChange={vi.fn()} />);

    // then
    expect(pageButtonLabels()).toEqual(['1', '2', '5']);
    expect(screen.getAllByText('…')).toHaveLength(1);
  });

  it('현재 페이지가 중간이면 양쪽에 생략 표시가 들어간다', () => {
    // given & when
    render(<Pagination page={9} totalPages={20} onChange={vi.fn()} />);

    // then
    expect(pageButtonLabels()).toEqual(['1', '9', '10', '11', '20']);
    expect(screen.getAllByText('…')).toHaveLength(2);
  });

  it('현재 페이지가 앞쪽이면 뒤쪽에만 생략 표시가 들어간다', () => {
    // given & when
    render(<Pagination page={0} totalPages={20} onChange={vi.fn()} />);

    // then
    expect(pageButtonLabels()).toEqual(['1', '2', '20']);
    expect(screen.getAllByText('…')).toHaveLength(1);
  });

  it('siblingCount 를 늘리면 주변 페이지를 더 보여준다', () => {
    // given & when
    render(<Pagination page={9} totalPages={20} onChange={vi.fn()} siblingCount={2} />);

    // then
    expect(pageButtonLabels()).toEqual(['1', '8', '9', '10', '11', '12', '20']);
  });

  it('현재 페이지 버튼에 aria-current 를 붙인다', () => {
    // given & when
    render(<Pagination page={2} totalPages={5} onChange={vi.fn()} />);

    // then
    expect(screen.getByRole('button', { name: '3' })).toHaveAttribute('aria-current', 'page');
  });

  it('페이지 번호를 누르면 0-base 값으로 변환해 전달한다', async () => {
    // given
    const onChange = vi.fn();
    render(<Pagination page={2} totalPages={5} onChange={onChange} />);

    // when
    await userEvent.click(screen.getByRole('button', { name: '4' }));

    // then
    expect(onChange).toHaveBeenCalledWith(3);
  });

  it('첫 페이지에서는 이전 버튼이, 마지막 페이지에서는 다음 버튼이 비활성이다', () => {
    // given & when
    const { unmount } = render(<Pagination page={0} totalPages={5} onChange={vi.fn()} />);

    // then
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeEnabled();

    // given & when
    unmount();
    render(<Pagination page={4} totalPages={5} onChange={vi.fn()} />);

    // then
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeDisabled();
  });
});
