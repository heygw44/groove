import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getProducts } from '@/api/product';
import { productKeys } from '@/hooks/queries/queryKeys';
import type { ProductListParams } from '@/types/product';

export const useProducts = (params: ProductListParams) =>
  useQuery({
    queryKey: productKeys.list(params),
    queryFn: () => getProducts(params),
    placeholderData: keepPreviousData,
  });
