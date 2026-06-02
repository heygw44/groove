import client from './client'

/**
 * 앨범 목록. GET /api/v1/albums (공개). PageResponse<AlbumSummary> 반환.
 * @param {{page?:number, size?:number, sort?:string, genre?:string, artist?:string}} [params]
 */
export function list(params = {}) {
  return client.get('/albums', { params, auth: false }).then((res) => res.data)
}

/** 앨범 상세. GET /api/v1/albums/{id} (공개). (#114 에서 상세 뷰 연결) */
export function detail(id) {
  return client.get(`/albums/${id}`, { auth: false }).then((res) => res.data)
}
