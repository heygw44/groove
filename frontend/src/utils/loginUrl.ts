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

/**
 * 로그인 화면의 redirect 를 회원가입 화면으로 그대로 넘긴다. 최종 소비자인 로그인 화면이
 * getSafeRedirect 로 거르므로 여기서는 검사하지 않는다.
 */
export const buildSignupUrl = (redirect?: string | null) => {
  const params = new URLSearchParams();
  if (redirect) {
    params.set('redirect', redirect);
  }
  const query = params.toString();
  return query ? `/signup?${query}` : '/signup';
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
