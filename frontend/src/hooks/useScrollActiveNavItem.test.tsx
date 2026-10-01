import { render } from '@testing-library/react';
import { useRef } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useScrollActiveNavItem } from '@/hooks/useScrollActiveNavItem';

const scrollIntoView = vi.fn();

function TestNav({ pathname }: { pathname: string }) {
  const navRef = useRef<HTMLElement>(null);
  useScrollActiveNavItem(navRef, pathname);
  return (
    <nav ref={navRef} data-testid="nav">
      <a href="/a">A</a>
      <a href="/b" aria-current="page">
        B
      </a>
    </nav>
  );
}

const setOverflow = (scrollWidth: number, clientWidth: number) => {
  Object.defineProperty(HTMLElement.prototype, 'scrollWidth', {
    configurable: true,
    get: () => scrollWidth,
  });
  Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
    configurable: true,
    get: () => clientWidth,
  });
};

describe('useScrollActiveNavItem', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = scrollIntoView;
  });

  afterEach(() => {
    scrollIntoView.mockClear();
    Reflect.deleteProperty(HTMLElement.prototype, 'scrollWidth');
    Reflect.deleteProperty(HTMLElement.prototype, 'clientWidth');
  });

  it('메뉴가 넘치면 현재 항목을 보이는 위치로 스크롤한다', () => {
    // given
    setOverflow(600, 300);

    // when
    render(<TestNav pathname="/b" />);

    // then
    expect(scrollIntoView).toHaveBeenCalledWith({ inline: 'nearest', block: 'nearest' });
  });

  it('메뉴가 넘치지 않으면 스크롤하지 않는다', () => {
    // given
    setOverflow(300, 300);

    // when
    render(<TestNav pathname="/b" />);

    // then
    expect(scrollIntoView).not.toHaveBeenCalled();
  });
});
