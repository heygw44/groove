import { Link } from 'react-router-dom';

import { DropStatusBadge } from '@/components/limited/DropStatusBadge';
import { RemainingGauge } from '@/components/limited/RemainingGauge';
import type { LimitedDropSummary } from '@/types/limitedDrop';
import { formatPrice } from '@/utils/formatPrice';
import { formatDday, getDropPhase } from '@/utils/limitedDrop';
import { toServerMs } from '@/utils/serverTime';

interface LimitedDropCardProps {
  drop: LimitedDropSummary;
  nowMs: number;
}

export function LimitedDropCard({ drop, nowMs }: LimitedDropCardProps) {
  const phase = getDropPhase(drop, nowMs);
  const showCountdown = phase === 'SCHEDULED' || phase === 'OPENING';

  return (
    <Link
      to={`/limited-drops/${drop.id}`}
      className="group block text-content"
    >
      <div className="relative aspect-square overflow-hidden rounded-md bg-surface-muted">
        {drop.product.thumbnailUrl ? (
          <img
            src={drop.product.thumbnailUrl}
            alt=""
            loading="lazy"
            className="h-full w-full object-cover transition-transform group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center text-content-subtle">
            <svg
              width="40"
              height="40"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.4"
              aria-hidden
            >
              <circle cx="12" cy="12" r="9" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          </div>
        )}
        <DropStatusBadge status={drop.status} className="absolute left-2 top-2" />
      </div>

      <p className="mt-2.5 line-clamp-2 text-sm font-medium">{drop.product.title}</p>
      <p className="mt-0.5 text-xs text-content-muted">{drop.product.artistName}</p>
      <p className="mt-1 text-sm font-bold">{formatPrice(drop.product.price)}</p>

      <RemainingGauge
        className="mt-2"
        remaining={drop.remainingQuantity}
        total={drop.totalQuantity}
      />

      {showCountdown && (
        <p className="mt-1.5 text-xs font-medium text-accent-hover">
          {formatDday(toServerMs(drop.openAt), nowMs)}
        </p>
      )}
    </Link>
  );
}
