/* 마지막 사용자 입력 시각. 페이지 로드도 활동으로 본다. 탭 간 전파는 authChannel 이 맡는다. */
let lastUserActivity = Date.now();

/** 더 늦은 시각만 반영한다. 다른 탭에서 온 오래된 값이 되돌리지 못하게 하기 위해서다. */
export const recordUserActivity = (at: number) => {
  lastUserActivity = Math.max(lastUserActivity, at);
};

export const getLastUserActivity = () => lastUserActivity;

export const getIdleSeconds = (now: number) =>
  Math.max(0, Math.floor((now - lastUserActivity) / 1000));

/** 테스트에서 모듈 상태를 되돌릴 때만 쓴다. */
export const resetUserActivityForTest = (at: number = Date.now()) => {
  lastUserActivity = at;
};
