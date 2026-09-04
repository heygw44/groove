import { StarRatingDisplay } from '@/components/common/StarRating';

interface ReviewSummaryProps {
  averageRating?: number;
  reviewCount: number;
}

export function ReviewSummary({ averageRating, reviewCount }: ReviewSummaryProps) {
  if (averageRating === undefined) {
    return (
      <div className="flex flex-col gap-1">
        <p className="text-sm font-medium text-content">아직 리뷰가 없습니다.</p>
        <p className="text-xs text-content-muted">첫 리뷰를 남겨보세요.</p>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-3">
      <span className="text-3xl font-bold tracking-tight">{averageRating.toFixed(1)}</span>
      <div className="flex flex-col gap-1">
        <StarRatingDisplay value={averageRating} />
        <span className="text-sm text-content-muted">리뷰 {reviewCount}개</span>
      </div>
    </div>
  );
}
