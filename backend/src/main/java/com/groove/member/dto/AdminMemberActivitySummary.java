package com.groove.member.dto;

import java.math.BigDecimal;

/** {@link com.groove.member.mapper.MemberQueryMapper#findActivitySummary} 결과 행. */
public record AdminMemberActivitySummary(
		long orderCount,
		BigDecimal totalPaymentAmount,
		long usableCouponCount
) {
}
