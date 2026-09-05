import type { LimitedDropDetail, LimitedDropStatus, LimitedDropSummary } from '@/types/limitedDrop';
import { toServerMs } from '@/utils/serverTime';

export type DropPhase = 'SCHEDULED' | 'OPENING' | 'OPEN' | 'SOLD_OUT' | 'CLOSED';

interface CountdownParts {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
}

const HOUR_MS = 1000 * 60 * 60;
const DAY_MS = HOUR_MS * 24;

/**
 * status 는 스케줄러가 openAt 을 지난 뒤에야 OPEN 으로 바뀌므로, 그 사이(SCHEDULED
 * 인데 서버 시각이 이미 openAt 을 지난) 구간을 OPENING 으로 따로 표시해 refetch 를 유도한다.
 */
export function getDropPhase(
  drop: Pick<LimitedDropSummary, 'status' | 'openAt'>,
  nowMs: number,
): DropPhase {
  if (drop.status === 'SCHEDULED' && nowMs >= toServerMs(drop.openAt)) {
    return 'OPENING';
  }
  return drop.status;
}

export function splitCountdown(ms: number): CountdownParts {
  const clamped = Math.max(ms, 0);
  const totalSeconds = Math.floor(clamped / 1000);
  return {
    days: Math.floor(totalSeconds / (24 * 60 * 60)),
    hours: Math.floor((totalSeconds % (24 * 60 * 60)) / (60 * 60)),
    minutes: Math.floor((totalSeconds % (60 * 60)) / 60),
    seconds: totalSeconds % 60,
  };
}

const pad2 = (value: number): string => String(value).padStart(2, '0');

/** 24시간 이상 남았으면 D-n, 아니면 hh:mm:ss 카운트다운. */
export function formatDday(openAtMs: number, nowMs: number): string {
  const remaining = openAtMs - nowMs;
  if (remaining >= DAY_MS) {
    return `D-${Math.ceil(remaining / DAY_MS)}`;
  }
  const { hours, minutes, seconds } = splitCountdown(remaining);
  return `${pad2(hours)}:${pad2(minutes)}:${pad2(seconds)}`;
}

interface PurchaseButtonState {
  label: string;
  disabled: boolean;
}

export function getPurchaseButtonState(
  drop: Pick<LimitedDropDetail, 'purchased'>,
  phase: DropPhase,
  isLoggedIn: boolean,
): PurchaseButtonState {
  if (phase === 'SCHEDULED' || phase === 'OPENING') {
    return { label: '오픈 대기', disabled: true };
  }
  if (phase === 'SOLD_OUT') {
    return { label: '매진', disabled: true };
  }
  if (phase === 'CLOSED') {
    return { label: '마감', disabled: true };
  }
  // phase === 'OPEN'
  if (isLoggedIn && drop.purchased) {
    return { label: '구매 완료', disabled: true };
  }
  return { label: '구매하기', disabled: false };
}

const STATUS_PRIORITY: Record<LimitedDropStatus, number> = {
  OPEN: 0,
  SCHEDULED: 1,
  SOLD_OUT: 2,
  CLOSED: 3,
};

/** 배너에 걸 드롭 하나를 고른다: OPEN 중 openAt 이 가장 이른 것, 없으면 SCHEDULED 중 가장 이른 것. */
export function pickBannerDrop(drops: LimitedDropSummary[]): LimitedDropSummary | undefined {
  const candidates = drops.filter((drop) => drop.status === 'OPEN' || drop.status === 'SCHEDULED');
  if (candidates.length === 0) {
    return undefined;
  }
  return [...candidates].sort((a, b) => {
    if (a.status !== b.status) {
      return STATUS_PRIORITY[a.status] - STATUS_PRIORITY[b.status];
    }
    return toServerMs(a.openAt) - toServerMs(b.openAt);
  })[0];
}

export type PurchaseErrorKind =
  | 'SOLD_OUT'
  | 'ALREADY_PURCHASED'
  | 'STATE_CHANGED'
  | 'ADDRESS_MISSING'
  | 'UNKNOWN';

const PURCHASE_ERROR_CODE_KIND: Record<string, PurchaseErrorKind> = {
  LIMITED_SOLD_OUT: 'SOLD_OUT',
  LIMITED_ALREADY_PURCHASED: 'ALREADY_PURCHASED',
  LIMITED_NOT_OPEN: 'STATE_CHANGED',
  LIMITED_CLOSED: 'STATE_CHANGED',
  LIMITED_INVALID_STATUS: 'STATE_CHANGED',
  MEMBER_ADDRESS_NOT_FOUND: 'ADDRESS_MISSING',
};

/** 한정반 구매 요청 실패 코드를 화면 처리 분기로 좁힌다. */
export function classifyPurchaseError(code: string | undefined): PurchaseErrorKind {
  if (!code) {
    return 'UNKNOWN';
  }
  return PURCHASE_ERROR_CODE_KIND[code] ?? 'UNKNOWN';
}
