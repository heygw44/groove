import client from './client'

/** 앨범 목록·검색. GET /api/v1/albums (공개). PageResponse<AlbumSummary> 반환. */
export function list(params = {}) {
  return client.get('/albums', { params, auth: false }).then((res) => res.data)
}

/** 앨범 상세. GET /api/v1/albums/{id} (공개). AlbumDetail 반환. */
export function detail(id) {
  return client.get(`/albums/${id}`, { auth: false }).then((res) => res.data)
}

/** 앨범 리뷰 목록. GET /api/v1/albums/{id}/reviews (공개). PageResponse<Review> 반환. */
export function reviews(id, params = {}) {
  return client.get(`/albums/${id}/reviews`, { params, auth: false }).then((res) => res.data)
}
