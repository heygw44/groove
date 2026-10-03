import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import { AxiosError } from 'axios';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { useSignup } from '@/hooks/mutations/useAuthMutations';
import SignupPage from '@/pages/auth/SignupPage';

vi.mock('@/hooks/mutations/useAuthMutations', () => ({
  useSignup: vi.fn(),
}));

const showToast = vi.fn();
vi.mock('@/components/common/toastContext', () => ({
  useToast: () => ({ showToast }),
}));

function LocationProbe() {
  const location = useLocation();
  return <p data-testid="location">{`${location.pathname}${location.search}`}</p>;
}

const renderPage = (search: string) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/signup${search}`]}>
        <Routes>
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/login" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const mockSignup = (mutateAsync: (payload: unknown) => Promise<unknown>) => {
  const mock = vi.fn(mutateAsync);
  vi.mocked(useSignup).mockReturnValue({ mutateAsync: mock } as unknown as ReturnType<
    typeof useSignup
  >);
  return mock;
};

/* 스키마(8~20자)를 통과하는 테스트 입력값이다. */
const VALID_PW = 'password1234';

const fillValidForm = async (user: UserEvent) => {
  await user.type(screen.getByLabelText(/^이메일/), 'member@groove.com');
  await user.type(screen.getByLabelText(/^비밀번호(?! 확인)/), VALID_PW);
  await user.type(screen.getByLabelText(/^비밀번호 확인/), VALID_PW);
  await user.type(screen.getByLabelText(/^닉네임/), '그루브');
};

afterEach(() => {
  vi.clearAllMocks();
});

describe('SignupPage', () => {
  it('응답 전에 가입하기를 연달아 누르면 요청을 한 번만 보낸다', async () => {
    // given
    const mutateAsync = mockSignup(() => new Promise(() => {}));
    const user = userEvent.setup();
    renderPage('');
    await fillValidForm(user);
    const submit = screen.getByRole('button', { name: '가입하기' });

    // when
    await user.click(submit);
    await user.click(submit);

    // then
    expect(submit).toBeDisabled();
    expect(mutateAsync).toHaveBeenCalledTimes(1);
    expect(mutateAsync).toHaveBeenCalledWith({
      email: 'member@groove.com',
      password: VALID_PW,
      nickname: '그루브',
    });
  });

  it('가입에 성공하면 redirect 를 유지한 채 로그인 화면으로 이동한다', async () => {
    // given
    mockSignup(() => Promise.resolve(undefined));
    const user = userEvent.setup();
    renderPage('?redirect=/cart');
    await fillValidForm(user);

    // when
    await user.click(screen.getByRole('button', { name: '가입하기' }));

    // then
    expect(await screen.findByTestId('location')).toHaveTextContent('/login?redirect=%2Fcart');
    expect(showToast).toHaveBeenCalledWith('success', '가입이 완료되었습니다. 로그인해주세요.');
  });

  it('redirect 가 있으면 로그인 링크에 redirect 를 이어 붙인다', () => {
    // given
    mockSignup(() => Promise.resolve(undefined));

    // when
    renderPage('?redirect=/cart');

    // then
    expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute(
      'href',
      '/login?redirect=%2Fcart',
    );
  });

  it('redirect 가 없으면 로그인 링크는 /login 이다', () => {
    // given
    mockSignup(() => Promise.resolve(undefined));

    // when
    renderPage('');

    // then
    expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login');
  });

  it('이미 가입된 이메일로 거절되면 이메일 필드에 안내를 보여주고 이동하지 않는다', async () => {
    // given
    const error = new AxiosError('Request failed', undefined, undefined, undefined, {
      status: 409,
      data: { error: { code: 'MEMBER_EMAIL_DUPLICATE', message: 'server message' } },
    } as never);
    mockSignup(() => Promise.reject(error));
    const user = userEvent.setup();
    renderPage('');
    await fillValidForm(user);

    // when
    await user.click(screen.getByRole('button', { name: '가입하기' }));

    // then
    expect(await screen.findByRole('alert')).toHaveTextContent('이미 가입된 이메일입니다.');
    expect(screen.getByLabelText(/^이메일/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByTestId('location')).not.toBeInTheDocument();
    expect(showToast).not.toHaveBeenCalled();
  });

  it('필드에 귀속되지 않는 오류로 거절되면 폼 상단에 안내를 보여준다', async () => {
    // given
    const error = new AxiosError('Request failed', undefined, undefined, undefined, {
      status: 429,
      data: { error: { code: 'AUTH_RATE_LIMITED', message: 'server message' } },
    } as never);
    mockSignup(() => Promise.reject(error));
    const user = userEvent.setup();
    renderPage('');
    await fillValidForm(user);

    // when
    await user.click(screen.getByRole('button', { name: '가입하기' }));

    // then
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '요청이 너무 많습니다. 잠시 후 다시 시도해주세요.',
    );
    expect(screen.getByRole('button', { name: '가입하기' })).toBeEnabled();
  });
});
