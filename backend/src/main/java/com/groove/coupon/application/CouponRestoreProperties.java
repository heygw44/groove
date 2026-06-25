package com.groove.coupon.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 쿠폰 복원 정책 설정 (groove.coupon.restore.*).
 *
 * <p>grace 는 주문 취소/환불로 쿠폰을 되살릴 때, 복원 시점에 이미 만료된 쿠폰의 만료시각을
 * now + grace 로 연장해 주는 유예기간이다 (1 이상). 기본 7일.
 */
@ConfigurationProperties(prefix = "groove.coupon.restore")
public record CouponRestoreProperties(
        @DefaultValue("P7D") Duration grace
) {

    public CouponRestoreProperties {
        if (grace == null || grace.isNegative() || grace.isZero()) {
            throw new IllegalStateException(
                    "groove.coupon.restore.grace 는 양수 기간이어야 합니다 (현재: " + grace + ")");
        }
    }
}
