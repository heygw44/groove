import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { ToastContext } from '@/components/common/toastContext';
import { TasteProfileForm } from '@/components/recommend/TasteProfileForm';
import { referenceKeys } from '@/hooks/queries/queryKeys';
import type { Genre } from '@/types/product';

vi.mock('@/api/recommend', () => ({
  updateTasteProfile: vi.fn(),
}));

const GENRES: Genre[] = [
  { id: 1, name: '록' },
  { id: 2, name: '재즈' },
  { id: 3, name: '팝' },
];

const renderForm = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(referenceKeys.genres, GENRES);

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastContext.Provider value={{ showToast: vi.fn() }}>
        <TasteProfileForm />
      </ToastContext.Provider>
    </QueryClientProvider>,
  );
};

describe('TasteProfileForm', () => {
  it('장르를 고르지 않고 저장하면 검증 메시지를 보여준다', async () => {
    // given
    const user = userEvent.setup();
    renderForm();

    // when
    await user.click(screen.getByRole('button', { name: '저장' }));

    // then
    expect(await screen.findByText('좋아하는 장르를 하나 이상 골라주세요.')).toBeInTheDocument();
  });
});
