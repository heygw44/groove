import client from './client'

/**
 * 아티스트 목록. GET /api/v1/artists (공개). PageResponse<Artist> 반환.
 * 카탈로그 필터 드롭다운 옵션 소스로도 쓴다(size 를 크게 줘 한 번에 받음).
 * @param {{page?:number, size?:number, sort?:string}} [params]
 */
export function list(params = {}) {
  return client.get('/artists', { params, auth: false }).then((res) => res.data)
}

/** 아티스트 상세. GET /api/v1/artists/{id} (공개). */
export function detail(id) {
  return client.get(`/artists/${id}`, { auth: false }).then((res) => res.data)
}

/**
 * 아티스트의 앨범 목록. GET /api/v1/artists/{id}/albums (공개). PageResponse<AlbumSummary> 반환.
 * @param {number|string} id
 * @param {Record<string, unknown>} [params] page/size/sort 등 (artistId 는 경로 id 로 강제됨).
 */
export function albums(id, params = {}) {
  return client.get(`/artists/${id}/albums`, { params, auth: false }).then((res) => res.data)
}
