import type { JSX } from 'react';
import { Navigate } from 'react-router-dom';

import { useAuthStore } from '@/store/authStore';

interface AdminRouteProps {
  children: JSX.Element;
}

export function AdminRoute({ children }: AdminRouteProps) {
  const accessToken = useAuthStore((s) => s.accessToken);
  const role = useAuthStore((s) => s.member?.role);

  if (!accessToken) {
    return <Navigate to="/login" replace />;
  }
  if (role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }

  return children;
}
