import { useEffect } from 'react';

import { queryClient } from '@/lib/queryClient';
import { useAuthStore } from '@/store/authStore';
import { subscribeAuthMessage } from '@/utils/authChannel';
import { buildLoginUrl, currentPath } from '@/utils/loginUrl';

/**
 * 다른 탭의 로그아웃을 이 탭에도 반영한다. 로그인 상태가 아니었던 탭(게스트)은
 * 지울 세션이 없으므로 무시하고, 여기서 받은 메시지를 되쏘지 않는다.
 */
export const useAuthChannelSync = () => {
  useEffect(
    () =>
      subscribeAuthMessage((message) => {
        if (message.type !== 'logout') {
          return;
        }
        if (useAuthStore.getState().accessToken === null) {
          return;
        }

        useAuthStore.getState().clearAuth();
        queryClient.clear();

        if (window.location.pathname === '/login') {
          return;
        }
        window.location.replace(buildLoginUrl({ reason: message.reason, redirect: currentPath() }));
      }),
    [],
  );
};
