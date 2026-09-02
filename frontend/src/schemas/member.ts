import { z } from 'zod';

import { nicknameField, passwordField } from '@/schemas/auth';

export const nicknameSchema = z.object({
  nickname: nicknameField,
});

export const passwordChangeSchema = z
  .object({
    currentPassword: z.string().min(1, '현재 비밀번호를 입력해주세요.'),
    newPassword: passwordField,
    newPasswordConfirm: z.string().min(1, '새 비밀번호를 한 번 더 입력해주세요.'),
  })
  .refine((values) => values.newPassword === values.newPasswordConfirm, {
    path: ['newPasswordConfirm'],
    message: '비밀번호가 일치하지 않습니다.',
  });

export type NicknameFormValues = z.infer<typeof nicknameSchema>;
export type PasswordChangeFormValues = z.infer<typeof passwordChangeSchema>;
