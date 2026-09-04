import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import type { UseFormSetError } from 'react-hook-form';
import { Link, useLocation } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { useToast } from '@/components/common/toastContext';
import { ReviewForm } from '@/components/review/ReviewForm';
import { ReviewList } from '@/components/review/ReviewList';
import { ReviewSummary } from '@/components/review/ReviewSummary';
import { REVIEW_INELIGIBLE_MESSAGE } from '@/constants/review';
import { useCreateReview, useDeleteReview, useUpdateReview } from '@/hooks/mutations/useReviewMutations';
import { reviewKeys } from '@/hooks/queries/queryKeys';
import { useReviewEligibility } from '@/hooks/queries/useReviewEligibility';
import { useReviewStats } from '@/hooks/queries/useReviewStats';
import { toReviewFormValues, type ReviewFormValues } from '@/schemas/review';
import type { Review, ReviewSort, ReviewWriteRequest } from '@/types/review';
import { applyFieldErrors, getErrorCode, getErrorMessage } from '@/utils/apiError';

interface ReviewSectionProps {
  productId: number;
  averageRating?: number;
  reviewCount: number;
}

type ReviewFormSubmitHelpers = { setError: UseFormSetError<ReviewFormValues> };

export function ReviewSection({ productId, averageRating, reviewCount }: ReviewSectionProps) {
  const sectionRef = useRef<HTMLElement>(null);
  const { pathname } = useLocation();
  const { showToast } = useToast();
  const queryClient = useQueryClient();

  const [sort, setSort] = useState<ReviewSort>('latest');
  const [page, setPage] = useState(0);
  const [isWriting, setIsWriting] = useState(false);
  const [editingId, setEditingId] = useState<number | undefined>(undefined);
  const [deleting, setDeleting] = useState<Review | undefined>(undefined);

  const eligibilityQuery = useReviewEligibility(productId);
  const statsQuery = useReviewStats(productId);
  const createMutation = useCreateReview(productId);
  const updateMutation = useUpdateReview(productId);
  const deleteMutation = useDeleteReview(productId);

  /* eligibility 는 리뷰 CRUD 로 판정이 바뀌는데(구매 확인 후 작성, 중복 작성) 목록과 같은 접두사라 함께 무효화된다. */
  const invalidateReviews = () =>
    queryClient.invalidateQueries({ queryKey: reviewKeys.product(productId) });

  const handleSortChange = (nextSort: ReviewSort) => {
    setSort(nextSort);
    setPage(0);
  };

  const handlePageChange = (nextPage: number) => {
    setPage(nextPage);
    sectionRef.current?.scrollIntoView({ block: 'start' });
  };

  const handleCreate = (payload: ReviewWriteRequest, { setError }: ReviewFormSubmitHelpers) => {
    createMutation.mutate(payload, {
      onSuccess: () => {
        showToast('success', '리뷰를 등록했습니다.');
        setIsWriting(false);
        // 방금 쓴 리뷰가 첫 페이지 맨 위에 오도록 정렬·페이지를 되돌린다.
        setSort('latest');
        setPage(0);
      },
      onError: (error) => {
        if (!applyFieldErrors(error, setError)) {
          setError('root.serverError', { message: getErrorMessage(error) });
        }
        const code = getErrorCode(error);
        if (code === 'REVIEW_ALREADY_EXISTS' || code === 'REVIEW_PURCHASE_REQUIRED') {
          invalidateReviews();
        }
      },
    });
  };

  const handleUpdate =
    (review: Review) => (payload: ReviewWriteRequest, { setError }: ReviewFormSubmitHelpers) => {
      updateMutation.mutate(
        { reviewId: review.id, payload },
        {
          onSuccess: () => {
            showToast('success', '리뷰를 수정했습니다.');
            setEditingId(undefined);
          },
          onError: (error) => {
            if (!applyFieldErrors(error, setError)) {
              setError('root.serverError', { message: getErrorMessage(error) });
            }
            if (getErrorCode(error) === 'REVIEW_NOT_FOUND') {
              invalidateReviews();
            }
          },
        },
      );
    };

  const handleDelete = () => {
    if (!deleting) {
      return;
    }
    deleteMutation.mutate(deleting.id, {
      onSuccess: () => {
        showToast('success', '리뷰를 삭제했습니다.');
        setDeleting(undefined);
      },
      onError: (error) => {
        setDeleting(undefined);
        showToast('error', getErrorMessage(error));
        if (getErrorCode(error) === 'REVIEW_NOT_FOUND') {
          invalidateReviews();
        }
      },
    });
  };

  const eligibility = eligibilityQuery.data;

  return (
    <section id="reviews" ref={sectionRef} className="mt-12">
      <h2 className="mb-4 text-lg font-bold">리뷰</h2>
      <ReviewSummary
        averageRating={averageRating}
        reviewCount={reviewCount}
        distribution={statsQuery.data?.distribution}
      />

      {!eligibilityQuery.isPending && eligibility && (
        <div className="mt-6">
          {eligibility.eligible && !isWriting && (
            <Button onClick={() => setIsWriting(true)}>리뷰 작성</Button>
          )}

          {eligibility.eligible && isWriting && (
            <div className="max-w-lg">
              <ReviewForm
                submitLabel="등록"
                pending={createMutation.isPending}
                onSubmit={handleCreate}
                onCancel={() => setIsWriting(false)}
              />
            </div>
          )}

          {!eligibility.eligible && eligibility.reason === 'LOGIN_REQUIRED' && (
            <Link
              to={`/login?redirect=${encodeURIComponent(pathname)}`}
              className="inline-flex h-10 items-center justify-center gap-1.5 rounded-md border border-line-strong bg-surface px-4 text-sm font-medium text-content hover:bg-surface-muted"
            >
              로그인 후 작성
            </Link>
          )}

          {!eligibility.eligible && eligibility.reason === 'PURCHASE_REQUIRED' && (
            <div className="flex flex-wrap items-center gap-3">
              <Button disabled title={REVIEW_INELIGIBLE_MESSAGE.PURCHASE_REQUIRED}>
                리뷰 작성
              </Button>
              <span className="text-xs text-content-muted">
                {REVIEW_INELIGIBLE_MESSAGE.PURCHASE_REQUIRED}
              </span>
            </div>
          )}

          {!eligibility.eligible && eligibility.reason === 'ALREADY_REVIEWED' && (
            <p className="text-xs text-content-muted">
              이미 이 상품에 리뷰를 작성했습니다. 아래 내 리뷰에서 수정할 수 있습니다.
            </p>
          )}
        </div>
      )}

      <div className="mt-6">
        <ReviewList
          productId={productId}
          sort={sort}
          page={page}
          onSortChange={handleSortChange}
          onPageChange={handlePageChange}
          editingId={editingId}
          onEdit={(review) => setEditingId(review.id)}
          onDelete={(review) => setDeleting(review)}
          renderEditForm={(review) => (
            <ReviewForm
              defaultValues={toReviewFormValues(review)}
              submitLabel="저장"
              pending={updateMutation.isPending}
              onSubmit={handleUpdate(review)}
              onCancel={() => setEditingId(undefined)}
            />
          )}
        />
      </div>

      <ConfirmDialog
        open={Boolean(deleting)}
        onClose={() => setDeleting(undefined)}
        onConfirm={handleDelete}
        title="리뷰를 삭제하시겠습니까?"
        description="삭제한 리뷰는 되돌릴 수 없습니다."
        confirmLabel="삭제"
        pending={deleteMutation.isPending}
      />
    </section>
  );
}
