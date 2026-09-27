import type { LoginReason } from '@/constants/authSession';

interface LoginUrlOptions {
  reason?: LoginReason;
  redirect?: string;
}

export const buildLoginUrl = ({ reason, redirect }: LoginUrlOptions = {}) => {
  const params = new URLSearchParams();
  if (reason) {
    params.set('reason', reason);
  }
  if (redirect) {
    params.set('redirect', redirect);
  }
  const query = params.toString();
  return query ? `/login?${query}` : '/login';
};

export const currentPath = () => `${window.location.pathname}${window.location.search}`;
