import { Badge } from '@/components/common/Badge';
import type { TasteMatch } from '@/types/recommend';

interface TasteMatchBadgeProps {
  tasteMatch?: TasteMatch;
  className?: string;
}

export function TasteMatchBadge({ tasteMatch, className }: TasteMatchBadgeProps) {
  if (!tasteMatch?.matched) {
    return null;
  }

  return (
    <Badge variant="accent" className={className}>
      당신 취향 드롭
    </Badge>
  );
}
