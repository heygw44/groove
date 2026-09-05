import type { JSX } from 'react';
import { Navigate, useLocation } from 'react-router-dom';

import { Spinner } from '@/components/common/Spinner';
import { useAuthStore } from '@/store/authStore';

interface AdminRouteProps {
  children: JSX.Element;
}

export function AdminRoute({ children }: AdminRouteProps) {
  const accessToken = useAuthStore((s) => s.accessToken);
  const role = useAuthStore((s) => s.member?.role);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);
  const location = useLocation();

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

  if (role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }

  return children;
}
