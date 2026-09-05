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

  /** 정렬은 결과를 좁히는 조건이 아니라 보기 방식이라 초기화 대상에서 뺀다. */
  const reset = () => {
    setSearchParams((prev) =>
      serializeProductFilters({ sort: parseProductFilters(prev).sort, page: 0 }),
    );
  };

  return { filters, update, setPage, reset };
}
