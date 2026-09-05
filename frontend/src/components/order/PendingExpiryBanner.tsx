import { useEffect, useRef } from 'react';

import { formatDateTime } from '@/utils/formatDate';
import { splitCountdown } from '@/utils/limitedDrop';

interface PendingExpiryBannerProps {
  expiresAtMs: number;
  nowMs: number;
  onExpired: () => void;
}

const pad2 = (value: number): string => String(value).padStart(2, '0');

/** PENDING 주문의 결제 대기 안내. 남은 시간이 0 이 되면 onExpired 를 한 번만 호출한다(만료 스케줄러 재조회 유도). */
export function PendingExpiryBanner({ expiresAtMs, nowMs, onExpired }: PendingExpiryBannerProps) {
  const remainingMs = expiresAtMs - nowMs;
  const isExpired = remainingMs <= 0;
  const hasNotifiedRef = useRef(false);

  useEffect(() => {
    if (isExpired && !hasNotifiedRef.current) {
      hasNotifiedRef.current = true;
      onExpired();
    }
  }, [isExpired, onExpired]);

  return (
    <div className="mt-6 rounded-lg border border-line bg-surface-muted px-5 py-4 text-sm text-content-muted">
      {isExpired ? (
        <p>만료 처리 중…</p>
      ) : (
        <p>
          결제 대기 중입니다. {formatRemaining(remainingMs)} 내에 결제하지 않으면 자동
          취소됩니다. ({formatDateTime(new Date(expiresAtMs))}까지)
        </p>
      )}
    </div>
  );
}

function formatRemaining(remainingMs: number): string {
  const { minutes, seconds } = splitCountdown(remainingMs);
  return `${pad2(minutes)}:${pad2(seconds)}`;
}
