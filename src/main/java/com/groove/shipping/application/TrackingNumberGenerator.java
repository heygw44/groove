package com.groove.shipping.application;

/**
 * 운송장 번호 발급기 — UUID 기반 (ERD §4.13, API.md §3.7).
 *
 * <p>인터페이스로 분리한 이유는 테스트에서 결정성을 확보하기 위함이다 — 운영에서는
 * {@link UuidTrackingNumberGenerator} 가 등록된다. 충돌 가능성은 호출 측이 {@code uk_shipping_tracking}
 * UNIQUE 제약으로 처리한다(UUIDv4 충돌 확률은 사실상 0).
 */
public interface TrackingNumberGenerator {

    /**
     * 새 운송장 번호를 1건 생성한다. {@code shipping.tracking_number} 컬럼 길이(50자) 이하여야 한다.
     */
    String generate();
}
