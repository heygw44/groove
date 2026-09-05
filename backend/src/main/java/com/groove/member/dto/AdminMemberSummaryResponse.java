package com.groove.member.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;

public record AdminMemberSummaryResponse(
		Long id,
		String email,
		String nickname,
		MemberRole role,
		MemberStatus status,
		long orderCount,
		BigDecimal totalPaymentAmount,
		LocalDateTime createdAt
) {
}
