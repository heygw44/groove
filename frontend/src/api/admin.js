import client from './client'

// 관리자 콘솔 API(#119) — 전부 ADMIN 전용 `/api/v1/admin/**`. client 인터셉터가 Bearer 를 자동 첨부하고,
// 비관리자/미인증은 서버가 403/401 로 거른다(클라 requiresAdmin 가드는 UI 보호일 뿐). api/coupons.js 패턴 차용.

// ── 앨범 ───────────────────────────────────────────────────────────────────

/**
 * 관리자 앨범 목록. GET /admin/albums → PageResponse<AlbumSummaryResponse>.
 * Public GET /albums 와 달리 HIDDEN 포함 전체 status 를 반환한다(#119 백엔드 추가).
 * @param {Record<string, unknown>} [params] keyword/genreId/labelId/artistId/format/status/sort/page/size
 */
export function listAlbums(params = {}) {
  return client.get('/admin/albums', { params }).then((res) => res.data)
}

/** 앨범 생성. POST /admin/albums → AlbumResponse(201). */
export function createAlbum(body) {
  return client.post('/admin/albums', body).then((res) => res.data)
}

/** 앨범 수정. PUT /admin/albums/{id} → AlbumResponse. stock 은 본 요청에 없음(재고는 adjustStock 전용). */
export function updateAlbum(id, body) {
  return client.put(`/admin/albums/${id}`, body).then((res) => res.data)
}

/** 앨범 삭제. DELETE /admin/albums/{id} → 204. */
export function deleteAlbum(id) {
  return client.delete(`/admin/albums/${id}`).then((res) => res.data)
}

/** 재고 조정. PATCH /admin/albums/{id}/stock (delta 음수 허용, 결과 음수면 400) → AlbumResponse. */
export function adjustStock(id, delta) {
  return client.patch(`/admin/albums/${id}/stock`, { delta }).then((res) => res.data)
}

// ── 주문 ───────────────────────────────────────────────────────────────────

/**
 * 관리자 주문 목록. GET /admin/orders → PageResponse<AdminOrderSummaryResponse>.
 * @param {{status?:string, memberId?:number, from?:string, to?:string, page?:number, size?:number}} [params]
 */
export function listOrders(params = {}) {
  return client.get('/admin/orders', { params }).then((res) => res.data)
}

/** 주문 상세. GET /admin/orders/{orderNumber} → AdminOrderResponse. */
export function getOrder(orderNumber) {
  return client.get(`/admin/orders/${orderNumber}`).then((res) => res.data)
}

/**
 * 주문 상태 강제 전환. PATCH /admin/orders/{orderNumber}/status → AdminOrderResponse.
 * 강제 가능 대상은 PREPARING/SHIPPED/DELIVERED/COMPLETED 이며 전이 규칙 위반 시 409.
 * @param {string} orderNumber
 * @param {{target:string, reason:string}} body reason 필수(1~500자)
 */
export function changeOrderStatus(orderNumber, body) {
  return client.patch(`/admin/orders/${orderNumber}/status`, body).then((res) => res.data)
}

/**
 * 주문 환불(취소). POST /admin/orders/{orderNumber}/refund → AdminRefundResponse(멱등).
 * 재요청 시 상태 불변 + alreadyRefunded=true.
 * @param {string} orderNumber
 * @param {{reason?:string}} [body]
 */
export function refundOrder(orderNumber, body = {}) {
  return client.post(`/admin/orders/${orderNumber}/refund`, body).then((res) => res.data)
}

// ── 쿠폰 ───────────────────────────────────────────────────────────────────

/**
 * 관리자 쿠폰 목록. GET /admin/coupons → PageResponse<AdminCouponSummary>.
 * @param {{status?:string, page?:number, size?:number, sort?:string}} [params]
 */
export function listCoupons(params = {}) {
  return client.get('/admin/coupons', { params }).then((res) => res.data)
}

/** 쿠폰 생성. POST /admin/coupons → AdminCouponSummary(201). */
export function createCoupon(body) {
  return client.post('/admin/coupons', body).then((res) => res.data)
}

/** 쿠폰 상태 변경. PATCH /admin/coupons/{id}/status → AdminCouponSummary. ENDED 는 종착(전이 불가 시 409). */
export function changeCouponStatus(id, target) {
  return client.patch(`/admin/coupons/${id}/status`, { target }).then((res) => res.data)
}

/** 쿠폰 직접지급. POST /admin/coupons/{id}/grant → AdminMemberCouponResponse(201). 탈퇴 회원은 404. */
export function grantCoupon(id, memberId) {
  return client.post(`/admin/coupons/${id}/grant`, { memberId }).then((res) => res.data)
}
