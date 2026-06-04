package com.groove.common.idempotency;

/**
 * 멱등성 레코드 상태.
 *
 * <p>{@code FAILED} 같은 종착 실패 상태는 없다 — 처리 중 예외가 나면 {@link IdempotencyService}
 * 가 마커 행을 삭제해 동일 키 재시도를 허용한다. 따라서 영속된 레코드는 항상 이 둘 중 하나다.
 */
public enum IdempotencyStatus {

    /** 어떤 호출자가 처리를 소유한 상태. 같은 키의 다른 호출자는 409 를 받는다. */
    IN_PROGRESS,

    /** 처리 완료 + 결과 캐싱됨. 같은 키의 재요청은 캐시된 결과를 그대로 받는다. */
    COMPLETED
}
