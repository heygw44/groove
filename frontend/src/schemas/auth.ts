import { z } from 'zod';

/** 백엔드 SignupRequest / LoginRequest 의 Bean Validation 을 그대로 옮긴 것. */
export const emailField = z
  .string()
  .min(1, '이메일을 입력해주세요.')
  .email('이메일 형식이 아닙니다.');

export const passwordField = z
  .string()
  .min(1, '비밀번호를 입력해주세요.')
  .min(8, '비밀번호는 8~20자여야 합니다.')
  .max(20, '비밀번호는 8~20자여야 합니다.');

export const nicknameField = z
  .string()
  .min(1, '닉네임을 입력해주세요.')
  .min(2, '닉네임은 2~20자여야 합니다.')
  .max(20, '닉네임은 2~20자여야 합니다.');

export const loginSchema = z.object({
  email: z.string().min(1, '이메일을 입력해주세요.'),
  password: z.string().min(1, '비밀번호를 입력해주세요.'),
});

export const signupSchema = z
  .object({
    email: emailField,
    password: passwordField,
    passwordConfirm: z.string().min(1, '비밀번호를 한 번 더 입력해주세요.'),
    nickname: nicknameField,
  })
  .refine((values) => values.password === values.passwordConfirm, {
    path: ['passwordConfirm'],
    message: '비밀번호가 일치하지 않습니다.',
  });

export type LoginFormValues = z.infer<typeof loginSchema>;
export type SignupFormValues = z.infer<typeof signupSchema>;
