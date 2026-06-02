import client from './client'

/** 장르 전체 목록. GET /api/v1/genres (공개). Array<{id,name}> 반환 — 필터 드롭다운 옵션. */
export function genres() {
  return client.get('/genres', { auth: false }).then((res) => res.data)
}

/** 레이블 전체 목록. GET /api/v1/labels (공개). Array<{id,name}> 반환 — 필터 드롭다운 옵션. */
export function labels() {
  return client.get('/labels', { auth: false }).then((res) => res.data)
}
