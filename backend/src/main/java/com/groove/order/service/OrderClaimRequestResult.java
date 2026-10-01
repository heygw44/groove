package com.groove.order.service;

import java.math.BigDecimal;

/** T1(클레임 요청 기록)의 결과. {@code immediate} 가 true 면 곧바로 환불을 시도해야 한다(즉시 취소 가능 구간). */
public record OrderClaimRequestResult(
		Long claimId,
		Long orderItemId,
		Long orderId,
		BigDecimal refundAmount,
		boolean immediate
) {
}
