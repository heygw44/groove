import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { useToast } from '@/components/common/toastContext';
import { useUpdateNickname } from '@/hooks/mutations/useMemberMutations';
import { nicknameSchema, type NicknameFormValues } from '@/schemas/member';
import { applyFieldErrors, getErrorMessage } from '@/utils/apiError';

interface NicknameFormProps {
  nickname: string;
}

export function NicknameForm({ nickname }: NicknameFormProps) {
  const { showToast } = useToast();
  const updateMutation = useUpdateNickname();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<NicknameFormValues>({
    resolver: zodResolver(nicknameSchema),
    mode: 'onBlur',
    defaultValues: { nickname },
  });

  /* 조회가 늦게 끝나거나 다른 곳에서 바뀌면 폼도 따라간다. */
  useEffect(() => {
    reset({ nickname });
  }, [nickname, reset]);

  const onSubmit = handleSubmit((values) => {
    updateMutation.mutate(values, {
      onSuccess: () => showToast('success', '닉네임을 변경했습니다.'),
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
      <Field htmlFor="nickname" label="닉네임" help="2~20자" error={errors.nickname?.message}>
        <Input id="nickname" invalid={Boolean(errors.nickname)} {...register('nickname')} />
      </Field>
      <div>
        <Button type="submit" disabled={isSubmitting}>
          저장
        </Button>
      </div>
    </form>
  );
}
