import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { StarRatingDisplay, StarRatingInput } from '@/components/common/StarRating';

describe('StarRatingDisplay', () => {
  it('aria-label 에 소수 첫째 자리까지 점수를 담는다', () => {
    // given & when
    render(<StarRatingDisplay value={4.3} />);

    // then
    expect(screen.getByRole('img', { name: '별점 4.3점' })).toBeInTheDocument();
  });

  it.each([
    [0, '0px'],
    [2.5, '54px'],
    [4.0, '86px'],
    [5, '108px'],
    [6, '108px'],
    [-1, '0px'],
  ])('value=%s 이면 채움 폭을 %s 로 그린다', (value, expectedWidth) => {
    // given & when
    render(<StarRatingDisplay value={value} />);

    // then
    const fillLayer = screen.getByRole('img').querySelector('span[style]') as HTMLElement;
    expect(fillLayer.style.width).toBe(expectedWidth);
  });

  it('별 svg 가 부모 폭에 눌려 찌그러지지 않도록 shrink-0 을 갖는다', () => {
    // given & when
    render(<StarRatingDisplay value={3} />);

    // then
    const stars = screen.getByRole('img').querySelectorAll('svg');
    stars.forEach((star) => {
      expect(star).toHaveClass('shrink-0');
    });
  });

  it('NaN 값은 0점으로 취급한다', () => {
    // given & when
    render(<StarRatingDisplay value={NaN} />);

    // then
    expect(screen.getByRole('img', { name: '별점 0.0점' })).toBeInTheDocument();
    const fillLayer = screen.getByRole('img').querySelector('span[style]') as HTMLElement;
    expect(fillLayer.style.width).toBe('0px');
  });
});

describe('StarRatingInput', () => {
  it('라디오 5개를 렌더링한다', () => {
    // given & when
    render(<StarRatingInput value={0} onChange={vi.fn()} name="rating" />);

    // then
    expect(screen.getAllByRole('radio')).toHaveLength(5);
  });

  it('별을 클릭하면 onChange 에 해당 점수를 넘긴다', async () => {
    // given
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<StarRatingInput value={0} onChange={handleChange} name="rating" />);

    // when
    await user.click(screen.getAllByRole('radio')[2]);

    // then
    expect(handleChange).toHaveBeenCalledWith(3);
  });

  it('ArrowRight 로 다음 라디오를 선택하면 onChange 를 호출한다', async () => {
    // given
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<StarRatingInput value={2} onChange={handleChange} name="rating" />);

    // when
    screen.getAllByRole('radio')[1].focus();
    await user.keyboard('{ArrowRight}');

    // then
    expect(handleChange).toHaveBeenCalledWith(3);
  });
});
