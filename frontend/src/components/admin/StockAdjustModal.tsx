import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect } from 'react';
import { Controller, useForm } from 'react-hook-form';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { useToast } from '@/components/common/Toast';
import { useAdjustStock } from '@/hooks/mutations/useAdminProductMutations';
import { stockAdjustSchema, type StockAdjustFormValues } from '@/schemas/product';
import type { AdminProductSummary } from '@/types/product';
import { applyFieldErrors, getErrorMessage } from '@/utils/apiError';

interface StockAdjustModalProps {
  open: boolean;
  onClose: () => void;
  product?: AdminProductSummary;
}

const EMPTY_VALUES: StockAdjustFormValues = {
  changeType: 'IN',
  quantity: '',
  reason: '',
};

const STATUS_LABEL: Record<string, string> = {
  ON_SALE: '판매중',
  SOLD_OUT: '품절',
  HIDDEN: '숨김',
};

const CHANGE_TYPE_OPTIONS: { value: StockAdjustFormValues['changeType']; label: string; help: string }[] = [
  { value: 'IN', label: '입고', help: '현재 재고에 더합니다.' },
  { value: 'OUT', label: '출고', help: '현재 재고에서 뺍니다. 재고보다 많으면 실패합니다.' },
  { value: 'ADJUST', label: '조정', help: '입력한 수량으로 맞춥니다. 1 이상만 가능하며, 0으로 만들려면 출고를 사용하세요.' },
];

export function StockAdjustModal({ open, onClose, product }: StockAdjustModalProps) {
  const { showToast } = useToast();
  const adjustMutation = useAdjustStock();
  const {
    register,
    handleSubmit,
    control,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<StockAdjustFormValues>({
    resolver: zodResolver(stockAdjustSchema),
    mode: 'onBlur',
    defaultValues: EMPTY_VALUES,
  });

  useEffect(() => {
    if (open) {
      reset(EMPTY_VALUES);
    }
  }, [open, product, reset]);

  const isBusy = isSubmitting || adjustMutation.isPending;

  const handleError = (error: unknown) => {
    if (!applyFieldErrors(error, setError)) {
      setError('root.serverError', { message: getErrorMessage(error) });
    }
  };

  const onSubmit = handleSubmit((values) => {
    if (!product) {
      return;
    }
    adjustMutation.mutate(
      {
        id: product.id,
        payload: {
          changeType: values.changeType,
          quantity: Number(values.quantity),
          reason: values.reason || undefined,
        },
      },
      {
        onSuccess: (result) => {
          showToast(
            'success',
            `재고 ${result.quantity}개 (${STATUS_LABEL[result.productStatus] ?? result.productStatus})`,
          );
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
      title="재고 조정"
      description={product ? `${product.title} · 현재 재고 ${product.stockQuantity ?? 0}개` : undefined}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isBusy}>
            취소
          </Button>
          <Button type="submit" form="stock-adjust-form" disabled={isBusy}>
            저장
          </Button>
        </>
      }
    >
      <form id="stock-adjust-form" className="flex flex-col gap-4" onSubmit={onSubmit} noValidate>
        <FormError message={errors.root?.serverError?.message} />

        <Controller
          control={control}
          name="changeType"
          render={({ field }) => (
            <>
              <fieldset className="flex flex-col gap-2">
                <legend className="mb-1 text-sm font-medium text-content">변경 유형</legend>
                {CHANGE_TYPE_OPTIONS.map((option) => (
                  <label key={option.value} className="flex items-start gap-2.5 text-sm">
                    <input
                      type="radio"
                      name={field.name}
                      value={option.value}
                      checked={field.value === option.value}
                      onChange={() => field.onChange(option.value)}
                      className="mt-0.5 h-4 w-4 accent-content"
                    />
                    <span>
                      {option.label}
                      <span className="mt-0.5 block text-xs text-content-muted">{option.help}</span>
                    </span>
                  </label>
                ))}
              </fieldset>

              <Field
                htmlFor="quantity"
                label={field.value === 'ADJUST' ? '변경 후 재고' : '수량'}
                required
                error={errors.quantity?.message}
              >
                <Input
                  id="quantity"
                  inputMode="numeric"
                  invalid={Boolean(errors.quantity)}
                  {...register('quantity')}
                />
              </Field>
            </>
          )}
        />

        <Field htmlFor="reason" label="사유" error={errors.reason?.message}>
          <Input id="reason" placeholder="선택" invalid={Boolean(errors.reason)} {...register('reason')} />
        </Field>
      </form>
    </Modal>
  );
}
