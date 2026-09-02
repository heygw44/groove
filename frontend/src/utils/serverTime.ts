// 클라이언트 시계와 서버 시각의 오프셋(ms)을 보관한다.
// 한정반 카운트다운 등 클라이언트 시계 조작에 취약한 화면에서 사용.
let serverOffsetMs = 0;

export function setServerOffset(serverTimeIso: string): void {
  const serverTime = new Date(serverTimeIso).getTime();
  serverOffsetMs = serverTime - Date.now();
}

export function getServerNow(): Date {
  return new Date(Date.now() + serverOffsetMs);
}
