// 측정 요약(handleSummary) 공통 처리 (#219) — setup() 가 반환한 토큰 풀이 요약 파일에 평문으로
// 새지 않도록 비식별화한다. 커밋된 *-summary.json 에 실제 JWT 가 박혀 시크릿 스캐너가 검출하고
// 저장소 접근자에게 인증 재사용 단서를 노출하는 문제를 막는다.

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

// data.setup_data.tokens(실제 JWT 배열)를 `<redacted:N>` 한 건으로 치환한다(개수만 보존).
// in-place 치환 — goja 에 structuredClone 미보장. textSummary 는 metrics 만 쓰므로 영향 없다.
// 커스텀 handleSummary(예: flash-sale.js)는 이 함수만 호출하고 자체 산출물을 구성하면 된다.
export function redactSetupTokens(data) {
  if (data && data.setup_data && Array.isArray(data.setup_data.tokens)) {
    data.setup_data.tokens = [`<redacted:${data.setup_data.tokens.length}>`];
  }
  return data;
}

// 비식별화 후 표준 요약 산출물(JSON 파일 + 컬러 stdout)을 만든다. path 는 'loadtest/X-summary.json'.
// handleSummary 가 커스텀 로직 없이 토큰 풀만 가리면 되는 시나리오의 공통 반환값.
export function redactedSummary(data, path) {
  redactSetupTokens(data);
  return {
    [path]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}
