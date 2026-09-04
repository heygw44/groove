import type { ReviewRatingDistribution as ReviewRatingDistributionType } from '@/types/review';

interface ReviewRatingDistributionProps {
  distribution: ReviewRatingDistributionType;
}

const RATINGS = ['5', '4', '3', '2', '1'] as const;

export function ReviewRatingDistribution({ distribution }: ReviewRatingDistributionProps) {
  const total = RATINGS.reduce((sum, rating) => sum + distribution[rating], 0);
  if (total === 0) {
    return null;
  }

  return (
    <div className="flex flex-col gap-1.5">
      {RATINGS.map((rating) => {
        const count = distribution[rating];
        const percent = Math.round((count / total) * 100);
        return (
          <div key={rating} className="flex items-center gap-2 text-xs">
            <span className="w-6 shrink-0 text-content-muted">{rating}점</span>
            <div className="h-2 flex-1 overflow-hidden rounded-full bg-surface-muted">
              <div
                role="progressbar"
                aria-valuenow={percent}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-label={`${rating}점 ${count}개`}
                className="h-full rounded-full bg-accent"
                style={{ width: `${percent}%` }}
              />
            </div>
            <span className="w-9 shrink-0 text-right text-content-muted">{percent}%</span>
            <span className="w-14 shrink-0 text-content-muted">({count}개)</span>
          </div>
        );
      })}
    </div>
  );
}
