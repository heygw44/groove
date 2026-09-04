import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { useToast } from '@/components/common/toastContext';
import { useIssueCoupon } from '@/hooks/mutations/useCouponMutations';
import { couponIssueSchema, type CouponIssueFormValues } from '@/schemas/coupon';
import { applyFieldErrors, getErrorMessage } from '@/utils/apiError';

export function CouponIssueForm() {
  const { showToast } = useToast();
  const issueMutation = useIssueCoupon();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CouponIssueFormValues>({
    resolver: zodResolver(couponIssueSchema),
    mode: 'onBlur',
    defaultValues: { code: '' },
  });

  const { onChange, ...codeField } = register('code');

  const onSubmit = handleSubmit((values) => {
    issueMutation.mutate(values, {
      onSuccess: (data) => {
        showToast('success', `${data.couponName} 쿠폰이 발급되었습니다.`);
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
      <Field htmlFor="coupon-code" label="쿠폰 코드" error={errors.code?.message}>
        <div className="flex gap-2">
          <Input
            id="coupon-code"
            invalid={Boolean(errors.code)}
            autoCapitalize="characters"
            spellCheck={false}
            {...codeField}
            onChange={(e) => {
              e.target.value = e.target.value.toUpperCase();
              onChange(e);
            }}
          />
          <Button type="submit" disabled={isSubmitting} className="shrink-0">
            등록
          </Button>
        </div>
      </Field>
    </form>
  );
}
