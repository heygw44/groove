package com.groove.member.dto;

import com.groove.member.entity.MemberStatus;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record AdminMemberStatusChangeRequest(
		@NotNull MemberStatus status
) {

	@AssertTrue(message = "status 는 ACTIVE 또는 SUSPENDED 만 허용됩니다.")
	public boolean isChangeableStatus() {
		return status == null || status != MemberStatus.WITHDRAWN;
	}
}
