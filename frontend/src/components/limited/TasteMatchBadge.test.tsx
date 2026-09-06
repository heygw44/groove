import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { TasteMatchBadge } from '@/components/limited/TasteMatchBadge';

describe('TasteMatchBadge', () => {
  it('tasteMatch 가 matched 이면 배지를 렌더링한다', () => {
    // given & when
    render(<TasteMatchBadge tasteMatch={{ matched: true, reasons: ['TASTE_GENRE'] }} />);

    // then
    expect(screen.getByText('당신 취향 드롭')).toBeInTheDocument();
  });

  it('tasteMatch 가 없으면 아무것도 렌더링하지 않는다', () => {
    // given & when
    const { container } = render(<TasteMatchBadge />);

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it('tasteMatch.matched 가 false 면 아무것도 렌더링하지 않는다', () => {
    // given & when
    const { container } = render(<TasteMatchBadge tasteMatch={{ matched: false, reasons: [] }} />);

    // then
    expect(container).toBeEmptyDOMElement();
  });
});
