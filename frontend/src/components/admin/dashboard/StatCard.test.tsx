import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { StatCard } from '@/components/admin/dashboard/StatCard';

describe('StatCard', () => {
  it('라벨과 값을 렌더링한다', () => {
    // given & when
    render(<StatCard label="오늘 매출" value="1,200,000원" />);

    // then
    expect(screen.getByText('오늘 매출')).toBeInTheDocument();
    expect(screen.getByText('1,200,000원')).toBeInTheDocument();
  });

  it('hint 가 있으면 함께 보여준다', () => {
    // given & when
    render(<StatCard label="결제 대기" value="3건" hint="확인이 필요합니다" />);

    // then
    expect(screen.getByText('확인이 필요합니다')).toBeInTheDocument();
  });

  it('hint 가 없으면 렌더링하지 않는다', () => {
    // given & when
    const { container } = render(<StatCard label="오늘 주문" value="10건" />);

    // then
    expect(container.querySelector('.text-content-subtle')).not.toBeInTheDocument();
  });

  it('to 가 있으면 링크로 렌더링한다', () => {
    // given & when
    render(
      <MemoryRouter>
        <StatCard label="결제 대기" value="3건" to="/admin/orders?status=PENDING" />
      </MemoryRouter>,
    );

    // then
    expect(screen.getByRole('link')).toHaveAttribute('href', '/admin/orders?status=PENDING');
  });

  it('to 가 없으면 링크로 렌더링하지 않는다', () => {
    // given & when
    render(<StatCard label="오늘 주문" value="10건" />);

    // then
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
