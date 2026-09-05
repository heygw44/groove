import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useMemo } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { Select } from '@/components/common/Select';
import { useToast } from '@/components/common/toastContext';
import {
  useCreateAdminCoupon,
  useUpdateAdminCoupon,
} from '@/hooks/mutations/useAdminCouponMutations';
import {
  createAdminCouponFormSchema,
  EMPTY_ADMIN_COUPON_FORM_VALUES,
  generateCouponCode,
  toCreatePayload,
  toFormValues,
  toUpdatePayload,
  type AdminCouponFormValues,
} from '@/schemas/adminCoupon';
import type { AdminCouponSummary } from '@/types/coupon';
import { applyFieldErrors, getErrorMessage } from '@/utils/apiError';
import { toDatetimeLocalValue } from '@/utils/formatDate';
import { getServerNow } from '@/utils/serverTime';

interface AdminCouponFormModalProps {
  open: boolean;
  onClose: () => void;
  /** 있으면 수정, 없으면 등록. */
  coupon?: AdminCouponSummary;
}

const DISCOUNT_TYPE_OPTIONS: { value: AdminCouponFormValues['discountType']; label: string }[] = [
  { value: 'FIXED', label: '정액' },
  { value: 'RATE', label: '정률' },
];

export function AdminCouponFormModal({ open, onClose, coupon }: AdminCouponFormModalProps) {
  const isEdit = Boolean(coupon);
  const discountLocked = Boolean(coupon && coupon.issuedCount > 0);

  const { showToast } = useToast();
  const createMutation = useCreateAdminCoupon();
  const updateMutation = useUpdateAdminCoupon();

  // 만료일 검증 기준 시각은 모달을 여는 순간이어야 한다. 모듈 로드 시점에 고정하면 오래 켜 둔 탭에서 기준이 낡는다.
  const resolver = useMemo(
    () => zodResolver(createAdminCouponFormSchema()),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [open],
  );

  const {
    register,
    handleSubmit,
    control,
    setValue,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AdminCouponFormValues>({
    resolver,
    mode: 'onBlur',
    defaultValues: EMPTY_ADMIN_COUPON_FORM_VALUES,
  });

  useEffect(() => {
    if (open) {
      reset(coupon ? toFormValues(coupon) : EMPTY_ADMIN_COUPON_FORM_VALUES);
    }
  }, [open, coupon, reset]);

  const isMutating = createMutation.isPending || updateMutation.isPending;
  const isBusy = isSubmitting || isMutating;
  const discountType = useWatch({ control, name: 'discountType' });

  const handleError = (error: unknown) => {
    if (!applyFieldErrors(error, setError)) {
      setError('root.serverError', { message: getErrorMessage(error) });
    }
  };

  const totalQuantityHelp = coupon
    ? `발급 수 ${coupon.issuedCount} 이상이어야 합니다. 비워두면 무제한`
    : '비워두면 무제한';

  const onSubmit = handleSubmit((values) => {
    if (!isEdit) {
      createMutation.mutate(toCreatePayload(values), {
        onSuccess: () => {
          showToast('success', '쿠폰을 등록했습니다.');
          onClose();
        },
        onError: handleError,
      });
      return;
    }

    if (!coupon) {
      return;
    }

    const payload = toUpdatePayload(values, coupon);
    if (Object.keys(payload).length === 0) {
      showToast('info', '변경된 내용이 없습니다.');
      onClose();
      return;
    }

    updateMutation.mutate(
      { id: coupon.id, payload },
      {
        onSuccess: () => {
          showToast('success', '쿠폰을 수정했습니다.');
          onClose();
        },
        onError: handleError,
      },
    );
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? '쿠폰 수정' : '쿠폰 등록'}
      size="md"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isBusy}>
            취소
          </Button>
          <Button type="submit" form="admin-coupon-form" disabled={isBusy}>
            저장
          </Button>
        </>
      }
    >
      <form id="admin-coupon-form" className="flex flex-col gap-4" onSubmit={onSubmit} noValidate>
        <FormError message={errors.root?.serverError?.message} />

        <Field
          htmlFor="coupon-code"
          label="코드"
          required
          help={isEdit ? '코드는 수정할 수 없습니다.' : undefined}
          error={errors.code?.message}
        >
          <div className="flex gap-2">
            <Input
              id="coupon-code"
              disabled={isEdit}
              invalid={Boolean(errors.code)}
              {...register('code')}
            />
            {!isEdit && (
              <Button
                type="button"
                variant="secondary"
                size="sm"
                className="shrink-0"
                onClick={() =>
                  setValue('code', generateCouponCode(), {
                    shouldValidate: true,
                    shouldDirty: true,
                  })
                }
              >
                자동 생성
              </Button>
            )}
          </div>
        </Field>

        <Field htmlFor="coupon-name" label="이름" required error={errors.name?.message}>
          <Input id="coupon-name" invalid={Boolean(errors.name)} {...register('name')} />
        </Field>

        {discountLocked && (
          <p className="rounded-md border border-line-strong bg-surface-sunken px-3 py-2.5 text-xs text-content-muted">
            발급이 시작되어 할인 조건은 변경할 수 없습니다.
          </p>
        )}

        <Controller
          control={control}
          name="discountType"
          render={({ field }) => (
            <fieldset className="flex flex-col gap-2">
              <legend className="mb-1 text-sm font-medium text-content">할인 유형</legend>
              {DISCOUNT_TYPE_OPTIONS.map((option) => (
                <label key={option.value} className="flex items-center gap-2.5 text-sm">
                  <input
                    type="radio"
                    name={field.name}
                    value={option.value}
                    checked={field.value === option.value}
                    disabled={discountLocked}
                    onChange={() => {
                      field.onChange(option.value);
                      if (option.value === 'FIXED') {
                        setValue('maxDiscountAmount', '', { shouldValidate: true });
                      }
                    }}
                    className="h-4 w-4 accent-content"
                  />
                  {option.label}
                </label>
              ))}
            </fieldset>
          )}
        />

        <Field
          htmlFor="coupon-discount-value"
          label={discountType === 'FIXED' ? '할인 금액(원)' : '할인율(%)'}
          required
          error={errors.discountValue?.message}
        >
          <Input
            id="coupon-discount-value"
            inputMode="numeric"
            disabled={discountLocked}
            invalid={Boolean(errors.discountValue)}
            {...register('discountValue')}
          />
        </Field>

        <Field
          htmlFor="coupon-min-order-amount"
          label="최소 주문 금액"
          help="비워두면 제한 없음"
          error={errors.minOrderAmount?.message}
        >
          <Input
            id="coupon-min-order-amount"
            inputMode="numeric"
            disabled={discountLocked}
            invalid={Boolean(errors.minOrderAmount)}
            {...register('minOrderAmount')}
          />
        </Field>

        {discountType === 'RATE' && (
          <Field
            htmlFor="coupon-max-discount-amount"
            label="최대 할인 금액"
            error={errors.maxDiscountAmount?.message}
          >
            <Input
              id="coupon-max-discount-amount"
              inputMode="numeric"
              disabled={discountLocked}
              invalid={Boolean(errors.maxDiscountAmount)}
              {...register('maxDiscountAmount')}
            />
          </Field>
        )}

        <Field
          htmlFor="coupon-total-quantity"
          label="발급 수량"
          help={totalQuantityHelp}
          error={errors.totalQuantity?.message}
        >
          <Input
            id="coupon-total-quantity"
            inputMode="numeric"
            invalid={Boolean(errors.totalQuantity)}
            {...register('totalQuantity')}
          />
        </Field>

        <Field
          htmlFor="coupon-expires-at"
          label="만료일"
          required
          error={errors.expiresAt?.message}
        >
          <Input
            id="coupon-expires-at"
            type="datetime-local"
            min={toDatetimeLocalValue(getServerNow())}
            invalid={Boolean(errors.expiresAt)}
            {...register('expiresAt')}
          />
        </Field>

        {isEdit && (
          <Field
            htmlFor="coupon-status"
            label="상태"
            help="만료된 쿠폰을 다시 활성화하려면 만료일도 미래로 바꿔주세요."
            error={errors.status?.message}
          >
            <Select id="coupon-status" invalid={Boolean(errors.status)} {...register('status')}>
              <option value="ACTIVE">활성</option>
              <option value="DISABLED">비활성</option>
            </Select>
          </Field>
        )}
      </form>
    </Modal>
  );
}
