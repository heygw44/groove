import type { JSX } from 'react';
import { Navigate, useSearchParams } from 'react-router-dom';

import { RouteFallback } from '@/components/common/RouteFallback';
import { useAuthStore } from '@/store/authStore';
import { getSafeRedirect } from '@/utils/loginUrl';

interface GuestRouteProps {
  children: JSX.Element;
}

/** 로그인·회원가입처럼 비로그인 사용자만 볼 화면. 이미 로그인했으면 redirect 대상으로 보낸다. */
export function GuestRoute({ children }: GuestRouteProps) {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);
  const [searchParams] = useSearchParams();

  if (isBootstrapping) {
    return <RouteFallback />;
  }

  if (accessToken) {
    return <Navigate to={getSafeRedirect(searchParams.get('redirect'))} replace />;
  }

  return children;
}
