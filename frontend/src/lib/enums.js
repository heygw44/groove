// 도메인 enum ↔ 한글 라벨/옵션 매핑 — 백엔드 AlbumFormat·AlbumStatus 와 1:1.

/** AlbumFormat enum → 한글 라벨. */
export const FORMAT_LABEL = {
  LP_12: '12" LP',
  LP_DOUBLE: '더블 LP',
  EP: 'EP',
  SINGLE_7: '7" 싱글',
}

/** AlbumStatus enum → 한글 라벨. */
export const STATUS_LABEL = {
  SELLING: '판매중',
  SOLD_OUT: '품절',
  HIDDEN: '비공개',
}

/** format 코드 → 라벨. 매핑이 없으면 원본 코드를 그대로 반환. */
export function formatLabel(format) {
  return FORMAT_LABEL[format] || format || ''
}

/** status 코드 → 라벨. 매핑이 없으면 원본 코드를 그대로 반환. */
export function statusLabel(status) {
  return STATUS_LABEL[status] || status || ''
}

/** 카탈로그 필터 — 포맷 select 옵션. FORMAT_LABEL 에서 파생. */
export const FORMAT_OPTIONS = Object.entries(FORMAT_LABEL).map(([value, label]) => ({ value, label }))

/** 카탈로그 정렬 옵션(createdAt/price/releaseYear). */
export const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: '최신순' },
  { value: 'price,asc', label: '가격 낮은순' },
  { value: 'price,desc', label: '가격 높은순' },
  { value: 'releaseYear,desc', label: '발매 최신순' },
  { value: 'releaseYear,asc', label: '발매 오래된순' },
]
