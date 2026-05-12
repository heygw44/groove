package com.groove.shipping.application;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 운송장 번호 = 임의 UUIDv4 문자열 (36자, {@code shipping.tracking_number} 컬럼 길이 50 이내).
 *
 * <p>실제 택배사 연동이 없는 시연 환경이므로 외부 추적 시스템과의 정합성은 필요 없다 — 충돌 없는 외부 식별자면 충분하다.
 * UUIDv4 충돌 확률은 무시 가능하며, 만일의 충돌은 {@code uk_shipping_tracking} UNIQUE 제약이 잡는다.
 */
@Component
public class UuidTrackingNumberGenerator implements TrackingNumberGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
