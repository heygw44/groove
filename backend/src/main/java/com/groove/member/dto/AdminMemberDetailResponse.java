package com.groove.member.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;
import com.groove.order.dto.OrderSummaryResponse;

public record AdminMemberDetailResponse(
		Long id,
		String email,
		String nickname,
		MemberRole role,
		MemberStatus status,
		LocalDateTime createdAt,
		long orderCount,
		BigDecimal totalPaymentAmount,
		long usableCouponCount,
		List<OrderSummaryResponse> recentOrders
) {

	public static AdminMemberDetailResponse of(Member member, AdminMemberActivitySummary activitySummary,
			List<OrderSummaryResponse> recentOrders) {
		return new AdminMemberDetailResponse(
				member.getId(),
				member.getEmail(),
				member.getNickname(),
				member.getRole(),
				member.getStatus(),
				member.getCreatedAt(),
				activitySummary.orderCount(),
				activitySummary.totalPaymentAmount(),
				activitySummary.usableCouponCount(),
				recentOrders
		);
	}
}
