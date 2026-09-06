import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { DiggingSection } from '@/components/recommend/DiggingSection';
import { recommendKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { Member } from '@/types/member';
import type { HomeRecommendResponse } from '@/types/recommend';

const member: Member = {
  id: 1,
  email: 'user@groove.com',
  nickname: '레코드러버',
  role: 'USER',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00',
};

interface RenderOptions {
  loggedIn?: boolean;
  homeData?: HomeRecommendResponse;
}

const renderSection = ({ loggedIn = true, homeData }: RenderOptions = {}) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  if (homeData) {
    queryClient.setQueryData(recommendKeys.home, homeData);
  }
  useAuthStore.setState(
    loggedIn
      ? { accessToken: 't', member, isBootstrapping: false }
      : { accessToken: null, member: null, isBootstrapping: false },
  );

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>
          <DiggingSection />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
};

describe('DiggingSection', () => {
  it('비로그인이면 아무것도 보여주지 않는다', () => {
    // given & when
    renderSection({ loggedIn: false });

    // then
    expect(screen.queryByText('당신을 위한 디깅')).not.toBeInTheDocument();
    expect(screen.queryByText('취향을 알려주면 판을 골라드려요')).not.toBeInTheDocument();
  });

  it('profileRequired 면 취향 설정 유도 카드를 보여준다', () => {
    // given & when
    renderSection({ homeData: { profileRequired: true, items: [] } });

    // then
    expect(screen.getByText('취향을 알려주면 판을 골라드려요')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '취향 설정하기' })).toHaveAttribute(
      'href',
      '/mypage/taste',
    );
  });

  it('추천 상품과 이유 배지를 최대 2개까지 렌더한다', () => {
    // given
    const homeData: HomeRecommendResponse = {
      profileRequired: false,
      items: [
        {
          product: {
            id: 1,
            title: '판 A',
            artistName: '아티스트 A',
            price: 10000,
            status: 'ON_SALE',
            editionType: 'STANDARD',
            wishlisted: false,
          },
          reasons: ['TASTE_GENRE', 'TASTE_ARTIST', 'TASTE_DECADE'],
        },
        {
          product: {
            id: 2,
            title: '판 B',
            artistName: '아티스트 B',
            price: 20000,
            status: 'ON_SALE',
            editionType: 'STANDARD',
            wishlisted: false,
          },
          reasons: ['SAME_LABEL'],
        },
      ],
    };

    // when
    renderSection({ homeData });

    // then
    expect(screen.getByText('당신을 위한 디깅')).toBeInTheDocument();
    expect(screen.getByText('판 A')).toBeInTheDocument();
    expect(screen.getByText('판 B')).toBeInTheDocument();
    expect(screen.getByText('취향 장르')).toBeInTheDocument();
    expect(screen.getByText('취향 아티스트')).toBeInTheDocument();
    expect(screen.queryByText('취향 연대')).not.toBeInTheDocument();
    expect(screen.getByText('같은 레이블')).toBeInTheDocument();
  });
});
