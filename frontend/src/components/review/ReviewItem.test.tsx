import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ReviewItem } from '@/components/review/ReviewItem';
import type { Review } from '@/types/review';

const review = (overrides: Partial<Review> = {}): Review => ({
  id: 1,
  productId: 1,
  nickname: '레코드러버',
  rating: 4,
  title: '만족스러워요',
  content: '음질이 좋습니다.',
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
  mine: false,
  ...overrides,
});

describe('ReviewItem', () => {
  it('내 리뷰면 배지와 수정·삭제 버튼을 보여준다', () => {
    // given & when
    render(<ReviewItem review={review({ mine: true })} onEdit={vi.fn()} onDelete={vi.fn()} />);

    // then
    expect(screen.getByText('내 리뷰')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '수정' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '삭제' })).toBeInTheDocument();
  });

  it('남의 리뷰면 배지와 수정·삭제 버튼이 없다', () => {
    // given & when
    render(<ReviewItem review={review({ mine: false })} />);

    // then
    expect(screen.queryByText('내 리뷰')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '수정' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument();
  });

  it('수정된 리뷰는 (수정됨) 을 표시한다', () => {
    // given & when
    render(
      <ReviewItem
        review={review({ createdAt: '2026-09-01T00:00:00', updatedAt: '2026-09-02T00:00:00' })}
      />,
    );

    // then
    expect(screen.getByText(/\(수정됨\)/)).toBeInTheDocument();
  });

  it('생성 후 수정된 적이 없으면 (수정됨) 을 표시하지 않는다', () => {
    // given & when
    render(
      <ReviewItem
        review={review({ createdAt: '2026-09-01T00:00:00', updatedAt: '2026-09-01T00:00:00' })}
      />,
    );

    // then
    expect(screen.queryByText(/\(수정됨\)/)).not.toBeInTheDocument();
  });
});
