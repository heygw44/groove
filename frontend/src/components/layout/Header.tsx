import { Link } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { useLogout } from '@/hooks/mutations/useAuthMutations';
import { useAuthStore } from '@/store/authStore';

export function Header() {
  const member = useAuthStore((s) => s.member);
  const isLoggedIn = useAuthStore((s) => Boolean(s.accessToken));
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);
  const logoutMutation = useLogout();

  return (
    <header className="border-b border-line bg-surface">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-6 px-4 py-3">
        <Link to="/" className="text-lg font-bold tracking-[0.14em] text-content">
          GROOVE
        </Link>

        <nav className="flex items-center gap-5 text-sm text-content-muted">
          <Link to="/products" className="text-content-muted hover:text-content">
            상품
          </Link>
          <Link to="/limited-drops" className="text-content-muted hover:text-content">
            한정반
          </Link>
          {/* 장바구니는 비로그인에도 노출한다. 눌렀을 때 PrivateRoute 가 로그인으로 보낸다. */}
          <Link to="/cart" className="text-content-muted hover:text-content">
            장바구니
          </Link>
          {member?.role === 'ADMIN' && (
            <Link to="/admin" className="text-content-muted hover:text-content">
              관리자
            </Link>
          )}
        </nav>

        <div className="flex items-center gap-3 text-sm">
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
      </div>
    </header>
  );
}
