import { client, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { SearchSuggestions } from '@/types/search';

export const getSearchSuggestions = (keyword: string) =>
  unwrap(
    client.get<ApiResponse<SearchSuggestions>>('/products/suggestions', { params: { keyword } }),
  );
