package com.groove.member.dto;

import com.groove.member.entity.MemberStatus;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminMemberStatusChangeRequest(
		@NotNull MemberStatus status,
		@Size(max = 200) String reason
) {

	@AssertTrue(message = "status 는 ACTIVE 또는 SUSPENDED 만 허용됩니다.")
	public boolean isChangeableStatus() {
		return status == null || status != MemberStatus.WITHDRAWN;
	}
}
