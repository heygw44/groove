import { z } from 'zod';

import {
  DECADES,
  TASTE_ARTIST_MAX,
  TASTE_DECADE_MAX,
  TASTE_GENRE_MAX,
  TASTE_GENRE_MIN,
} from '@/constants/taste';
import type { TasteProfile, TasteProfileUpdateRequest } from '@/types/recommend';

export const tasteProfileFormSchema = z.object({
  genreIds: z
    .array(z.number().int())
    .min(TASTE_GENRE_MIN, '좋아하는 장르를 하나 이상 골라주세요.')
    .max(TASTE_GENRE_MAX, `장르는 최대 ${TASTE_GENRE_MAX}개까지 고를 수 있습니다.`),
  artists: z
    .array(z.object({ id: z.number().int(), name: z.string(), nameEn: z.string().optional() }))
    .max(TASTE_ARTIST_MAX, `아티스트는 최대 ${TASTE_ARTIST_MAX}명까지 고를 수 있습니다.`),
  decades: z
    .array(z.enum(DECADES))
    .max(TASTE_DECADE_MAX, `연대는 최대 ${TASTE_DECADE_MAX}개까지 고를 수 있습니다.`),
});

export type TasteProfileFormValues = z.infer<typeof tasteProfileFormSchema>;

export const EMPTY_TASTE_FORM_VALUES: TasteProfileFormValues = {
  genreIds: [],
  artists: [],
  decades: [],
};

export const toTasteFormValues = (profile: TasteProfile): TasteProfileFormValues => ({
  genreIds: profile.genres.map((genre) => genre.id),
  artists: profile.artists,
  decades: profile.decades,
});

export const toTastePayload = (values: TasteProfileFormValues): TasteProfileUpdateRequest => ({
  genreIds: values.genreIds,
  artistIds: values.artists.map((artist) => artist.id),
  decades: values.decades,
});
