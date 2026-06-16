import client from './client'

// 회원 셀프서비스 API. Bearer 토큰은 client 인터셉터가 자동 첨부하므로 별도 옵션이 필요 없다.

/** 내 정보 조회 → MemberResponse{memberId,email,name,phone,role,emailVerified,createdAt}. */
export async function getMe() {
  const res = await client.get('/members/me')
  return res.data
}

/** 프로필 부분 수정(PATCH). 보내지 않은 필드는 미변경. 빈 값 정리는 호출부 책임. → MemberResponse. */
export async function updateProfile(payload) {
  const res = await client.patch('/members/me', payload)
  return res.data
}

/** 비밀번호 변경(204). 성공 시 서버가 모든 refresh 토큰을 폐기한다. */
export async function changePassword(payload) {
  await client.patch('/members/me/password', payload)
}

/** 회원 탈퇴(204). DELETE 바디로 비밀번호를 확인한다(axios 는 data 옵션으로 바디 전달). */
export async function withdraw(payload) {
  await client.delete('/members/me', { data: payload })
}
