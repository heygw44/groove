import { zodResolver } from '@hookform/resolvers/zod';
import { Controller, useForm, useWatch, type UseFormSetError } from 'react-hook-form';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { StarRatingInput } from '@/components/common/StarRating';
import { Textarea } from '@/components/common/Textarea';
import { REVIEW_CONTENT_MAX, REVIEW_TITLE_MAX } from '@/constants/review';
import {
  EMPTY_REVIEW_FORM_VALUES,
  reviewFormSchema,
  toReviewPayload,
  type ReviewFormValues,
} from '@/schemas/review';
import type { ReviewWriteRequest } from '@/types/review';

interface ReviewFormProps {
  defaultValues?: ReviewFormValues;
  submitLabel: string;
  pending: boolean;
  onSubmit: (
    payload: ReviewWriteRequest,
    helpers: { setError: UseFormSetError<ReviewFormValues> },
  ) => void;
  onCancel?: () => void;
}

export function ReviewForm({
  defaultValues,
  submitLabel,
  pending,
  onSubmit,
  onCancel,
}: ReviewFormProps) {
  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ReviewFormValues>({
    resolver: zodResolver(reviewFormSchema),
    mode: 'onBlur',
    defaultValues: defaultValues ?? EMPTY_REVIEW_FORM_VALUES,
  });

  const content = useWatch({ control, name: 'content' });
  const isBusy = isSubmitting || pending;

  const submit = handleSubmit((values) => {
    onSubmit(toReviewPayload(values), { setError });
  });

  return (
    <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
      <FormError message={errors.root?.serverError?.message} />

      <div>
        <span className="mb-1.5 block text-sm font-medium text-content">별점</span>
        <Controller
          control={control}
          name="rating"
          render={({ field }) => (
            <StarRatingInput
              name={field.name}
              value={field.value}
              onChange={field.onChange}
              invalid={Boolean(errors.rating)}
              describedBy={errors.rating ? 'review-rating-error' : undefined}
            />
          )}
        />
        {errors.rating && (
          <p id="review-rating-error" role="alert" className="mt-1 text-xs text-danger">
            {errors.rating.message}
          </p>
        )}
      </div>

      <Field htmlFor="review-title" label="제목" error={errors.title?.message}>
        <Input
          id="review-title"
          maxLength={REVIEW_TITLE_MAX}
          invalid={Boolean(errors.title)}
          {...register('title')}
        />
      </Field>

      <Field htmlFor="review-content" label="내용" error={errors.content?.message}>
        <Textarea
          id="review-content"
          rows={4}
          maxLength={REVIEW_CONTENT_MAX}
          invalid={Boolean(errors.content)}
          {...register('content')}
        />
        <p className="mt-1 text-right text-xs text-content-subtle">
          {(content ?? '').length}/{REVIEW_CONTENT_MAX}
        </p>
      </Field>

      <div className="flex gap-2">
        <Button type="submit" disabled={isBusy}>
          {submitLabel}
        </Button>
        {onCancel && (
          <Button type="button" variant="secondary" onClick={onCancel} disabled={isBusy}>
            취소
          </Button>
        )}
      </div>
    </form>
  );
}
