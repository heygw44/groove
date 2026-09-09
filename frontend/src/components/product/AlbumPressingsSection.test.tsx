import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { AlbumPressingsSection } from '@/components/product/AlbumPressingsSection';
import { albumKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { AlbumDetail } from '@/types/catalog';
import type { ProductSummary } from '@/types/product';

const baseAlbum: Omit<AlbumDetail, 'pressings'> = {
  id: 10,
  title: 'Kind of Blue',
  artist: { id: 1, name: 'Miles Davis' },
};

// 앨범 상세의 프레싱 목록은 항상 otherPressingCount 0 으로 내려온다(대표 축약이 없는 전체 목록이라서).
const pressing = (overrides: Partial<ProductSummary> & Pick<ProductSummary, 'id' | 'title'>): ProductSummary => ({
  artistName: 'Miles Davis',
  price: 30000,
  status: 'ON_SALE',
  editionType: 'ORIGINAL',
  albumId: baseAlbum.id,
  otherPressingCount: 0,
  ...overrides,
});

const renderSection = (album: AlbumDetail, currentProductId: number, hasOtherPressings = true) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(albumKeys.detail(album.id), album);
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>
          <AlbumPressingsSection
            albumId={album.id}
            currentProductId={currentProductId}
            hasOtherPressings={hasOtherPressings}
            action={<button type="button">구독</button>}
          />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
};

describe('AlbumPressingsSection', () => {
  it('현재 상품은 목록에서 빠진다', () => {
    // given
    const album: AlbumDetail = {
      ...baseAlbum,
      pressings: [
        pressing({ id: 1, title: '현재 프레싱' }),
        pressing({ id: 2, title: '재발매 프레싱', price: 25000, editionType: 'REISSUE' }),
      ],
    };

    // when
    renderSection(album, 1);

    // then
    expect(screen.queryByText('현재 프레싱')).not.toBeInTheDocument();
    expect(screen.getByText('재발매 프레싱')).toBeInTheDocument();
  });

  it('다른 프레싱이 없어도 헤딩과 action 은 남는다', () => {
    // given
    const album: AlbumDetail = {
      ...baseAlbum,
      pressings: [pressing({ id: 1, title: '현재 프레싱' })],
    };

    // when
    renderSection(album, 1);

    // then
    expect(screen.getByText('이 앨범의 다른 에디션')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '구독' })).toBeInTheDocument();
    expect(screen.getByText('아직 다른 에디션이 없습니다.')).toBeInTheDocument();
  });

  it('hasOtherPressings 가 false 면 조회 없이 안내 문구를 보여준다', () => {
    // given
    const album: AlbumDetail = {
      ...baseAlbum,
      pressings: [
        pressing({ id: 1, title: '현재 프레싱' }),
        pressing({ id: 2, title: '재발매 프레싱', price: 25000, editionType: 'REISSUE' }),
      ],
    };

    // when
    renderSection(album, 1, false);

    // then
    expect(screen.getByText('이 앨범의 다른 에디션')).toBeInTheDocument();
    expect(screen.getByText('아직 다른 에디션이 없습니다.')).toBeInTheDocument();
    expect(screen.queryByText('재발매 프레싱')).not.toBeInTheDocument();
  });

  it('국가·연도·에디션 메타가 보인다', () => {
    // given
    const album: AlbumDetail = {
      ...baseAlbum,
      pressings: [
        pressing({ id: 1, title: '현재 프레싱' }),
        pressing({
          id: 2,
          title: '일본반',
          price: 40000,
          editionType: 'REISSUE',
          country: 'Japan',
          pressingYear: 1990,
        }),
      ],
    };

    // when
    renderSection(album, 1);

    // then
    expect(screen.getByText('일본 · 1990 · 재발매반')).toBeInTheDocument();
  });
});
