package com.groove.order.application;

/**
 * orderNumber 발급기 — 형식 {@code ORD-YYYYMMDD-XXXXXX} (API.md §3.5 예시).
 *
 * <p>인터페이스로 분리한 이유는 테스트에서 Clock/Random 결정성을 확보하기 위함이다 —
 * 운영에서는 {@link RandomOrderNumberGenerator} 가 등록된다.
 */
public interface OrderNumberGenerator {

    /**
     * 새 orderNumber 를 1건 생성한다. 충돌 가능성은 호출 측이 UNIQUE 제약 + 재시도로 처리한다.
     */
    String generate();
}
