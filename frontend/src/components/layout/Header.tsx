import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { Drawer } from '@/components/common/Drawer';
import { useLogout } from '@/hooks/mutations/useAuthMutations';
import { useCart } from '@/hooks/queries/useCart';
import { useAuthStore } from '@/store/authStore';

interface NavItem {
  to: string;
  label: string;
  badge?: number;
}

export function Header() {
  const member = useAuthStore((s) => s.member);
  const isLoggedIn = useAuthStore((s) => Boolean(s.accessToken));
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);
  const logoutMutation = useLogout();
  // enabled 가 로그인 여부로 이미 걸려 있어 비로그인일 때는 호출만 되고 요청은 나가지 않는다.
  const { data: cart } = useCart();
  const cartItemCount = cart?.items.length ?? 0;

  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const { pathname } = useLocation();

  /*
   * 메뉴에서 링크를 누르면 화면만 바뀌고 드로어가 남아 있으면 안 된다.
   * 렌더 중 이전 값과 비교해 setState 하는 건 React 공식 패턴 - effect 로 하면
   * 드로어가 한 프레임 깜빡이고 set-state-in-effect 경고도 뜬다.
   */
  const [prevPathname, setPrevPathname] = useState(pathname);
  if (pathname !== prevPathname) {
    setPrevPathname(pathname);
    setIsMenuOpen(false);
  }

  const navItems: NavItem[] = [
    { to: '/products', label: '상품' },
    { to: '/limited-drops', label: '한정반' },
    // 장바구니는 비로그인에도 노출한다. 눌렀을 때 PrivateRoute 가 로그인으로 보낸다.
    {
      to: '/cart',
      label: '장바구니',
      badge: isLoggedIn && cartItemCount > 0 ? cartItemCount : undefined,
    },
    ...(member?.role === 'ADMIN' ? [{ to: '/admin', label: '관리자' }] : []),
  ];

  const navLinkProps = (item: NavItem) => ({
    to: item.to,
    'aria-label': item.badge ? `${item.label} ${item.badge}개` : undefined,
  });

  const countBadge = (count: number) => (
    <span className="rounded-full bg-accent px-1.5 text-[11px] text-accent-content">{count}</span>
  );

  return (
    <header className="border-b border-line bg-surface">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-6 px-4 py-3">
        <Link to="/" className="text-lg font-bold tracking-[0.14em] text-content">
          GROOVE
        </Link>

        <nav className="hidden items-center gap-5 text-sm text-content-muted md:flex">
          {navItems.map((item) => (
            <Link
              key={item.to}
              {...navLinkProps(item)}
              className="text-content-muted hover:text-content"
            >
              {item.label}
              {item.badge !== undefined && <span className="ml-1">{countBadge(item.badge)}</span>}
            </Link>
          ))}
        </nav>

        <div className="hidden items-center gap-3 text-sm md:flex">
          {/* 부팅 중에는 로그인 여부를 모른다. 자리만 잡아 레이아웃이 튀지 않게 한다. */}
          {isBootstrapping ? (
            <div className="h-8 w-40 animate-pulse rounded-md bg-surface-muted" />
          ) : isLoggedIn ? (
            <>
              <Link to="/mypage" className="text-content-muted hover:text-content">
                마이페이지
              </Link>
              <span className="text-content-muted">
                <b className="font-medium text-content">{member?.nickname}</b>님
              </span>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => logoutMutation.mutate()}
                disabled={logoutMutation.isPending}
              >
                로그아웃
              </Button>
            </>
          ) : (
            <>
              <Link to="/login" className="text-content-muted hover:text-content">
                로그인
              </Link>
              <Link to="/signup" className="text-content-muted hover:text-content">
                회원가입
              </Link>
            </>
          )}
        </div>

        <button
          type="button"
          aria-label="메뉴 열기"
          aria-expanded={isMenuOpen}
          onClick={() => setIsMenuOpen(true)}
          className="-mr-1 rounded-md p-1.5 text-content-muted hover:bg-surface-muted hover:text-content md:hidden"
        >
          <svg
            width="22"
            height="22"
            viewBox="0 0 22 22"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.7"
            strokeLinecap="round"
            aria-hidden
          >
            <path d="M4 6.5h14" />
            <path d="M4 11h14" />
            <path d="M4 15.5h14" />
          </svg>
        </button>
      </div>

      <Drawer open={isMenuOpen} onClose={() => setIsMenuOpen(false)} title="메뉴" side="right">
        <nav className="flex flex-col">
          {navItems.map((item) => (
            <Link
              key={item.to}
              {...navLinkProps(item)}
              className="flex items-center gap-2 py-2.5 text-sm text-content"
            >
              {item.label}
              {item.badge !== undefined && countBadge(item.badge)}
            </Link>
          ))}
        </nav>

        <div className="mt-2 flex flex-col gap-2 border-t border-line pt-4 text-sm">
          {isBootstrapping ? (
            <div className="h-9 w-full animate-pulse rounded-md bg-surface-muted" />
          ) : isLoggedIn ? (
            <>
              <p className="text-content-muted">
                <b className="font-medium text-content">{member?.nickname}</b>님
              </p>
              <Link to="/mypage" className="py-2.5 text-content">
                마이페이지
              </Link>
              <Button
                variant="secondary"
                onClick={() => logoutMutation.mutate()}
                disabled={logoutMutation.isPending}
              >
                로그아웃
              </Button>
            </>
          ) : (
            <>
              <Link to="/login" className="py-2.5 text-content">
                로그인
              </Link>
              <Link to="/signup" className="py-2.5 text-content">
                회원가입
              </Link>
            </>
          )}
        </div>
      </Drawer>
    </header>
  );
}
