import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { searchArtists } from '@/api/reference';
import { ArtistChipPicker } from '@/components/recommend/ArtistChipPicker';
import type { Artist } from '@/types/product';

vi.mock('@/api/reference', () => ({
  searchArtists: vi.fn(),
}));

const AMON: Artist = { id: 1, name: 'Amon Tobin' };
const IU: Artist = { id: 2, name: '아이유', nameEn: 'IU' };

const renderPicker = (value: Artist[] = [], max = 5) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const onChange = vi.fn();
  render(
    <QueryClientProvider client={queryClient}>
      <ArtistChipPicker value={value} onChange={onChange} max={max} />
    </QueryClientProvider>,
  );
  return { onChange };
};

describe('ArtistChipPicker', () => {
  it('검색어를 입력하면 검색 결과를 보여준다', async () => {
    // given
    vi.mocked(searchArtists).mockResolvedValue([AMON]);
    const user = userEvent.setup();
    renderPicker();

    // when
    await user.click(screen.getByRole('combobox'));
    await user.type(screen.getByRole('combobox'), 'Tobin');

    // then
    expect(await screen.findByRole('button', { name: 'Amon Tobin' }, { timeout: 2000 })).toBeInTheDocument();
  });

  it('검색 결과를 고르면 칩으로 보여주고 onChange 를 호출한다', async () => {
    // given
    vi.mocked(searchArtists).mockResolvedValue([AMON]);
    const user = userEvent.setup();
    const { onChange } = renderPicker();

    // when
    await user.click(screen.getByRole('combobox'));
    const candidate = await screen.findByRole('button', { name: 'Amon Tobin' }, { timeout: 2000 });
    await user.click(candidate);

    // then
    expect(onChange).toHaveBeenCalledWith([AMON]);
  });

  it('이미 선택된 아티스트는 검색 결과에서 제외한다', async () => {
    // given
    vi.mocked(searchArtists).mockResolvedValue([AMON, IU]);
    const user = userEvent.setup();
    renderPicker([AMON]);

    // when
    await user.click(screen.getByRole('combobox'));

    // then
    expect(await screen.findByRole('button', { name: '아이유' }, { timeout: 2000 })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Amon Tobin' })).not.toBeInTheDocument();
  });

  it('선택된 칩의 해제 버튼을 누르면 제거된 목록으로 onChange 를 호출한다', async () => {
    // given
    const user = userEvent.setup();
    const { onChange } = renderPicker([AMON, IU]);

    // when
    await user.click(screen.getByRole('button', { name: 'Amon Tobin 해제' }));

    // then
    expect(onChange).toHaveBeenCalledWith([IU]);
  });

  it('최대 개수에 도달하면 입력을 비활성화한다', () => {
    // given & when
    renderPicker([AMON, IU], 2);

    // then
    expect(screen.getByRole('combobox')).toBeDisabled();
    expect(screen.getByPlaceholderText('최대 2명까지 고를 수 있어요')).toBeInTheDocument();
  });
});
