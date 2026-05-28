package com.groove.coupon.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 쿠폰 만료 배치 설정 ({@code groove.coupon.expiration.*}).
 *
 * <p>스케줄러 cron 은 {@code @Scheduled(cron = "${groove.coupon.expiration.cron:…}")} 로 환경에서
 * 직접 읽으므로 여기에 두지 않는다 ({@link MemberCouponExpirationTask}, {@code IdempotencyProperties}
 * 와 동일 규약).
 *
 * @param batchSize 한 트랜잭션에서 EXPIRED 로 전환하는 최대 행 수 (1 이상)
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
