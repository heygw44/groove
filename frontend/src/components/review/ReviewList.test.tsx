import type { UseQueryResult } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { ReviewList } from '@/components/review/ReviewList';
import { REVIEW_PAGE_SIZE } from '@/constants/review';
import { useReviews } from '@/hooks/queries/useReviews';
import type { PageResponse } from '@/types/api';
import type { Review, ReviewListParams } from '@/types/review';

vi.mock('@/hooks/queries/useReviews', () => ({
  useReviews: vi.fn(),
}));

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

const reviewsResult = (data: Omit<PageResponse<Review>, 'size'>) =>
  ({
    data: { ...data, size: REVIEW_PAGE_SIZE },
    isPending: false,
    isError: false,
    error: null,
    isPlaceholderData: false,
    refetch: vi.fn(),
  }) as unknown as UseQueryResult<PageResponse<Review>>;

const renderList = (page: number, onFallbackPage = vi.fn()) => {
  render(
    <ReviewList
      productId={1}
      sort="latest"
      page={page}
      onSortChange={vi.fn()}
      onPageChange={vi.fn()}
      onFallbackPage={onFallbackPage}
      onEdit={vi.fn()}
      onDelete={vi.fn()}
      renderEditForm={() => null}
    />,
  );
  return onFallbackPage;
};

/** 부모(ReviewSection)처럼 page 를 상태로 들고 fallback 요청을 그대로 반영한다. */
function StatefulReviewList({ initialPage }: { initialPage: number }) {
  const [page, setPage] = useState(initialPage);
  return (
    <ReviewList
      productId={1}
      sort="latest"
      page={page}
      onSortChange={vi.fn()}
      onPageChange={setPage}
      onFallbackPage={setPage}
      onEdit={vi.fn()}
      onDelete={vi.fn()}
      renderEditForm={() => null}
    />
  );
}

describe('ReviewList', () => {
  it('마지막 페이지의 유일한 리뷰가 지워져 페이지가 비면 남은 마지막 페이지로 이동해 목록과 페이지 이동을 보여준다', () => {
    // given: 3 페이지 중 마지막 페이지의 리뷰를 지워 2 페이지만 남았다.
    vi.mocked(useReviews).mockImplementation((_productId, params: ReviewListParams) =>
      params.page === 2
        ? reviewsResult({
            content: [],
            page: 2,
            totalElements: REVIEW_PAGE_SIZE + 1,
            totalPages: 2,
          })
        : reviewsResult({
            content: [review({ title: '남은 리뷰' })],
            page: params.page,
            totalElements: REVIEW_PAGE_SIZE + 1,
            totalPages: 2,
          }),
    );

    // when
    render(<StatefulReviewList initialPage={2} />);

    // then
    expect(screen.getByText('남은 리뷰')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: '페이지' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2' })).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByText('아직 작성된 리뷰가 없습니다')).not.toBeInTheDocument();
  });

  it('첫 페이지가 아닌 페이지가 비면 빈 상태 대신 로딩을 보여주며 마지막 유효 페이지를 요청한다', () => {
    // given
    vi.mocked(useReviews).mockReturnValue(
      reviewsResult({ content: [], page: 1, totalElements: 3, totalPages: 1 }),
    );

    // when
    const onFallbackPage = renderList(1);

    // then
    expect(onFallbackPage).toHaveBeenCalledWith(0);
    expect(screen.getByRole('status', { name: '로딩 중' })).toBeInTheDocument();
    expect(screen.queryByText('아직 작성된 리뷰가 없습니다')).not.toBeInTheDocument();
  });

  it('첫 페이지가 비면 빈 상태를 보여주고 페이지를 옮기지 않는다', () => {
    // given
    vi.mocked(useReviews).mockReturnValue(
      reviewsResult({ content: [], page: 0, totalElements: 0, totalPages: 0 }),
    );

    // when
    const onFallbackPage = renderList(0);

    // then
    expect(screen.getByText('아직 작성된 리뷰가 없습니다')).toBeInTheDocument();
    expect(onFallbackPage).not.toHaveBeenCalled();
  });
});
