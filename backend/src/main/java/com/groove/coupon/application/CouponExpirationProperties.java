package com.groove.coupon.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 쿠폰 만료 배치 설정 (groove.coupon.expiration.*).
 *
 * <p>batchSize 는 한 트랜잭션에서 EXPIRED 로 전환하는 최대 행 수 (1 이상).
 */
@ConfigurationProperties(prefix = "groove.coupon.expiration")
public record CouponExpirationProperties(
        @DefaultValue("1000") int batchSize
) {

    public CouponExpirationProperties {
        if (batchSize <= 0) {
            throw new IllegalStateException(
                    "groove.coupon.expiration.batch-size 는 1 이상이어야 합니다 (현재: " + batchSize + ")");
        }
    }
}
