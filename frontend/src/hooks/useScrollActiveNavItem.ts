import { useEffect, type RefObject } from 'react';

/** 가로로 넘치는 메뉴에서 현재 경로의 항목이 가려져 있으면 보이는 위치로 스크롤한다. */
export function useScrollActiveNavItem(navRef: RefObject<HTMLElement | null>, pathname: string) {
  useEffect(() => {
    const nav = navRef.current;
    if (!nav || nav.scrollWidth <= nav.clientWidth) {
      return;
    }
    nav
      .querySelector('[aria-current="page"]')
      ?.scrollIntoView({ inline: 'nearest', block: 'nearest' });
  }, [navRef, pathname]);
}
