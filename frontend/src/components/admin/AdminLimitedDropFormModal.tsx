import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useMemo } from 'react';
import { Controller, useForm } from 'react-hook-form';

import { ProductSearchSelect } from '@/components/admin/ProductSearchSelect';
import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { useToast } from '@/components/common/toastContext';
import {
  useCreateAdminLimitedDrop,
  useUpdateAdminLimitedDrop,
} from '@/hooks/mutations/useAdminLimitedDropMutations';
import { useAdminLimitedDrops } from '@/hooks/queries/useAdminLimitedDrops';
import {
  createAdminLimitedDropFormSchema,
  EMPTY_ADMIN_LIMITED_DROP_FORM_VALUES,
  toAdminLimitedDropCreatePayload,
  toAdminLimitedDropFormValues,
  toAdminLimitedDropUpdatePayload,
  type AdminLimitedDropFormValues,
} from '@/schemas/adminLimitedDrop';
import type { AdminLimitedDropSummary } from '@/types/limitedDrop';
import { applyFieldErrors, getErrorMessage } from '@/utils/apiError';
import { toDatetimeLocalValue } from '@/utils/formatDate';
import { getServerNow } from '@/utils/serverTime';

interface AdminLimitedDropFormModalProps {
  open: boolean;
  onClose: () => void;
  /** 있으면 수정, 없으면 등록. */
  drop?: AdminLimitedDropSummary;
}

const REGISTERED_LIST_SIZE = 100;

export function AdminLimitedDropFormModal({ open, onClose, drop }: AdminLimitedDropFormModalProps) {
  const isEdit = Boolean(drop);

  const { showToast } = useToast();
  const createMutation = useCreateAdminLimitedDrop();
  const updateMutation = useUpdateAdminLimitedDrop();

  // 유니크 제약(uk_limited_drop_product)이 CLOSED 드롭도 막으므로 상태와 무관하게 전체를 대조 대상으로 삼는다.
  const { data: existingDrops } = useAdminLimitedDrops({ size: REGISTERED_LIST_SIZE });
  const disabledProductIds = useMemo(() => {
    const ids = new Set<number>();
    existingDrops?.content.forEach((existing) => {
      if (existing.id !== drop?.id) {
        ids.add(existing.productId);
      }
    });
    return ids;
  }, [existingDrops, drop]);

  // 오픈 시각 검증 기준 시각은 모달을 여는 순간이어야 한다. 모듈 로드 시점에 고정하면 오래 켜 둔 탭에서 기준이 낡는다.
  const resolver = useMemo(
    () => zodResolver(createAdminLimitedDropFormSchema()),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [open],
  );

  const {
    register,
    handleSubmit,
    control,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AdminLimitedDropFormValues>({
    resolver,
    mode: 'onBlur',
    defaultValues: EMPTY_ADMIN_LIMITED_DROP_FORM_VALUES,
  });

  useEffect(() => {
    if (open) {
      reset(drop ? toAdminLimitedDropFormValues(drop) : EMPTY_ADMIN_LIMITED_DROP_FORM_VALUES);
    }
  }, [open, drop, reset]);

  const isMutating = createMutation.isPending || updateMutation.isPending;
  const isBusy = isSubmitting || isMutating;

  const handleError = (error: unknown) => {
    if (!applyFieldErrors(error, setError)) {
      setError('root.serverError', { message: getErrorMessage(error) });
    }
  };

  const onSubmit = handleSubmit((values) => {
    if (!isEdit) {
      createMutation.mutate(toAdminLimitedDropCreatePayload(values), {
        onSuccess: () => {
          showToast('success', '한정반 드롭을 등록했습니다.');
          onClose();
        },
        onError: handleError,
      });
      return;
    }

    if (!drop) {
      return;
    }

    const payload = toAdminLimitedDropUpdatePayload(drop, values);
    if (!payload) {
      showToast('info', '변경된 내용이 없습니다.');
      onClose();
      return;
    }

    updateMutation.mutate(
      { id: drop.id, payload },
      {
        onSuccess: () => {
          showToast('success', '한정반 드롭을 수정했습니다.');
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
      title={isEdit ? '한정반 드롭 수정' : '한정반 드롭 등록'}
      size="md"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isBusy}>
            취소
          </Button>
          <Button type="submit" form="admin-limited-drop-form" disabled={isBusy}>
            저장
          </Button>
        </>
      }
    >
      <form
        id="admin-limited-drop-form"
        className="flex flex-col gap-4"
        onSubmit={onSubmit}
        noValidate
      >
        <FormError message={errors.root?.serverError?.message} />

        <Field
          htmlFor="limited-drop-product"
          label="상품"
          required
          help={isEdit ? '상품은 수정할 수 없습니다.' : undefined}
          error={errors.productId?.message}
        >
          <Controller
            control={control}
            name="productId"
            render={({ field }) => (
              <ProductSearchSelect
                id="limited-drop-product"
                value={field.value ? Number(field.value) : undefined}
                selectedTitle={drop?.productTitle}
                disabled={isEdit}
                invalid={Boolean(errors.productId)}
                disabledProductIds={disabledProductIds}
                onChange={(product) => field.onChange(product ? String(product.id) : '')}
              />
            )}
          />
        </Field>

        <Field
          htmlFor="limited-drop-total-quantity"
          label="총 수량"
          required
          error={errors.totalQuantity?.message}
        >
          <Input
            id="limited-drop-total-quantity"
            inputMode="numeric"
            invalid={Boolean(errors.totalQuantity)}
            {...register('totalQuantity')}
          />
        </Field>

        <Field
          htmlFor="limited-drop-per-member-limit"
          label="1인 구매 한도"
          required
          help="1~5 사이"
          error={errors.perMemberLimit?.message}
        >
          <Input
            id="limited-drop-per-member-limit"
            inputMode="numeric"
            invalid={Boolean(errors.perMemberLimit)}
            {...register('perMemberLimit')}
          />
        </Field>

        <Field
          htmlFor="limited-drop-open-at"
          label="오픈 시각"
          required
          error={errors.openAt?.message}
        >
          <Input
            id="limited-drop-open-at"
            type="datetime-local"
            min={toDatetimeLocalValue(getServerNow())}
            invalid={Boolean(errors.openAt)}
            {...register('openAt')}
          />
        </Field>

        <Field
          htmlFor="limited-drop-close-at"
          label="마감 시각"
          required
          error={errors.closeAt?.message}
        >
          <Input
            id="limited-drop-close-at"
            type="datetime-local"
            min={toDatetimeLocalValue(getServerNow())}
            invalid={Boolean(errors.closeAt)}
            {...register('closeAt')}
          />
        </Field>
      </form>
    </Modal>
  );
}
