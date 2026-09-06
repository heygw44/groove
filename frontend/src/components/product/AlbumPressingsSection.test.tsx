import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { AlbumPressingsSection } from '@/components/product/AlbumPressingsSection';
import { albumKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { AlbumDetail } from '@/types/catalog';

const baseAlbum: Omit<AlbumDetail, 'pressings'> = {
  id: 10,
  title: 'Kind of Blue',
  artist: { id: 1, name: 'Miles Davis' },
};

const renderSection = (album: AlbumDetail, currentProductId: number) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(albumKeys.detail(album.id), album);
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>
          <AlbumPressingsSection albumId={album.id} currentProductId={currentProductId} />
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
        {
          id: 1,
          title: '현재 프레싱',
          artistName: 'Miles Davis',
          price: 30000,
          status: 'ON_SALE',
          editionType: 'ORIGINAL',
        },
        {
          id: 2,
          title: '재발매 프레싱',
          artistName: 'Miles Davis',
          price: 25000,
          status: 'ON_SALE',
          editionType: 'REISSUE',
        },
      ],
    };

    // when
    renderSection(album, 1);

    // then
    expect(screen.queryByText('현재 프레싱')).not.toBeInTheDocument();
    expect(screen.getByText('재발매 프레싱')).toBeInTheDocument();
  });

  it('다른 프레싱이 없으면 아무것도 렌더하지 않는다', () => {
    // given
    const album: AlbumDetail = {
      ...baseAlbum,
      pressings: [
        {
          id: 1,
          title: '현재 프레싱',
          artistName: 'Miles Davis',
          price: 30000,
          status: 'ON_SALE',
          editionType: 'ORIGINAL',
        },
      ],
    };

    // when
    renderSection(album, 1);

    // then
    expect(screen.queryByText('이 앨범의 다른 프레싱')).not.toBeInTheDocument();
  });

  it('국가·연도·에디션 메타가 보인다', () => {
    // given
    const album: AlbumDetail = {
      ...baseAlbum,
      pressings: [
        {
          id: 1,
          title: '현재 프레싱',
          artistName: 'Miles Davis',
          price: 30000,
          status: 'ON_SALE',
          editionType: 'ORIGINAL',
        },
        {
          id: 2,
          title: '일본반',
          artistName: 'Miles Davis',
          price: 40000,
          status: 'ON_SALE',
          editionType: 'REISSUE',
          country: 'Japan',
          pressingYear: 1990,
        },
      ],
    };

    // when
    renderSection(album, 1);

    // then
    expect(screen.getByText('일본 · 1990 · 재발매')).toBeInTheDocument();
  });
});
