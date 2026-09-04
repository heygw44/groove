import { getServerTime } from '@/api/time';

/*
 * 앵커 방식: 동기화 시점의 서버 시각을 performance.now() 스냅샷에 고정해두고,
 * 이후로는 그 스냅샷과의 차이만 더한다. performance.now() 는 단조증가라
 * 동기화 이후 사용자가 시스템 시계를 바꿔도(또는 Date.now() 를 조작해도) 영향받지 않는다.
 */
let anchorServerMs = Date.now();
let anchorPerf = performance.now();

export function getServerNowMs(): number {
  return anchorServerMs + (performance.now() - anchorPerf);
}

/** 기존 시그니처 유지 - 쿠폰 D-day 계산 등 Date 객체를 쓰는 호출부는 그대로 둔다. */
export function getServerNow(): Date {
  return new Date(getServerNowMs());
}

/** rttMs 는 요청 왕복 시간. 서버가 응답을 만든 시점은 왕복의 중간이라 절반만 보정한다. */
export function applyServerTime(iso: string, rttMs = 0): void {
  anchorServerMs = new Date(iso).getTime() + rttMs / 2;
  anchorPerf = performance.now();
}

/** 기존 호출부(schemas/adminCoupon.ts 등) 호환용 별칭. */
export function setServerOffset(iso: string): void {
  applyServerTime(iso);
}

/**
 * offset 이 없는 LocalDateTime 문자열(관리자 DTO, purchase.expiresAt 등)은
 * Asia/Seoul 암묵이므로 '+09:00' 을 붙여 파싱한다. 이미 오프셋(Z 또는 ±hh:mm)이
 * 있으면 그대로 둔다.
 */
export function toServerMs(dateTime: string): number {
  const hasOffset = /Z$|[+-]\d{2}:\d{2}$/.test(dateTime);
  return Date.parse(hasOffset ? dateTime : `${dateTime}+09:00`);
}

export async function syncServerTime(): Promise<void> {
  const start = performance.now();
  try {
    const { serverTime } = await getServerTime();
    const rttMs = performance.now() - start;
    applyServerTime(serverTime, rttMs);
  } catch {
    // 동기화 실패는 조용히 무시한다 - 다음 60초 주기에 다시 시도한다.
  }
}

let subscriberCount = 0;
let intervalId: ReturnType<typeof setInterval> | undefined;

const SYNC_INTERVAL_MS = 60_000;

/** refcount 구독: 첫 구독자가 즉시 동기화하고 주기 동기화를 시작하고, 마지막 구독 해제 시 멈춘다. */
export function subscribeServerTimeSync(): () => void {
  subscriberCount += 1;
  if (subscriberCount === 1) {
    void syncServerTime();
    intervalId = setInterval(() => void syncServerTime(), SYNC_INTERVAL_MS);
  }

  let unsubscribed = false;
  return () => {
    if (unsubscribed) {
      return;
    }
    unsubscribed = true;
    subscriberCount -= 1;
    if (subscriberCount === 0 && intervalId !== undefined) {
      clearInterval(intervalId);
      intervalId = undefined;
    }
  };
}
