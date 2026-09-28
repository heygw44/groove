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

/**
 * 쿼리로 받은 redirect 를 앱 내부 경로로만 좁힌다. '//host' 나 '/\\host' 는 브라우저가
 * 외부 주소로 해석하므로 막는다.
 */
export const getSafeRedirect = (raw: string | null): string => {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\')) {
    return '/';
  }
  return raw;
};
