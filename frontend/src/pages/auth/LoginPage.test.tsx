import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError } from 'axios';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useLogin } from '@/hooks/mutations/useAuthMutations';
import LoginPage from '@/pages/auth/LoginPage';

vi.mock('@/hooks/mutations/useAuthMutations', () => ({
  useLogin: vi.fn(),
}));

const renderPage = (search: string) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/login${search}`]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

beforeEach(() => {
  vi.mocked(useLogin).mockReturnValue({ mutate: vi.fn() } as unknown as ReturnType<
    typeof useLogin
  >);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('LoginPage', () => {
  it('reason=password-changed 면 재로그인 안내를 보여준다', () => {
    // when
    renderPage('?reason=password-changed');

    // then
    expect(screen.getByRole('status')).toHaveTextContent(
      '비밀번호가 변경되어 다시 로그인해 주세요.',
    );
  });

  it('reason=idle 이면 유휴 로그아웃 안내를 보여준다', () => {
    // when
    renderPage('?reason=idle');

    // then
    expect(screen.getByRole('status')).toHaveTextContent(
      '오랫동안 활동이 없어 로그아웃되었습니다. 다시 로그인해 주세요.',
    );
  });

  it('reason=expired 면 세션 만료 안내를 보여준다', () => {
    // when
    renderPage('?reason=expired');

    // then
    expect(screen.getByRole('status')).toHaveTextContent(
      '로그인 유지 기간이 끝났습니다. 다시 로그인해 주세요.',
    );
  });

  it('reason 이 없으면 안내를 보여주지 않는다', () => {
    // when
    renderPage('');

    // then
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('알 수 없는 reason 이면 안내를 보여주지 않는다', () => {
    // when
    renderPage('?reason=unknown');

    // then
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it.each([
    [
      'AUTH_LOGIN_LOCKED',
      '로그인에 여러 번 실패해 로그인이 일시적으로 제한되었습니다. 잠시 후 다시 시도해주세요.',
    ],
    ['AUTH_RATE_LIMITED', '요청이 너무 많습니다. 잠시 후 다시 시도해주세요.'],
  ])('%s 로 거절되면 제한 안내를 보여준다', async (code, message) => {
    // given
    const error = new AxiosError('Request failed', undefined, undefined, undefined, {
      status: 429,
      data: { error: { code, message: 'server message' } },
    } as never);
    const mutate = vi.fn((_values: unknown, options: { onError: (e: unknown) => void }) =>
      options.onError(error),
    );
    vi.mocked(useLogin).mockReturnValue({ mutate } as unknown as ReturnType<typeof useLogin>);
    const user = userEvent.setup();
    renderPage('');

    // when
    await user.type(screen.getByLabelText('이메일'), 'member@groove.com');
    await user.type(screen.getByLabelText('비밀번호'), 'password1234!');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    // then
    expect(await screen.findByRole('alert')).toHaveTextContent(message);
  });
});
