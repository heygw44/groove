import { describe, expect, it } from 'vitest';

import { tasteProfileFormSchema, toTasteFormValues, toTastePayload } from '@/schemas/recommend';
import type { TasteProfile } from '@/types/recommend';

const validValues = {
  genreIds: [1],
  artists: [{ id: 1, name: '아이유', nameEn: 'IU' }],
  decades: ['D1990'] as const,
};

describe('tasteProfileFormSchema', () => {
  it('genreIds 가 0개면 실패한다', () => {
    // given & when
    const result = tasteProfileFormSchema.safeParse({ ...validValues, genreIds: [] });

    // then
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe('좋아하는 장르를 하나 이상 골라주세요.');
  });

  it('genreIds 가 6개면 실패한다', () => {
    // given
    const genreIds = [1, 2, 3, 4, 5, 6];

    // when
    const result = tasteProfileFormSchema.safeParse({ ...validValues, genreIds });

    // then
    expect(result.success).toBe(false);
  });

  it('artists 가 6명이면 실패한다', () => {
    // given
    const artists = Array.from({ length: 6 }, (_, index) => ({
      id: index + 1,
      name: `아티스트${index}`,
      nameEn: `Artist${index}`,
    }));

    // when
    const result = tasteProfileFormSchema.safeParse({ ...validValues, artists });

    // then
    expect(result.success).toBe(false);
  });

  it('decades 가 4개면 실패한다', () => {
    // given
    const decades = ['D1960', 'D1970', 'D1980', 'D1990'];

    // when
    const result = tasteProfileFormSchema.safeParse({ ...validValues, decades });

    // then
    expect(result.success).toBe(false);
  });

  it('유효한 값이면 통과한다', () => {
    // given & when
    const result = tasteProfileFormSchema.safeParse(validValues);

    // then
    expect(result.success).toBe(true);
  });
});

describe('toTasteFormValues / toTastePayload', () => {
  it('프로필 → 폼 값 → 요청 payload 로 왕복 변환된다', () => {
    // given
    const profile: TasteProfile = {
      genres: [{ id: 1, name: '록' }],
      artists: [{ id: 1, name: '아이유', nameEn: 'IU' }],
      decades: ['D1990'],
      updatedAt: '2026-09-06T00:00:00',
    };

    // when
    const formValues = toTasteFormValues(profile);
    const payload = toTastePayload(formValues);

    // then
    expect(formValues).toEqual({
      genreIds: [1],
      artists: [{ id: 1, name: '아이유', nameEn: 'IU' }],
      decades: ['D1990'],
    });
    expect(payload).toEqual({
      genreIds: [1],
      artistIds: [1],
      decades: ['D1990'],
    });
  });
});
