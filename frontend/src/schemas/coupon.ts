import { z } from 'zod';

/** 서버 검증은 NotBlank + Size(max 30) 뿐이라 그보다 엄격한 패턴은 두지 않는다. */
export const couponIssueSchema = z.object({
  code: z
    .string()
    .trim()
    .min(1, '쿠폰 코드를 입력해주세요.')
    .max(30, '쿠폰 코드는 30자 이하여야 합니다.'),
});

export type CouponIssueFormValues = z.infer<typeof couponIssueSchema>;
