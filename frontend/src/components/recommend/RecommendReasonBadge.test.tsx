import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { RecommendReasonBadges } from '@/components/recommend/RecommendReasonBadge';
import { RECOMMEND_REASON_LABELS } from '@/constants/recommendReasons';
import type { RecommendReason } from '@/types/recommend';

describe('RecommendReasonBadges', () => {
  it('빈 배열이어도 카드 정렬을 위해 자리는 유지한다', () => {
    const { container } = render(<RecommendReasonBadges reasons={[]} />);

    expect(container.firstChild).toHaveClass('min-h-4');
    expect(container.firstChild).toBeEmptyDOMElement();
  });

  it('사유를 가운뎃점으로 이어 붙인다', () => {
    // given
    const reasons: RecommendReason[] = ['SAME_ARTIST', 'SAME_GENRE'];

    // when
    render(<RecommendReasonBadges reasons={reasons} />);

    // then
    expect(screen.getByText('같은 아티스트 · 같은 장르')).toBeInTheDocument();
  });

  it('3개를 넘기면 최대 2개만 표기한다', () => {
    // given
    const reasons: RecommendReason[] = ['SAME_ARTIST', 'SAME_GENRE', 'SAME_LABEL'];

    // when
    render(<RecommendReasonBadges reasons={reasons} />);

    // then
    expect(screen.getByText('같은 아티스트 · 같은 장르')).toBeInTheDocument();
    expect(
      screen.queryByText(RECOMMEND_REASON_LABELS.SAME_LABEL, { exact: false }),
    ).not.toBeInTheDocument();
  });
});
