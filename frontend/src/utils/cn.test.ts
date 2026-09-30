import { describe, expect, it } from 'vitest';

import { cn } from '@/utils/cn';

const INPUT_BASE =
  'h-10 w-full rounded-md border px-3 text-sm text-content placeholder:text-content-subtle ' +
  'focus:outline-none focus:ring-3 disabled:cursor-not-allowed disabled:opacity-60';
const INPUT_STATE =
  'border-line-strong bg-surface-sunken focus:border-content focus:bg-surface focus:ring-content/10';

const tokensOf = (value: string) => value.split(' ');

describe('cn', () => {
  it('falsy 값은 버리고 나머지를 공백으로 잇는다', () => {
    // given & when
    const result = cn('a', false, null, undefined, 'b');

    // then
    expect(result).toBe('a b');
  });

  it('뒤에 오는 너비가 w-full 을 대체한다', () => {
    // given & when
    const result = cn('w-full', 'w-36');

    // then
    expect(tokensOf(result)).toEqual(['w-36']);
  });

  it('높이와 글자 크기는 뒤쪽 값만 남는다', () => {
    // given & when
    const result = cn('h-10 text-sm', 'h-8 text-xs');

    // then
    expect(tokensOf(result).sort()).toEqual(['h-8', 'text-xs']);
  });

  it('커스텀 색 토큰 text-content 와 글자 크기 text-xs 는 서로 지우지 않는다', () => {
    // given & when
    const result = cn('text-content', 'text-xs');

    // then
    expect(tokensOf(result).sort()).toEqual(['text-content', 'text-xs']);
  });

  it('프로젝트 커스텀 토큰은 지워지지 않는다', () => {
    // given & when
    const result = cn('bg-surface-sunken border-line-strong focus:ring-content/10 focus:ring-3');

    // then
    expect(tokensOf(result).sort()).toEqual([
      'bg-surface-sunken',
      'border-line-strong',
      'focus:ring-3',
      'focus:ring-content/10',
    ]);
  });

  it('Input 기본 클래스에 w-36 을 합치면 w-full 만 빠지고 나머지는 유지된다', () => {
    // given & when
    const result = cn(INPUT_BASE, INPUT_STATE, 'w-36');

    // then
    const tokens = tokensOf(result);
    expect(tokens).toContain('w-36');
    expect(tokens).not.toContain('w-full');
    [
      'h-10',
      'text-sm',
      'text-content',
      'placeholder:text-content-subtle',
      'focus:ring-3',
      'border-line-strong',
      'bg-surface-sunken',
      'focus:border-content',
      'focus:bg-surface',
      'focus:ring-content/10',
    ].forEach((token) => expect(tokens).toContain(token));
  });
});
