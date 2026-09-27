import { ADMIN_IDLE_TIMEOUT_MS, IDLE_WARNING_BEFORE_MS } from '@/constants/authSession';

export type IdlePhase = 'active' | 'warning' | 'expired';

/*
 * 경과 시간은 타이머 누적이 아니라 시각 차이로 잰다. 백그라운드 탭은 타이머가
 * 분 단위로 늦춰지고 절전 중에는 아예 멈추기 때문이다.
 */
interface IdleState {
  phase: IdlePhase;
  remainingMs: number;
}

export const getIdlePhase = (now: number, lastActivity: number): IdleState => {
  const remainingMs = Math.max(0, ADMIN_IDLE_TIMEOUT_MS - (now - lastActivity));
  if (remainingMs === 0) {
    return { phase: 'expired', remainingMs };
  }
  if (remainingMs <= IDLE_WARNING_BEFORE_MS) {
    return { phase: 'warning', remainingMs };
  }
  return { phase: 'active', remainingMs };
};
