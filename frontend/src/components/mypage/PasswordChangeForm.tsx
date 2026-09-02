import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { useToast } from '@/components/common/Toast';
import { useChangePassword } from '@/hooks/mutations/useMemberMutations';
import { passwordChangeSchema, type PasswordChangeFormValues } from '@/schemas/member';
import { applyFieldErrors, getErrorMessage } from '@/utils/apiError';

export function PasswordChangeForm() {
  const { showToast } = useToast();
  const changeMutation = useChangePassword();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<PasswordChangeFormValues>({
    resolver: zodResolver(passwordChangeSchema),
    mode: 'onBlur',
    defaultValues: { currentPassword: '', newPassword: '', newPasswordConfirm: '' },
  });

  const onSubmit = handleSubmit((values) => {
    const payload = {
      currentPassword: values.currentPassword,
      newPassword: values.newPassword,
    };
    changeMutation.mutate(payload, {
      onSuccess: () => {
        showToast('success', '비밀번호를 변경했습니다.');
        reset();
      },
      onError: (error) => {
        if (!applyFieldErrors(error, setError)) {
          setError('root.serverError', { message: getErrorMessage(error) });
        }
      },
    });
  });

  return (
    <form className="flex max-w-sm flex-col gap-4" onSubmit={onSubmit} noValidate>
      <FormError message={errors.root?.serverError?.message} />

      <Field
        htmlFor="currentPassword"
        label="현재 비밀번호"
        error={errors.currentPassword?.message}
      >
        <Input
          id="currentPassword"
          type="password"
          autoComplete="current-password"
          invalid={Boolean(errors.currentPassword)}
          {...register('currentPassword')}
        />
      </Field>

      <Field
        htmlFor="newPassword"
        label="새 비밀번호"
        help="8~20자"
        error={errors.newPassword?.message}
      >
        <Input
          id="newPassword"
          type="password"
          autoComplete="new-password"
          invalid={Boolean(errors.newPassword)}
          {...register('newPassword')}
        />
      </Field>

      <Field
        htmlFor="newPasswordConfirm"
        label="새 비밀번호 확인"
        error={errors.newPasswordConfirm?.message}
      >
        <Input
          id="newPasswordConfirm"
          type="password"
          autoComplete="new-password"
          invalid={Boolean(errors.newPasswordConfirm)}
          {...register('newPasswordConfirm')}
        />
      </Field>

      <div>
        <Button type="submit" disabled={isSubmitting}>
          비밀번호 변경
        </Button>
      </div>
    </form>
  );
}
