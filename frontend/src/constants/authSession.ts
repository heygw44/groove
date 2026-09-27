/** 관리자 콘솔 유휴 로그아웃 기준. 서버 쪽 상한은 관리자 12시간 절대 만료와 30분 access TTL 이다. */
export const ADMIN_IDLE_TIMEOUT_MS = 15 * 60 * 1000;

/** 만료 이만큼 전부터 경고 모달을 띄운다. */
export const IDLE_WARNING_BEFORE_MS = 60 * 1000;

/** 활동 기록·전파 최소 간격. pointermove 가 아니어도 wheel 은 초당 수십 번 온다. */
export const ACTIVITY_THROTTLE_MS = 1000;

export const IDLE_CHECK_INTERVAL_MS = 1000;

export const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'wheel', 'touchstart'] as const;

export const AUTH_CHANNEL_NAME = 'groove-auth';

/** 로그인 화면 ?reason= 값. 문구는 authMessages.ts 의 LOGIN_NOTICE_MESSAGES. */
export type LoginReason = 'idle' | 'expired' | 'password-changed';
