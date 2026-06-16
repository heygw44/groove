import client from './client'

// 장바구니 API (회원 전용). Bearer 토큰은 client 인터셉터가 자동 첨부한다.

/** 장바구니 조회 → CartResponse{cartId, items[], totalAmount, totalItemCount}. */
export function getCart() {
  return client.get('/cart').then((res) => res.data)
}

/** 담기. 동일 albumId 는 수량 누적. → CartResponse. */
export function addItem(albumId, quantity = 1) {
  return client.post('/cart/items', { albumId, quantity }).then((res) => res.data)
}

/** 수량 변경(절대값 교체, 1~99). → CartResponse. */
export function updateItem(itemId, quantity) {
  return client.patch(`/cart/items/${itemId}`, { quantity }).then((res) => res.data)
}

/** 항목 삭제(204). */
export function removeItem(itemId) {
  return client.delete(`/cart/items/${itemId}`).then((res) => res.data)
}

/** 전체 비우기(204). */
export function clearCart() {
  return client.delete('/cart').then((res) => res.data)
}
