import { useCallback, useEffect, useRef, useState } from 'react';

import { logout } from '@/api/auth';
import {
  ACTIVITY_EVENTS,
  ACTIVITY_THROTTLE_MS,
  IDLE_CHECK_INTERVAL_MS,
} from '@/constants/authSession';
import { queryClient } from '@/lib/queryClient';
import { useAuthStore } from '@/store/authStore';
import { postAuthMessage, subscribeAuthMessage } from '@/utils/authChannel';
import { getIdlePhase } from '@/utils/idlePhase';
import { buildLoginUrl, currentPath } from '@/utils/loginUrl';

interface UseAdminIdleLogoutResult {
  warningOpen: boolean;
  remainingSeconds: number;
  extend: () => void;
}

/** 관리자 콘솔에서 일정 시간 활동이 없으면 경고 후 자동 로그아웃시킨다. */
export function useAdminIdleLogout(): UseAdminIdleLogoutResult {
  const [warningOpen, setWarningOpen] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState(0);

  /* Date.now() 는 impure 라 렌더 중에 못 읽는다 - 마운트 이펙트에서 채운다. */
  const lastActivityRef = useRef(0);
  const lastRecordedRef = useRef(0);
  const expiredRef = useRef(false);

  useEffect(() => {
    const now = Date.now();
    lastActivityRef.current = now;
    lastRecordedRef.current = now;
  }, []);

  const expire = useCallback(async () => {
    if (expiredRef.current) {
      return;
    }
    expiredRef.current = true;
    setWarningOpen(false);
    postAuthMessage({ type: 'logout', reason: 'idle' });
    const redirect = currentPath();
    /* 서버 세션 정리 실패는 무시한다 - 어차피 클라이언트는 로그아웃 상태로 이동한다. */
    await logout().catch(() => {});
    useAuthStore.getState().clearAuth();
    queryClient.clear();
    window.location.replace(buildLoginUrl({ reason: 'idle', redirect }));
  }, []);

  /* 활동 기록은 이벤트 핸들러와 extend() 가 공유한다. bypassThrottle 은 명시적 사용자 선택(계속 사용)에만 켠다. */
  const recordActivity = useCallback(
    (bypassThrottle: boolean) => {
      if (expiredRef.current) {
        return;
      }
      const now = Date.now();
      /* 절전·백그라운드 탭에서 깨어난 직후일 수 있으므로, 갱신 전 현재 상태를 먼저 본다. */
      const { phase } = getIdlePhase(now, lastActivityRef.current);
      if (phase === 'expired') {
        void expire();
        return;
      }
      if (!bypassThrottle && now - lastRecordedRef.current < ACTIVITY_THROTTLE_MS) {
        return;
      }
      lastActivityRef.current = now;
      lastRecordedRef.current = now;
      postAuthMessage({ type: 'activity', at: now });
      setWarningOpen(false);
    },
    [expire],
  );

  useEffect(() => {
    const interval = setInterval(() => {
      if (expiredRef.current) {
        return;
      }
      const { phase, remainingMs } = getIdlePhase(Date.now(), lastActivityRef.current);
      if (phase === 'expired') {
        void expire();
        return;
      }
      if (phase === 'warning') {
        const seconds = Math.ceil(remainingMs / 1000);
        setWarningOpen(true);
        setRemainingSeconds((prev) => (prev === seconds ? prev : seconds));
      } else {
        setWarningOpen(false);
      }
    }, IDLE_CHECK_INTERVAL_MS);

    return () => clearInterval(interval);
  }, [expire]);

  useEffect(() => {
    const handleActivity = () => recordActivity(false);
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        recordActivity(false);
      }
    };

    ACTIVITY_EVENTS.forEach((eventName) => {
      window.addEventListener(eventName, handleActivity, { passive: true });
    });
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      ACTIVITY_EVENTS.forEach((eventName) => {
        window.removeEventListener(eventName, handleActivity);
      });
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [recordActivity]);

  useEffect(
    () =>
      subscribeAuthMessage((message) => {
        if (message.type === 'activity') {
          lastActivityRef.current = Math.max(lastActivityRef.current, message.at);
          const { phase } = getIdlePhase(Date.now(), lastActivityRef.current);
          if (phase === 'active') {
            setWarningOpen(false);
          }
          return;
        }
        /* 다른 탭이 세션을 끝냈다 - 이동은 useAuthChannelSync 가 처리하므로 여기서는 중복 로그아웃만 막는다. */
        expiredRef.current = true;
      }),
    [],
  );

  const extend = useCallback(() => recordActivity(true), [recordActivity]);

  return { warningOpen, remainingSeconds, extend };
}
