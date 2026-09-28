import { useEffect, useState } from 'react';

import { getServerNowMs, subscribeServerTimeSync } from '@/utils/serverTime';

/**
 * 서버 기준 현재 시각(ms)을 tickMs 간격으로 갱신한다. Date 객체 대신 ms 숫자를
 * 반환하는 이유는 카운트다운 등에서 매번 비교·차이 계산만 하면 되기 때문이다.
 * enabled 가 false 면 시각 동기화와 주기 렌더를 하지 않는다(조건부로만 시각이 필요한 화면용).
 */
export function useServerNow(tickMs = 1000, enabled = true): number {
  const [nowMs, setNowMs] = useState(() => getServerNowMs());

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    const unsubscribe = subscribeServerTimeSync();
    const interval = setInterval(() => setNowMs(getServerNowMs()), tickMs);
    return () => {
      clearInterval(interval);
      unsubscribe();
    };
  }, [tickMs, enabled]);

  return nowMs;
}
