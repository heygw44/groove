/**
 * 서버 error.code 중 그대로 노출하기에 문맥이 부족한 것만 다시 쓴다.
 * 여기 없는 코드는 서버 message 를 그대로 보여준다.
 */
export const ERROR_MESSAGES: Record<string, string> = {
  AUTH_INVALID_CREDENTIALS: '이메일 또는 비밀번호가 올바르지 않습니다.',
  AUTH_FORBIDDEN: '접근 권한이 없습니다.',
  MEMBER_EMAIL_DUPLICATE: '이미 가입된 이메일입니다.',
  MEMBER_PASSWORD_MISMATCH: '현재 비밀번호가 올바르지 않습니다.',
  MEMBER_WITHDRAWN: '탈퇴한 계정입니다.',
  MEMBER_ADDRESS_NOT_FOUND: '이미 삭제된 배송지입니다.',
  MEMBER_ADDRESS_LIMIT_EXCEEDED: '배송지는 최대 10개까지 등록할 수 있습니다.',
  COMMON_INTERNAL_ERROR: '잠시 후 다시 시도해주세요.',
  PRODUCT_NOT_FOUND: '존재하지 않는 상품입니다.',
  PRODUCT_HIDDEN: '판매가 중지된 상품입니다.',
  PRODUCT_NOT_HIDDEN: '숨김 상태가 아닌 상품입니다.',
  ARTIST_NOT_FOUND: '존재하지 않는 아티스트입니다.',
  LABEL_NOT_FOUND: '존재하지 않는 레이블입니다.',
  GENRE_NOT_FOUND: '존재하지 않는 장르입니다.',
  STOCK_NOT_FOUND: '존재하지 않는 재고입니다.',
  STOCK_INSUFFICIENT: '재고가 부족합니다.',
  STOCK_CONFLICT: '다른 요청과 충돌했습니다. 다시 시도해주세요.',
  FILE_EMPTY: '빈 파일은 업로드할 수 없습니다.',
  FILE_INVALID_FORMAT: 'jpg, png, webp 형식만 업로드할 수 있습니다.',
  FILE_SIZE_EXCEEDED: '파일 용량은 5MB를 넘을 수 없습니다.',
  CART_ITEM_NOT_FOUND: '이미 삭제된 장바구니 항목입니다.',
  CART_QUANTITY_EXCEEDED: '한 상품은 최대 10개까지 담을 수 있습니다.',
  CART_EMPTY: '장바구니가 비어 있습니다.',
  ORDER_NOT_FOUND: '존재하지 않는 주문입니다.',
  ORDER_CANNOT_CANCEL: '취소할 수 없는 상태의 주문입니다.',
  WISHLIST_ALREADY_EXISTS: '이미 위시리스트에 있는 상품입니다.',
  WISHLIST_NOT_FOUND: '위시리스트에 없는 상품입니다.',
};

/** 서버 코드를 폼의 특정 필드에 귀속시킨다. 나머지는 폼 상단 배너로 간다. */
export const ERROR_CODE_FIELD: Record<string, string> = {
  MEMBER_EMAIL_DUPLICATE: 'email',
  MEMBER_PASSWORD_MISMATCH: 'currentPassword',
  ARTIST_NOT_FOUND: 'artistId',
  LABEL_NOT_FOUND: 'labelId',
  GENRE_NOT_FOUND: 'genreIds',
};
