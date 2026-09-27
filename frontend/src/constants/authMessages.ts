/** 로그인 페이지 ?reason= 값 → 안내 문구. 알 수 없는 값은 무시한다. */
export const LOGIN_NOTICE_MESSAGES: ReadonlyMap<string, string> = new Map([
  ['password-changed', '비밀번호가 변경되어 다시 로그인해 주세요.'],
  ['idle', '오랫동안 활동이 없어 로그아웃되었습니다. 다시 로그인해 주세요.'],
  ['expired', '로그인 유지 기간이 끝났습니다. 다시 로그인해 주세요.'],
]);
