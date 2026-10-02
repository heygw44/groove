/**
 * 관리자 유휴 로그아웃 기준(경고는 1분 전). 서버는 관리자 access 5분·유휴 20분(재발급 때 보고된
 * 마지막 사용자 입력 기준)·12시간 절대 만료로 막는다.
 */
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
