package com.groove.shipping.application;

/**
 * 운송장 번호 발급기 — UUID 기반. 운영에서는 UuidTrackingNumberGenerator 가 등록된다.
 */
public interface TrackingNumberGenerator {

    /**
     * 새 운송장 번호를 1건 생성한다. shipping.tracking_number 컬럼 길이(50자) 이하여야 한다.
     */
    String generate();
}
