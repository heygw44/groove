package com.groove.common.idempotency;

/** 멱등성 레코드 상태. FAILED 같은 실패 상태는 없다 — 처리 중 예외가 나면 마커 행이 삭제된다. */
public enum IdempotencyStatus {

    /** 어떤 호출자가 처리를 소유한 상태. 같은 키의 다른 호출자는 409 를 받는다. */
    IN_PROGRESS,

    /** 처리 완료 + 결과 캐싱됨. 같은 키의 재요청은 캐시된 결과를 받는다. */
    COMPLETED
}
