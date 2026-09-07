import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Badge } from '@/components/common/Badge';

describe('Badge', () => {
  it('variant 를 생략하면 neutral 배경과 border-line-strong 테두리를 쓴다', () => {
    // given & when
    render(<Badge>기본</Badge>);

    // then
    expect(screen.getByText('기본')).toHaveClass('bg-surface-muted', 'border-line-strong');
  });

  it('accent variant 는 accent-soft 배경과 accent 계열 테두리를 쓴다', () => {
    // given & when
    render(<Badge variant="accent">추천</Badge>);

    // then
    expect(screen.getByText('추천')).toHaveClass('bg-accent-soft', 'border-accent/85');
  });

  it('danger variant 는 danger-soft 배경과 danger 계열 테두리를 쓴다', () => {
    // given & when
    render(<Badge variant="danger">품절</Badge>);

    // then
    expect(screen.getByText('품절')).toHaveClass('bg-danger-soft', 'border-danger/70');
  });

  it('success variant 는 success-soft 배경과 success 계열 테두리를 쓴다', () => {
    // given & when
    render(<Badge variant="success">완료</Badge>);

    // then
    expect(screen.getByText('완료')).toHaveClass('bg-success-soft', 'border-success/80');
  });

  it('variant 와 무관하게 border 유틸을 항상 포함한다', () => {
    // given & when
    render(<Badge variant="neutral">배지</Badge>);

    // then
    expect(screen.getByText('배지')).toHaveClass('border');
  });
});
