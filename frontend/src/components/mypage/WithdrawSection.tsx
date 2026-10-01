import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { WITHDRAW_NOTICE } from '@/constants/member';
import { useWithdraw } from '@/hooks/mutations/useMemberMutations';
import { withdrawSchema, type WithdrawFormValues } from '@/schemas/member';
import { getErrorMessage } from '@/utils/apiError';

const FORM_ID = 'withdraw-form';

export function WithdrawSection() {
  const [isConfirmOpen, setIsConfirmOpen] = useState(false);
  const withdrawMutation = useWithdraw();
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<WithdrawFormValues>({
    resolver: zodResolver(withdrawSchema),
    defaultValues: { password: '' },
  });

  const closeDialog = () => {
    setIsConfirmOpen(false);
    reset();
  };

  const onSubmit = handleSubmit((values) => {
    /* 성공하면 페이지가 새로 열리므로 토스트를 띄울 자리가 없다. */
    withdrawMutation.mutate(values.password, {
      onError: (error) => {
        setError('root.serverError', { message: getErrorMessage(error) });
      },
    });
  });

  return (
    <section className="rounded-lg border border-danger-line bg-danger-soft">
      <div className="px-7 pt-5">
        <h2 className="text-[15px] font-bold text-danger">회원 탈퇴</h2>
      </div>
      <div className="flex flex-col gap-4 px-7 pt-2.5 pb-6 sm:flex-row sm:items-end sm:justify-between">
        <p className="max-w-lg text-sm text-danger/85">탈퇴하면 {WITHDRAW_NOTICE}</p>
        <div>
          <Button variant="danger" onClick={() => setIsConfirmOpen(true)}>
            회원 탈퇴
          </Button>
        </div>
      </div>

      <Modal
        open={isConfirmOpen}
        onClose={closeDialog}
        dismissible={!withdrawMutation.isPending}
        title="정말 탈퇴하시겠습니까?"
        description={WITHDRAW_NOTICE}
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={closeDialog} disabled={withdrawMutation.isPending}>
              취소
            </Button>
            <Button
              type="submit"
              form={FORM_ID}
              variant="danger"
              loading={withdrawMutation.isPending}
            >
              탈퇴하기
            </Button>
          </>
        }
      >
        <form id={FORM_ID} className="flex flex-col gap-4" onSubmit={onSubmit} noValidate>
          <FormError message={errors.root?.serverError?.message} />
          <Field htmlFor="withdrawPassword" label="비밀번호 확인" error={errors.password?.message}>
            <Input
              id="withdrawPassword"
              type="password"
              autoComplete="current-password"
              invalid={Boolean(errors.password)}
              {...register('password')}
            />
          </Field>
        </form>
      </Modal>
    </section>
  );
}
