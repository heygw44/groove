package com.groove.global.idempotency;

/** {@link IdempotentExecutor#execute} 의 결과. replayed 가 true 면 저장된 완료 응답을 그대로 돌려준 것이다. */
public record IdempotentResult<T>(T response, boolean replayed) {
}
