import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { useLogin } from '@/hooks/mutations/useAuthMutations';
import { loginSchema, type LoginFormValues } from '@/schemas/auth';
import { applyFieldErrors, getErrorMessage } from '@/utils/apiError';

export default function LoginPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const loginMutation = useLogin();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    mode: 'onBlur',
    defaultValues: { email: '', password: '' },
  });

  const onSubmit = handleSubmit((values) => {
    loginMutation.mutate(values, {
      onSuccess: () => navigate(searchParams.get('redirect') ?? '/', { replace: true }),
      onError: (error) => {
        if (!applyFieldErrors(error, setError)) {
          setError('root.serverError', { message: getErrorMessage(error) });
        }
      },
    });
  });

  return (
    <div className="mx-auto w-full max-w-md px-4 py-12">
      <div className="rounded-lg border border-line bg-surface p-7">
        <h1 className="text-2xl font-bold tracking-tight">로그인</h1>
        <p className="mt-1.5 text-sm text-content-muted">
          주문 내역과 배송지를 관리하려면 로그인하세요.
        </p>

        <form className="mt-6 flex flex-col gap-4" onSubmit={onSubmit} noValidate>
          <FormError message={errors.root?.serverError?.message} />

          <Field htmlFor="email" label="이메일" error={errors.email?.message}>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="example@groove.kr"
              invalid={Boolean(errors.email)}
              {...register('email')}
            />
          </Field>

          <Field htmlFor="password" label="비밀번호" error={errors.password?.message}>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              invalid={Boolean(errors.password)}
              {...register('password')}
            />
          </Field>

          <Button type="submit" className="mt-1 w-full" disabled={isSubmitting}>
            로그인
          </Button>
        </form>

        <p className="mt-5 border-t border-line pt-4 text-center text-sm text-content-muted">
          아직 계정이 없으신가요? <Link to="/signup">회원가입</Link>
        </p>
      </div>
    </div>
  );
}
