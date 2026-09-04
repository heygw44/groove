import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ReviewRatingDistribution } from '@/components/review/ReviewRatingDistribution';

describe('ReviewRatingDistribution', () => {
  it('별점별 비율을 백분율로 보여준다', () => {
    // given & when
    render(
      <ReviewRatingDistribution
        distribution={{ '1': 0, '2': 0, '3': 1, '4': 0, '5': 2 }}
      />,
    );

    // then
    expect(screen.getByText('67%')).toBeInTheDocument();
    expect(screen.getByText('33%')).toBeInTheDocument();
  });

  it('리뷰가 하나도 없으면 아무것도 렌더링하지 않는다', () => {
    // given & when
    const { container } = render(
      <ReviewRatingDistribution
        distribution={{ '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 }}
      />,
    );

    // then
    expect(container.firstChild).toBeNull();
  });
});
