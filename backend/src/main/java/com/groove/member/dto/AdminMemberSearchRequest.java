package com.groove.member.dto;

import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminMemberSearchRequest(
		@Size(max = 100) String keyword,
		MemberStatus status,
		MemberRole role,
		@PositiveOrZero Integer page,
		@Min(1) @Max(100) Integer size
) {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;

	public AdminMemberSearchCondition toCondition() {
		int resolvedPage = page == null ? DEFAULT_PAGE : page;
		int resolvedSize = size == null ? DEFAULT_SIZE : size;
		return new AdminMemberSearchCondition(keyword, status, role, resolvedPage, resolvedSize);
	}
}
