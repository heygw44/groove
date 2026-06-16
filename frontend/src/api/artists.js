import client from './client'

/** 아티스트 목록. GET /api/v1/artists (공개). PageResponse<Artist> 반환. */
export function list(params = {}) {
  return client.get('/artists', { params, auth: false }).then((res) => res.data)
}

/** 아티스트 상세. GET /api/v1/artists/{id} (공개). */
export function detail(id) {
  return client.get(`/artists/${id}`, { auth: false }).then((res) => res.data)
}

/** 아티스트의 앨범 목록. GET /api/v1/artists/{id}/albums (공개). PageResponse<AlbumSummary> 반환. */
export function albums(id, params = {}) {
  return client.get(`/artists/${id}/albums`, { params, auth: false }).then((res) => res.data)
}
