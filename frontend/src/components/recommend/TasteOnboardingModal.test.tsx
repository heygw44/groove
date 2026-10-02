import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { TasteOnboardingModal } from '@/components/recommend/TasteOnboardingModal';
import { TASTE_ONBOARDING_DISMISSED_KEY, TASTE_ONBOARDING_SNOOZE_DAYS } from '@/constants/taste';
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
  role?: Member['role'];
}

const renderModal = ({ profile = null, path = '/', role = 'USER' }: RenderOptions = {}) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(tasteProfileKeys.mine, profile);
  useAuthStore.setState({ accessToken: 't', member: { ...member, role }, isBootstrapping: false });

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

// Node 의 실험적 전역 localStorage 가 jsdom 것을 가려 비어 있을 수 있어 메모리 구현으로 고정한다.
const createMemoryStorage = (): Storage => {
  const store = new Map<string, string>();
  return {
    get length() {
      return store.size;
    },
    clear: () => store.clear(),
    getItem: (key) => store.get(key) ?? null,
    key: (index) => Array.from(store.keys())[index] ?? null,
    removeItem: (key) => {
      store.delete(key);
    },
    setItem: (key, value) => {
      store.set(key, String(value));
    },
  };
};

beforeEach(() => {
  vi.stubGlobal('localStorage', createMemoryStorage());
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const DAY_MS = 24 * 60 * 60 * 1000;

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

  it('나중에를 누르면 닫히고 닫은 시각을 남긴다', async () => {
    // given
    const user = userEvent.setup();
    renderModal({ profile: null });
    const before = Date.now();

    // when
    await user.click(screen.getByRole('button', { name: '나중에' }));

    // then
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    const stored = Number(localStorage.getItem(TASTE_ONBOARDING_DISMISSED_KEY));
    expect(stored).toBeGreaterThanOrEqual(before);
    expect(stored).toBeLessThanOrEqual(Date.now());
  });

  it('유예 기간 안에 닫은 기록이 있으면 모달을 보여주지 않는다', () => {
    // given
    const dismissedAt = Date.now() - (TASTE_ONBOARDING_SNOOZE_DAYS - 1) * DAY_MS;
    localStorage.setItem(TASTE_ONBOARDING_DISMISSED_KEY, String(dismissedAt));

    // when
    renderModal({ profile: null });

    // then
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('유예 기간이 지나면 모달을 다시 보여준다', () => {
    // given
    const dismissedAt = Date.now() - (TASTE_ONBOARDING_SNOOZE_DAYS + 1) * DAY_MS;
    localStorage.setItem(TASTE_ONBOARDING_DISMISSED_KEY, String(dismissedAt));

    // when
    renderModal({ profile: null });

    // then
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('예전 방식의 세션 표시는 무시한다', () => {
    // given
    sessionStorage.setItem(TASTE_ONBOARDING_DISMISSED_KEY, '1');

    // when
    renderModal({ profile: null });

    // then
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('취향 관리 페이지에서는 모달을 보여주지 않는다', () => {
    // given & when
    renderModal({ profile: null, path: '/mypage/taste' });

    // then
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it.each(['/login', '/signup'])('인증 화면 %s 에서는 모달을 보여주지 않는다', (path) => {
    // given & when
    renderModal({ profile: null, path });

    // then
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it.each(['/', '/admin'])('관리자는 %s 에서 모달을 보지 않는다', (path) => {
    // given & when
    renderModal({ profile: null, path, role: 'ADMIN' });

    // then
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
