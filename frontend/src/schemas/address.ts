import { z } from 'zod';

/**
 * 전화번호 정규식은 백엔드 AddressCreateRequest 와 같다. 010 만 받지 않고
 * 지역번호(02, 031 등)도 통과시킨다 - 프론트가 더 엄격하면 안 된다.
 */
export const addressSchema = z.object({
  recipientName: z
    .string()
    .min(1, '수령인을 입력해주세요.')
    .max(30, '수령인은 30자 이하여야 합니다.'),
  phone: z
    .string()
    .min(1, '연락처를 입력해주세요.')
    .regex(/^\d{2,3}-\d{3,4}-\d{4}$/, '010-1234-5678 형식으로 입력해주세요.'),
  zipCode: z
    .string()
    .min(1, '우편번호를 입력해주세요.')
    .regex(/^\d{5}$/, '우편번호는 5자리 숫자입니다.'),
  address1: z
    .string()
    .min(1, '기본 주소를 입력해주세요.')
    .max(200, '기본 주소는 200자 이하여야 합니다.'),
  address2: z.string().max(200, '상세 주소는 200자 이하여야 합니다.'),
  isDefault: z.boolean(),
});

/** PATCH 요청에는 isDefault 가 없다. 기본 배송지는 전용 엔드포인트로만 바꾼다. */
export const addressUpdateSchema = addressSchema.omit({ isDefault: true });

export type AddressFormValues = z.infer<typeof addressSchema>;
