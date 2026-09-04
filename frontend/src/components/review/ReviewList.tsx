import type { ReactNode } from 'react';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { Select } from '@/components/common/Select';
import { Spinner } from '@/components/common/Spinner';
import { ReviewItem } from '@/components/review/ReviewItem';
import { REVIEW_PAGE_SIZE, REVIEW_SORT_OPTIONS } from '@/constants/review';
import { useReviews } from '@/hooks/queries/useReviews';
import type { Review, ReviewSort } from '@/types/review';

interface ReviewListProps {
  productId: number;
  sort: ReviewSort;
  page: number;
  onSortChange: (sort: ReviewSort) => void;
  onPageChange: (page: number) => void;
  editingId?: number;
  onEdit: (review: Review) => void;
  onDelete: (review: Review) => void;
  renderEditForm: (review: Review) => ReactNode;
}

export function ReviewList({
  productId,
  sort,
  page,
  onSortChange,
  onPageChange,
  editingId,
  onEdit,
  onDelete,
  renderEditForm,
}: ReviewListProps) {
  const {
    data,
    isPending,
    isError,
    isPlaceholderData,
    refetch,
  } = useReviews(productId, { sort, page, size: REVIEW_PAGE_SIZE });

  return (
    <div>
      <div className="flex justify-end">
        <Select
          aria-label="리뷰 정렬"
          value={sort}
          onChange={(e) => onSortChange(e.target.value as ReviewSort)}
          className="w-auto"
        >
          {REVIEW_SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
      </div>

      <div className="mt-3">
        {isPending && (
          <div className="flex min-h-40 items-center justify-center">
            <Spinner />
          </div>
        )}

        {!isPending && isError && (
          <EmptyState
            title="리뷰를 불러오지 못했습니다."
            action={
              <Button variant="secondary" onClick={() => refetch()}>
                다시 시도
              </Button>
            }
          />
        )}

        {!isPending && !isError && data && data.content.length === 0 && (
          <EmptyState title="아직 작성된 리뷰가 없습니다." />
        )}

        {!isPending && !isError && data && data.content.length > 0 && (
          <div className={isPlaceholderData ? 'opacity-60' : ''}>
            <ul className="flex flex-col gap-3">
              {data.content.map((review) => (
                <ReviewItem
                  key={review.id}
                  review={review}
                  editing={editingId === review.id}
                  editForm={editingId === review.id ? renderEditForm(review) : undefined}
                  onEdit={review.mine ? () => onEdit(review) : undefined}
                  onDelete={review.mine ? () => onDelete(review) : undefined}
                />
              ))}
            </ul>

            <div className="mt-6">
              <Pagination page={page} totalPages={data.totalPages} onChange={onPageChange} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
