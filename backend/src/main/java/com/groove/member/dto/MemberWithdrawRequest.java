package com.groove.member.dto;

import jakarta.validation.constraints.NotBlank;

public record MemberWithdrawRequest(
		@NotBlank
		String password
) {
}
