import { MAX_REASON_BADGES, RECOMMEND_REASON_LABELS } from '@/constants/recommendReasons';
import type { RecommendReason } from '@/types/recommend';

export function RecommendReasonBadges({ reasons }: { reasons: RecommendReason[] }) {
  const label = reasons
    .slice(0, MAX_REASON_BADGES)
    .map((reason) => RECOMMEND_REASON_LABELS[reason])
    .join(' · ');

  // 사유가 없어도 카드 하단 정렬이 흔들리지 않도록 한 줄 높이는 유지한다.
  return <p className="mt-1 min-h-4 text-xs text-accent-hover">{label}</p>;
}
