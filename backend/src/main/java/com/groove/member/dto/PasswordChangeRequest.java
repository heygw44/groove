package com.groove.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
		@NotBlank
		String currentPassword,

		@NotBlank
		@Size(min = 8, max = 20, message = "비밀번호는 8~20자여야 합니다.")
		String newPassword
) {
}
