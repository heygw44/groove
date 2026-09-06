import type { Decade } from '@/types/recommend';

export const DECADES = [
  'D1960',
  'D1970',
  'D1980',
  'D1990',
  'D2000',
  'D2010',
  'D2020',
] as const satisfies readonly Decade[];

export const DECADE_LABELS: Record<Decade, string> = {
  D1960: '1960년대',
  D1970: '1970년대',
  D1980: '1980년대',
  D1990: '1990년대',
  D2000: '2000년대',
  D2010: '2010년대',
  D2020: '2020년대',
};

export const TASTE_GENRE_MIN = 1;
export const TASTE_GENRE_MAX = 5;
export const TASTE_ARTIST_MAX = 5;
export const TASTE_DECADE_MAX = 3;

export const TASTE_ONBOARDING_DISMISSED_KEY = 'groove:taste-onboarding-dismissed';
