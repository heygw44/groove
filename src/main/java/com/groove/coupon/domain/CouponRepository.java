package com.groove.coupon.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 쿠폰 정책 저장소. 선착순 발급의 원자적 조건부 UPDATE 와 비관적 락 변형은 후속 이슈(#90)에서
 * 추가한다 — 본 이슈는 기본 CRUD 만 둔다.
 */
public interface CouponRepository extends JpaRepository<Coupon, Long> {
}
