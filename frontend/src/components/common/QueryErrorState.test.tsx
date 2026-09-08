import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError } from 'axios';
import { describe, expect, it, vi } from 'vitest';

import { QueryErrorState } from '@/components/common/QueryErrorState';
import type { ApiError } from '@/types/api';

const toAxiosError = (error: ApiError) =>
  new AxiosError('Request failed', undefined, undefined, undefined, {
    data: { error },
  } as never);

describe('QueryErrorState', () => {
  it('매핑된 에러 코드는 ERROR_MESSAGES 의 한글 문구를 보여준다', () => {
    // given
    const error = toAxiosError({ code: 'PRODUCT_NOT_FOUND', message: 'not found' });

    // when
    render(<QueryErrorState error={error} />);

    // then
    expect(screen.getByText('삭제되었거나 존재하지 않는 상품입니다.')).toBeInTheDocument();
  });

  it('onRetry 가 있으면 다시 시도 버튼이 뜨고 클릭 시 호출된다', async () => {
    // given
    const user = userEvent.setup();
    const onRetry = vi.fn();
    const error = toAxiosError({ code: 'COMMON_INTERNAL_ERROR', message: 'error' });

    // when
    render(<QueryErrorState error={error} onRetry={onRetry} />);
    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    // then
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('onRetry 가 없으면 다시 시도 버튼이 없다', () => {
    // given
    const error = toAxiosError({ code: 'COMMON_INTERNAL_ERROR', message: 'error' });

    // when
    render(<QueryErrorState error={error} />);

    // then
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument();
  });
});
