/**
 * 결제 도메인 모델 (W7).
 *
 * <p>{@link com.groove.payment.domain.Payment} 엔티티 + 상태/수단 enum
 * ({@link com.groove.payment.domain.PaymentStatus}, {@link com.groove.payment.domain.PaymentMethod})
 * + {@link com.groove.payment.domain.PaymentRepository}. 결제 확정(PAID/FAILED 전이, {@code paidAt}
 * 기록)은 #W7-4 웹훅/폴링 범위다.
 */
package com.groove.payment.domain;
