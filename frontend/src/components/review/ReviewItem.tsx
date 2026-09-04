import type { ReactNode } from 'react';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { StarRatingDisplay } from '@/components/common/StarRating';
import type { Review } from '@/types/review';
import { formatDate } from '@/utils/formatDate';

interface ReviewItemProps {
  review: Review;
  onEdit?: () => void;
  onDelete?: () => void;
  editing?: boolean;
  editForm?: ReactNode;
}

export function ReviewItem({ review, onEdit, onDelete, editing = false, editForm }: ReviewItemProps) {
  const isEdited = review.updatedAt !== review.createdAt;

  return (
    <li
      className={`rounded-lg border p-4 ${
        review.mine ? 'border-accent bg-accent-soft/40' : 'border-line bg-surface'
      }`}
    >
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <StarRatingDisplay value={review.rating} size="sm" />
          <span className="text-sm font-medium text-content">{review.nickname}</span>
          <span className="text-xs text-content-muted">
            {formatDate(review.createdAt)}
            {isEdited && ' (수정됨)'}
          </span>
          {review.mine && <Badge variant="accent">내 리뷰</Badge>}
        </div>

        {review.mine && !editing && (
          <div className="flex shrink-0 gap-1">
            <Button variant="ghost" size="sm" onClick={onEdit}>
              수정
            </Button>
            <Button variant="ghost" size="sm" className="text-danger hover:text-danger" onClick={onDelete}>
              삭제
            </Button>
          </div>
        )}
      </div>

      {editing ? (
        <div className="mt-3">{editForm}</div>
      ) : (
        <div className="mt-2">
          {review.title && <p className="text-sm font-bold text-content">{review.title}</p>}
          {review.content && (
            <p className="mt-1 whitespace-pre-wrap text-sm text-content">{review.content}</p>
          )}
        </div>
      )}
    </li>
  );
}
