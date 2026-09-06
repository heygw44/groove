import { Badge } from '@/components/common/Badge';
import { MAX_REASON_BADGES, RECOMMEND_REASON_LABELS } from '@/constants/recommendReasons';
import type { RecommendReason } from '@/types/recommend';

export function RecommendReasonBadge({ reason }: { reason: RecommendReason }) {
  return <Badge variant="accent">{RECOMMEND_REASON_LABELS[reason]}</Badge>;
}

export function RecommendReasonBadges({ reasons }: { reasons: RecommendReason[] }) {
  if (reasons.length === 0) {
    return null;
  }

  return (
    <div className="mt-1.5 flex flex-wrap gap-1">
      {reasons.slice(0, MAX_REASON_BADGES).map((reason) => (
        <RecommendReasonBadge key={reason} reason={reason} />
      ))}
    </div>
  );
}
