import { useSearchParams } from 'react-router-dom';

import {
  parseProductFilters,
  serializeProductFilters,
  type ProductListFilters,
} from '@/utils/productFilters';

interface UpdateOptions {
  replace?: boolean;
}

export function useProductFilters() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = parseProductFilters(searchParams);

  /** 필터가 바뀌면 이전 필터의 페이지 번호가 더 이상 유효하지 않으니 0으로 되돌린다. */
  const update = (patch: Partial<ProductListFilters>, options: UpdateOptions = {}) => {
    setSearchParams(
      (prev) =>
        serializeProductFilters({
          ...parseProductFilters(prev),
          ...patch,
          page: patch.page ?? 0,
        }),
      { replace: options.replace ?? false },
    );
  };

  const setPage = (page: number) => {
    setSearchParams((prev) => serializeProductFilters({ ...parseProductFilters(prev), page }));
  };

  const reset = () => {
    setSearchParams(new URLSearchParams());
  };

  return { filters, update, setPage, reset };
}
