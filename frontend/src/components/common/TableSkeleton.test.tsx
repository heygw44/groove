import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { TableSkeleton } from '@/components/common/TableSkeleton';

describe('TableSkeleton', () => {
  it('요청한 행과 열만큼 자리를 잡는다', () => {
    // given & when
    render(<TableSkeleton columns={7} rows={3} />);

    // then
    expect(screen.getAllByRole('row')).toHaveLength(3);
    expect(screen.getAllByRole('cell')).toHaveLength(21);
  });

  it('rows 를 생략하면 5행을 그린다', () => {
    // given & when
    render(<TableSkeleton columns={2} />);

    // then
    expect(screen.getAllByRole('row')).toHaveLength(5);
  });
});
