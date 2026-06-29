package com.groove.shipping.application;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 운송장 번호 = 임의 UUIDv4 문자열 (36자, shipping.tracking_number 컬럼 길이 50 이내).
 * 충돌은 uk_shipping_tracking UNIQUE 제약이 잡는다.
 */
@Component
public class UuidTrackingNumberGenerator {

    public String generate() {
        return UUID.randomUUID().toString();
    }
}
