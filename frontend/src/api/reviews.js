import client from './client'

// 리뷰 API. 작성·삭제는 회원 본인 전용 — client 가 Bearer 를 자동 첨부한다(인증 필수).
// 앨범별 리뷰 목록(공개)은 albums.js 의 reviews(id, params) 가 담당한다.

/**
 * 리뷰 작성. POST /api/v1/reviews → ReviewResponse(201).
 * 배송완료(DELIVERED/COMPLETED) 본인 주문 + 그 주문에 포함된 앨범에만 가능(서버 검증).
 * @param {{orderNumber:string, albumId:number, rating:number, content?:string}} body
 */
export function create(body) {
  return client.post('/reviews', body).then((res) => res.data)
}

/** 리뷰 삭제(본인). DELETE /api/v1/reviews/{reviewId} → 204(null). */
export function remove(reviewId) {
  return client.delete(`/reviews/${reviewId}`).then((res) => res.data)
}
