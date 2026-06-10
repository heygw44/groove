// W9 주문 페이로드 공통 헬퍼 (#193) — order.js / payment.js 가 재사용.
//
// albumId 를 반복 인덱스로 분산해 동일 앨범의 재고 경합(409)을 최소화한다 — 본 측정의 목적은
// 처리량 베이스라인이지 동시성 정합성이 아니다(정합성은 W9-3에서 집중 측정). 배송지는 고정 상수.

const SHIPPING = {
  recipientName: '부하테스트',
  recipientPhone: '01000000000',
  address: '서울특별시 강남구 테헤란로 123',
  addressDetail: '4층',
  zipCode: '06234',
  safePackagingRequested: false,
};

// 반복 인덱스 n 으로 서로 다른 albumId itemCount개를 고른다(1..albumCount 로 분산, 각 수량 1).
// 연속 iteration 이 연속 albumId 를 쓰므로 한 run 내에서 같은 앨범에 주문이 몰리지 않는다.
export function buildOrderBody({ n, albumCount, itemCount = 2 }) {
  // 라인 수가 albumCount 를 넘으면 한 본문 안에서 albumId 가 중복(같은 앨범 다중 차감)되므로 클램프한다.
  const lines = Math.min(itemCount, albumCount);
  const items = [];
  for (let k = 0; k < lines; k++) {
    const albumId = ((n * lines + k) % albumCount) + 1;
    items.push({ albumId, quantity: 1 });
  }
  return { items, shipping: SHIPPING };
}

// 단일 앨범 1종을 주문하는 본문 — flash-sale.js(#194) 가 동일 한정반에 동시 쇄도시켜 재고 경합을 일으킬 때
// 사용한다. buildOrderBody 와 달리 albumId 를 분산하지 않고 의도적으로 한 앨범에 고정한다.
export function buildSingleAlbumOrder(albumId, quantity = 1) {
  return { items: [{ albumId, quantity }], shipping: SHIPPING };
}
