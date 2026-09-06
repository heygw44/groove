import { describe, expect, it } from 'vitest';

import {
  catalogImportJobStartFormSchema,
  EMPTY_CATALOG_IMPORT_JOB_START_FORM_VALUES,
  toCatalogImportJobStartPayload,
  type CatalogImportJobStartFormValues,
} from '@/schemas/catalog';

const formValues = (
  overrides: Partial<CatalogImportJobStartFormValues> = {},
): CatalogImportJobStartFormValues => ({
  ...EMPTY_CATALOG_IMPORT_JOB_START_FORM_VALUES,
  discogsMasterId: '12345',
  defaultPrice: '35000',
  ...overrides,
});

describe('catalogImportJobStartFormSchema', () => {
  it('마스터 ID가 비어있으면 실패한다', () => {
    // given
    const values = formValues({ discogsMasterId: '' });

    // when & then
    expect(catalogImportJobStartFormSchema.safeParse(values).success).toBe(false);
  });

  it('마스터 ID가 0이면 실패한다', () => {
    // given
    const values = formValues({ discogsMasterId: '0' });

    // when & then
    expect(catalogImportJobStartFormSchema.safeParse(values).success).toBe(false);
  });

  it('기본가가 숫자가 아니면 실패한다', () => {
    // given
    const values = formValues({ defaultPrice: '삼만오천' });

    // when & then
    expect(catalogImportJobStartFormSchema.safeParse(values).success).toBe(false);
  });

  it('기본가가 0이면 실패한다', () => {
    // given
    const values = formValues({ defaultPrice: '0' });

    // when & then
    expect(catalogImportJobStartFormSchema.safeParse(values).success).toBe(false);
  });

  it('모든 값이 유효하면 통과한다', () => {
    // given
    const values = formValues();

    // when & then
    expect(catalogImportJobStartFormSchema.safeParse(values).success).toBe(true);
  });
});

describe('toCatalogImportJobStartPayload()', () => {
  it('문자열 값을 숫자로 변환한다', () => {
    // given
    const values = formValues({ discogsMasterId: '999', defaultPrice: '42000' });

    // when
    const payload = toCatalogImportJobStartPayload(values);

    // then
    expect(payload).toEqual({ discogsMasterId: 999, defaultPrice: 42000 });
  });
});
