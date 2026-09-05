import { Link } from 'react-router-dom';

import { DropStatusBadge } from '@/components/limited/DropStatusBadge';
import type { LimitedDropSummary } from '@/types/limitedDrop';
import { formatPrice } from '@/utils/formatPrice';
import { formatDday, getDropPhase } from '@/utils/limitedDrop';
import { toServerMs } from '@/utils/serverTime';

interface LimitedDropBannerProps {
  drop: LimitedDropSummary;
  nowMs: number;
}

export function LimitedDropBanner({ drop, nowMs }: LimitedDropBannerProps) {
  const phase = getDropPhase(drop, nowMs);
  const showCountdown = phase === 'SCHEDULED' || phase === 'OPENING';

  return (
    <Link
      to={`/limited-drops/${drop.id}`}
      className="flex items-center gap-5 rounded-lg border border-line bg-accent-soft px-6 py-5"
    >
      <div className="h-20 w-20 shrink-0 overflow-hidden rounded-md bg-surface-muted">
        {drop.product.thumbnailUrl && (
          <img
            src={drop.product.thumbnailUrl}
            alt=""
            loading="lazy"
            className="h-full w-full object-cover"
          />
        )}
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <DropStatusBadge status={drop.status} />
          <span className="text-xs font-medium text-content-muted">한정반 드롭</span>
        </div>
        <p className="mt-1.5 truncate text-lg font-bold text-content">{drop.product.title}</p>
        <p className="text-sm text-content-muted">
          {drop.product.artistName} · {formatPrice(drop.product.price)}
        </p>
      </div>

      {showCountdown && (
        <p className="shrink-0 font-mono text-xl font-bold tabular-nums text-accent-hover">
          {formatDday(toServerMs(drop.openAt), nowMs)}
        </p>
      )}
    </Link>
  );
}
