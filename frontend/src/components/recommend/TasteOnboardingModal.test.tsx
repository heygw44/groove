import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { TasteOnboardingModal } from '@/components/recommend/TasteOnboardingModal';
import { TASTE_ONBOARDING_DISMISSED_KEY } from '@/constants/taste';
import { tasteProfileKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { Member } from '@/types/member';
import type { TasteProfile } from '@/types/recommend';

vi.mock('@/api/reference', () => ({
  getGenres: vi.fn().mockResolvedValue([]),
}));

const member: Member = {
  id: 1,
  email: 'user@groove.com',
  nickname: '레코드러버',
  role: 'USER',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00',
};

const profileFixture: TasteProfile = {
  genres: [],
  artists: [],
  decades: [],
  updatedAt: '2026-01-01T00:00:00',
};

interface RenderOptions {
  profile?: TasteProfile | null;
  path?: string;
}

const renderModal = ({ profile = null, path = '/' }: RenderOptions = {}) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(tasteProfileKeys.mine, profile);
  useAuthStore.setState({ accessToken: 't', member, isBootstrapping: false });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[path]}>
          <TasteOnboardingModal />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
};

beforeEach(() => {
  sessionStorage.clear();
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
});

describe('TasteOnboardingModal', () => {
  it('취향 프로필이 없으면 모달을 보여준다', () => {
    // given & when
    renderModal({ profile: null });

    // then
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('취향 프로필이 있으면 모달을 보여주지 않는다', () => {
    // given & when
    renderModal({ profile: profileFixture });

    // then
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('나중에를 누르면 닫히고 세션에 표시를 남긴다', async () => {
    // given
    const user = userEvent.setup();
    renderModal({ profile: null });

    // when
    await user.click(screen.getByRole('button', { name: '나중에' }));

    // then
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(sessionStorage.getItem(TASTE_ONBOARDING_DISMISSED_KEY)).toBe('1');
  });

  it('세션에 이미 표시가 있으면 모달을 보여주지 않는다', () => {
    // given
    sessionStorage.setItem(TASTE_ONBOARDING_DISMISSED_KEY, '1');

    // when
    renderModal({ profile: null });

    // then
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('취향 관리 페이지에서는 모달을 보여주지 않는다', () => {
    // given & when
    renderModal({ profile: null, path: '/mypage/taste' });

    // then
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
