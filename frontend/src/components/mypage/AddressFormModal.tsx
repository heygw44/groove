import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { useToast } from '@/components/common/Toast';
import { useCreateAddress, useUpdateAddress } from '@/hooks/mutations/useAddressMutations';
import { addressSchema, addressUpdateSchema, type AddressFormValues } from '@/schemas/address';
import type { Address } from '@/types/member';
import { applyFieldErrors, getErrorMessage } from '@/utils/apiError';

interface AddressFormModalProps {
  open: boolean;
  onClose: () => void;
  /** 있으면 수정, 없으면 추가. */
  address?: Address;
}

const EMPTY_VALUES: AddressFormValues = {
  recipientName: '',
  phone: '',
  zipCode: '',
  address1: '',
  address2: '',
  isDefault: false,
};

const toFormValues = (address?: Address): AddressFormValues =>
  address
    ? {
        recipientName: address.recipientName,
        phone: address.phone,
        zipCode: address.zipCode,
        address1: address.address1,
        address2: address.address2 ?? '',
        isDefault: address.isDefault,
      }
    : EMPTY_VALUES;

export function AddressFormModal({ open, onClose, address }: AddressFormModalProps) {
  const isEdit = Boolean(address);
  const { showToast } = useToast();
  const createMutation = useCreateAddress();
  const updateMutation = useUpdateAddress();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AddressFormValues>({
    resolver: zodResolver(addressSchema),
    mode: 'onBlur',
    defaultValues: EMPTY_VALUES,
  });

  useEffect(() => {
    if (open) {
      reset(toFormValues(address));
    }
  }, [open, address, reset]);

  const handleError = (error: unknown) => {
    if (!applyFieldErrors(error, setError)) {
      setError('root.serverError', { message: getErrorMessage(error) });
    }
  };

  const onSubmit = handleSubmit((values) => {
    const done = (message: string) => {
      showToast('success', message);
      onClose();
    };

    if (address) {
      /* PATCH 요청에는 isDefault 가 없다. 스키마로 걸러 내보낸다. */
      const payload = addressUpdateSchema.parse(values);
      updateMutation.mutate(
        { addressId: address.id, payload },
        { onSuccess: () => done('배송지를 수정했습니다.'), onError: handleError },
      );
      return;
    }

    createMutation.mutate(values, {
      onSuccess: () => done('배송지를 추가했습니다.'),
      onError: handleError,
    });
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? '배송지 수정' : '배송지 추가'}
      description={isEdit ? undefined : '주문할 때 바로 선택할 수 있습니다.'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            취소
          </Button>
          <Button type="submit" form="address-form" disabled={isSubmitting}>
            저장
          </Button>
        </>
      }
    >
      <form id="address-form" className="flex flex-col gap-4" onSubmit={onSubmit} noValidate>
        <FormError message={errors.root?.serverError?.message} />

        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            htmlFor="recipientName"
            label="수령인"
            required
            error={errors.recipientName?.message}
          >
            <Input
              id="recipientName"
              autoComplete="name"
              invalid={Boolean(errors.recipientName)}
              {...register('recipientName')}
            />
          </Field>

          <Field htmlFor="phone" label="연락처" required error={errors.phone?.message}>
            <Input
              id="phone"
              inputMode="tel"
              placeholder="010-1234-5678"
              invalid={Boolean(errors.phone)}
              {...register('phone')}
            />
          </Field>
        </div>

        <div className="sm:max-w-44">
          <Field htmlFor="zipCode" label="우편번호" required error={errors.zipCode?.message}>
            <Input
              id="zipCode"
              inputMode="numeric"
              placeholder="06236"
              invalid={Boolean(errors.zipCode)}
              {...register('zipCode')}
            />
          </Field>
        </div>

        <Field htmlFor="address1" label="기본 주소" required error={errors.address1?.message}>
          <Input
            id="address1"
            autoComplete="street-address"
            invalid={Boolean(errors.address1)}
            {...register('address1')}
          />
        </Field>

        <Field htmlFor="address2" label="상세 주소" error={errors.address2?.message}>
          <Input
            id="address2"
            placeholder="동 · 호수 등 (선택)"
            invalid={Boolean(errors.address2)}
            {...register('address2')}
          />
        </Field>

        {/* 기본 배송지는 추가할 때만 정한다. 수정 요청에는 그 필드가 없다. */}
        {!isEdit && (
          <label className="flex items-start gap-2.5 text-sm">
            <input
              type="checkbox"
              className="mt-1 h-4 w-4 accent-content"
              {...register('isDefault')}
            />
            <span>
              기본 배송지로 지정
              <span className="mt-0.5 block text-xs text-content-muted">
                첫 배송지는 선택과 무관하게 기본 배송지가 됩니다.
              </span>
            </span>
          </label>
        )}
      </form>
    </Modal>
  );
}
