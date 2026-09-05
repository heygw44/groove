import axios from 'axios';
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';

import { ERROR_CODE_FIELD, ERROR_MESSAGES, FIELD_ALIASES } from '@/constants/errorMessages';
import type { ApiError } from '@/types/api';

/**
 * 훅이 아니라 순수 함수인 이유: axios 인터셉터나 mutationFn 처럼 React 밖에서도
 * 같은 판단이 필요하다.
 */
export const getApiError = (error: unknown): ApiError | undefined => {
  if (!axios.isAxiosError(error)) {
    return undefined;
  }
  const data = error.response?.data as { error?: ApiError } | undefined;
  return data?.error;
};

export const getErrorCode = (error: unknown): string | undefined => getApiError(error)?.code;

export const getErrorMessage = (error: unknown, fallback = '요청을 처리하지 못했습니다.') => {
  const apiError = getApiError(error);
  if (!apiError) {
    return fallback;
  }
  return ERROR_MESSAGES[apiError.code] ?? apiError.message ?? fallback;
};

/**
 * 서버 검증 실패를 폼 필드로 되돌린다. fieldErrors 가 있으면 그대로 옮기고,
 * 없으면 필드 귀속이 분명한 코드(중복 이메일 등)만 해당 필드에 붙인다.
 * 어느 필드에도 붙이지 못했으면 false - 호출부가 폼 상단 배너로 처리한다.
 */
export const applyFieldErrors = <T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
): boolean => {
  const apiError = getApiError(error);
  if (!apiError) {
    return false;
  }

  const fieldErrors = apiError.fieldErrors ?? [];
  fieldErrors.forEach((fieldError, index) => {
    const field = FIELD_ALIASES[fieldError.field] ?? fieldError.field;
    setError(
      field as Path<T>,
      { type: 'server', message: fieldError.reason },
      { shouldFocus: index === 0 },
    );
  });
  if (fieldErrors.length > 0) {
    return true;
  }

  const field = ERROR_CODE_FIELD[apiError.code];
  if (!field) {
    return false;
  }
  setError(
    field as Path<T>,
    { type: 'server', message: getErrorMessage(error) },
    { shouldFocus: true },
  );
  return true;
};
