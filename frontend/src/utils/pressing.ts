import { EDITION_TYPE_LABELS, getCountryLabel } from '@/constants/product';
import type { ProductSummary } from '@/types/product';

export function buildPressingMetaLine(product: ProductSummary): string {
  return [
    product.country && getCountryLabel(product.country),
    product.pressingYear?.toString(),
    EDITION_TYPE_LABELS[product.editionType],
  ]
    .filter((value): value is string => value !== undefined)
    .join(' · ');
}

/** 목록 축약으로 숨겨진 같은 앨범의 다른 에디션 수를 안내하는 문구. 0 이면 호출측에서 렌더링 자체를 생략한다. */
export function buildOtherPressingLabel(count: number): string {
  return `다른 에디션 ${count}종`;
}
