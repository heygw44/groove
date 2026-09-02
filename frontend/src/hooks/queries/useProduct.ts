import { useQuery } from '@tanstack/react-query';

import { getProduct } from '@/api/product';
import { productKeys } from '@/hooks/queries/queryKeys';

export const useProduct = (id: number) =>
  useQuery({
    queryKey: productKeys.detail(id),
    queryFn: () => getProduct(id),
    enabled: Number.isInteger(id) && id > 0,
  });
