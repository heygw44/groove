package com.groove.order.service;

import java.math.BigDecimal;

import com.groove.payment.client.dto.RefundAccountInfo;

/**
 * 취소·반품 클레임 승인이 공유하는 부분환불 시도를 결제 도메인에 맡긴다. 즉시 취소 가능 구간(PAID)의 자동 승인,
 * 관리자 취소 승인, 관리자 판매취소, 반품 수거 완료가 모두 이 훅을 거친다.
 *
 * <p>결과불명(토스 응답 timeout 등)이면 클레임을 진행 중 상태로 둔 채 조용히 반환한다 - 이후 대사
 * ({@code PaymentCancelRetrier})가 이어받아 마무리한다. 토스가 명시적으로 거절하면 클레임을 거부 상태로
 * 되돌린 뒤 예외를 다시 던진다.</p>
 */
public interface OrderClaimRefundHook {

	void refund(Long orderId, Long claimId, BigDecimal amount, String reason, RefundAccountInfo refundAccount);
}
