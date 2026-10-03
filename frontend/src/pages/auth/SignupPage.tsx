import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { useToast } from '@/components/common/toastContext';
import { useSignup } from '@/hooks/mutations/useAuthMutations';
import { signupSchema, type SignupFormValues } from '@/schemas/auth';
import { applyFieldErrors, getErrorMessage } from '@/utils/apiError';
import { buildLoginUrl } from '@/utils/loginUrl';

export default function SignupPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const signupMutation = useSignup();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
    mode: 'onBlur',
    defaultValues: { email: '', password: '', passwordConfirm: '', nickname: '' },
  });

  /* 로그인 화면에서 받은 redirect 를 이어 넘겨, 가입 후 로그인하면 원래 화면으로 돌아가게 한다. */
  const loginUrl = buildLoginUrl({ redirect: searchParams.get('redirect') ?? undefined });

  /*
   * mutateAsync 를 기다려야 응답이 올 때까지 isSubmitting 이 유지되고,
   * 버튼 loading 이 풀리지 않아 연속 클릭으로 가입 요청이 중복되지 않는다.
   */
  const onSubmit = handleSubmit(async (values) => {
    const payload = {
      email: values.email,
      password: values.password,
      nickname: values.nickname,
    };
    try {
      await signupMutation.mutateAsync(payload);
    } catch (error) {
      if (!applyFieldErrors(error, setError)) {
        setError('root.serverError', { message: getErrorMessage(error) });
      }
      return;
    }
    /* 가입 응답에 토큰이 없어 자동 로그인은 하지 않는다. */
    showToast('success', '가입이 완료되었습니다. 로그인해주세요.');
    navigate(loginUrl, { replace: true });
  });

  return (
    <div className="mx-auto w-full max-w-md px-4 py-12">
      <div className="rounded-lg border border-line bg-surface p-7">
        <h1 className="text-2xl font-bold tracking-tight">회원가입</h1>
        <p className="mt-1.5 text-sm text-content-muted">
          이메일과 비밀번호만으로 가입할 수 있습니다.
        </p>

        <form className="mt-6 flex flex-col gap-4" onSubmit={onSubmit} noValidate>
          <FormError message={errors.root?.serverError?.message} />

          <Field htmlFor="email" label="이메일" required error={errors.email?.message}>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="example@groove.kr"
              invalid={Boolean(errors.email)}
              {...register('email')}
            />
          </Field>

          <Field
            htmlFor="password"
            label="비밀번호"
            required
            help="8~20자"
            error={errors.password?.message}
          >
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              invalid={Boolean(errors.password)}
              {...register('password')}
            />
          </Field>

          <Field
            htmlFor="passwordConfirm"
            label="비밀번호 확인"
            required
            error={errors.passwordConfirm?.message}
          >
            <Input
              id="passwordConfirm"
              type="password"
              autoComplete="new-password"
              invalid={Boolean(errors.passwordConfirm)}
              {...register('passwordConfirm')}
            />
          </Field>

          <Field
            htmlFor="nickname"
            label="닉네임"
            required
            help="2~20자"
            error={errors.nickname?.message}
          >
            <Input
              id="nickname"
              autoComplete="nickname"
              invalid={Boolean(errors.nickname)}
              {...register('nickname')}
            />
          </Field>

          <Button type="submit" className="mt-1 w-full" loading={isSubmitting}>
            가입하기
          </Button>
        </form>

        <p className="mt-5 border-t border-line pt-4 text-center text-sm text-content-muted">
          이미 계정이 있으신가요? <Link to={loginUrl}>로그인</Link>
        </p>
      </div>
    </div>
  );
}
