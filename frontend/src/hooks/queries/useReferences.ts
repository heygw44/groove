import { useQuery } from '@tanstack/react-query';

import { getGenres, getLabels, searchArtists } from '@/api/reference';
import { referenceKeys } from '@/hooks/queries/queryKeys';

const FIVE_MINUTES = 5 * 60 * 1000;

export const useGenres = () =>
  useQuery({
    queryKey: referenceKeys.genres,
    queryFn: getGenres,
    staleTime: FIVE_MINUTES,
  });

export const useLabels = () =>
  useQuery({
    queryKey: referenceKeys.labels,
    queryFn: getLabels,
    staleTime: FIVE_MINUTES,
  });

export const useArtists = (keyword: string | undefined, enabled: boolean) =>
  useQuery({
    queryKey: referenceKeys.artists(keyword),
    queryFn: () => searchArtists(keyword),
    enabled,
  });
