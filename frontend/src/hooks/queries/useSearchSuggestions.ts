import { useQuery } from '@tanstack/react-query';

import { getSearchSuggestions } from '@/api/search';
import { searchKeys } from '@/hooks/queries/queryKeys';

export const useSearchSuggestions = (keyword: string, enabled: boolean) =>
  useQuery({
    queryKey: searchKeys.suggestions(keyword),
    queryFn: () => getSearchSuggestions(keyword),
    enabled,
  });
