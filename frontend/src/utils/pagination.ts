export function toTotalPages(totalElements: number, size: number): number {
  return Math.ceil(totalElements / size);
}

/** 처리 뒤 현재 페이지가 비었을 때 되돌아갈 마지막 페이지. 이동이 필요 없으면 undefined. */
export function getFallbackPage(
  page: number,
  contentLength: number,
  totalPages: number,
): number | undefined {
  if (contentLength === 0 && page > 0) {
    return Math.max(totalPages - 1, 0);
  }
  return undefined;
}
