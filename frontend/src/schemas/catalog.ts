import { z } from 'zod';

import type { CatalogImportJobRequest } from '@/types/catalog';

/*
 * 폼 값은 전부 문자열이다(adminLimitedDrop.ts 와 같은 이유 - zod transform 을 쓰면
 * 폼용/페이로드용 두 제네릭을 오가게 되어 useForm<T> 하나로 못 쓴다).
 */
export const catalogImportJobStartFormSchema = z
  .object({
    discogsMasterId: z.string().regex(/^\d{1,10}$/, '1 이상의 숫자로 입력해주세요.'),
    defaultPrice: z.string().regex(/^\d{1,9}$/, '1 이상의 숫자로 입력해주세요.'),
  })
  .superRefine((values, ctx) => {
    if (Number(values.discogsMasterId) < 1) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['discogsMasterId'],
        message: '1 이상의 숫자로 입력해주세요.',
      });
    }

    if (Number(values.defaultPrice) < 1) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['defaultPrice'],
        message: '1 이상의 숫자로 입력해주세요.',
      });
    }
  });

export type CatalogImportJobStartFormValues = z.infer<typeof catalogImportJobStartFormSchema>;

export const EMPTY_CATALOG_IMPORT_JOB_START_FORM_VALUES: CatalogImportJobStartFormValues = {
  discogsMasterId: '',
  defaultPrice: '',
};

export const toCatalogImportJobStartPayload = (
  values: CatalogImportJobStartFormValues,
): CatalogImportJobRequest => ({
  discogsMasterId: Number(values.discogsMasterId),
  defaultPrice: Number(values.defaultPrice),
});
