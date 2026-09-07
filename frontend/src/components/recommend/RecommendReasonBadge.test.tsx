import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { RecommendReasonBadge, RecommendReasonBadges } from '@/components/recommend/RecommendReasonBadge';
import { RECOMMEND_REASON_LABELS } from '@/constants/recommendReasons';
import type { RecommendReason } from '@/types/recommend';

describe('RecommendReasonBadge', () => {
  it('reason 에 매핑된 라벨을 렌더한다', () => {
    render(<RecommendReasonBadge reason="SAME_LABEL" />);

    expect(screen.getByText(RECOMMEND_REASON_LABELS.SAME_LABEL)).toBeInTheDocument();
  });
});

describe('RecommendReasonBadges', () => {
  it('빈 배열이어도 카드 정렬을 위해 자리는 유지한다', () => {
    const { container } = render(<RecommendReasonBadges reasons={[]} />);

    expect(container.firstChild).not.toBeNull();
    expect(container.firstChild).toBeEmptyDOMElement();
  });

  it('3개를 넘기면 최대 2개만 렌더한다', () => {
    const reasons: RecommendReason[] = ['SAME_ARTIST', 'SAME_GENRE', 'SAME_LABEL'];

    render(<RecommendReasonBadges reasons={reasons} />);

    expect(screen.getByText(RECOMMEND_REASON_LABELS.SAME_ARTIST)).toBeInTheDocument();
    expect(screen.getByText(RECOMMEND_REASON_LABELS.SAME_GENRE)).toBeInTheDocument();
    expect(screen.queryByText(RECOMMEND_REASON_LABELS.SAME_LABEL)).not.toBeInTheDocument();
  });
});
