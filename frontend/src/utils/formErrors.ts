/**
 * 배열 필드는 배열 자체의 에러(min/max 등)와 원소 에러(항목 내부 검증 실패)를
 * 같은 자리(`errors.field`)에 담는다. `.message` 만 읽으면 원소 에러는 조용히
 * 사라져 사용자에게는 "저장이 안 먹는다"로만 보인다. 배열 → root → 원소 순으로
 * 첫 메시지를 찾는다.
 */
export const getArrayFieldErrorMessage = (fieldError: unknown): string | undefined => {
  if (!fieldError || typeof fieldError !== 'object') {
    return undefined;
  }

  const { message, root, ...items } = fieldError as {
    message?: string;
    root?: { message?: string };
    [key: string]: unknown;
  };
  if (message) {
    return message;
  }
  if (root?.message) {
    return root.message;
  }

  for (const item of Object.values(items)) {
    if (!item || typeof item !== 'object') {
      continue;
    }
    for (const value of Object.values(item)) {
      if (value && typeof value === 'object' && typeof (value as { message?: unknown }).message === 'string') {
        return (value as { message: string }).message;
      }
    }
  }

  return undefined;
};
