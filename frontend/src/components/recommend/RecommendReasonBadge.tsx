import { Badge } from '@/components/common/Badge';
import { MAX_REASON_BADGES, RECOMMEND_REASON_LABELS } from '@/constants/recommendReasons';
import type { RecommendReason } from '@/types/recommend';

export function RecommendReasonBadge({ reason }: { reason: RecommendReason }) {
  return <Badge variant="accent">{RECOMMEND_REASON_LABELS[reason]}</Badge>;
}

export function RecommendReasonBadges({ reasons }: { reasons: RecommendReason[] }) {
  return (
    <div className="mt-1.5 flex h-5 flex-nowrap gap-1 overflow-hidden">
      {reasons.slice(0, MAX_REASON_BADGES).map((reason) => (
        <RecommendReasonBadge key={reason} reason={reason} />
      ))}
    </div>
  );
}
