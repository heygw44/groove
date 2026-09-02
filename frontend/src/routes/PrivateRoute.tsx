import type { JSX } from 'react';
import { Navigate, useLocation } from 'react-router-dom';

import { Spinner } from '@/components/common/Spinner';
import { useAuthStore } from '@/store/authStore';

interface PrivateRouteProps {
  children: JSX.Element;
}

export function PrivateRoute({ children }: PrivateRouteProps) {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);
  const location = useLocation();

  /* 재발급이 끝나기 전에는 로그인 여부를 모른다. 먼저 튕기면 새로고침마다 로그아웃이다. */
  if (isBootstrapping) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  if (!accessToken) {
    const redirect = encodeURIComponent(`${location.pathname}${location.search}`);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }

  return children;
}
