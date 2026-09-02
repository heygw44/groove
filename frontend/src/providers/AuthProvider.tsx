import { useEffect, type ReactNode } from 'react';

import { reissue } from '@/api/auth';
import { getMe } from '@/api/member';
import { memberKeys } from '@/hooks/queries/queryKeys';
import { queryClient } from '@/lib/queryClient';
import { useAuthStore } from '@/store/authStore';

/*
 * Access Token 은 메모리에만 두므로 새로고침하면 사라진다. 부팅 때 리프레시
 * 쿠키로 한 번 재발급해 세션을 되살린다. 모듈 레벨 프로미스로 감싼 이유는
 * StrictMode 의 이중 마운트에도 요청이 한 번만 나가게 하기 위해서다.
 */
let bootstrapPromise: Promise<void> | null = null;

const bootstrap = async () => {
  const { setAuth, clearAuth, setBootstrapped } = useAuthStore.getState();

  try {
    const { accessToken } = await reissue();
    /*
     * 탈퇴해도 서버가 리프레시 쿠키를 만료시키지 않아 재발급은 성공한다.
     * 내 정보 조회가 403 을 주는 지점에서 비로소 걸러진다.
     */
    const member = await getMe({ headers: { Authorization: `Bearer ${accessToken}` } });
    setAuth(accessToken, member);
    queryClient.setQueryData(memberKeys.me, member);
  } catch {
    /* 쿠키가 없거나 만료된 정상적인 비로그인 상태다. 조용히 넘어간다. */
    clearAuth();
  } finally {
    setBootstrapped();
  }
};

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  useEffect(() => {
    bootstrapPromise ??= bootstrap();
  }, []);

  return <>{children}</>;
}
