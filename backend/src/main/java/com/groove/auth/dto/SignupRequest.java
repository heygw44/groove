package com.groove.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@NotBlank
		@Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
		@Pattern(regexp = "^[\\x21-\\x7E]+$", message = "이메일은 영문, 숫자, 기호만 사용할 수 있습니다.")
		@Email(message = "이메일 형식이 아닙니다.")
		String email,

		@NotBlank
		@Size(min = 8, max = 20, message = "비밀번호는 8~20자여야 합니다.")
		String password,

		@NotBlank
		@Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
		String nickname
) {
}
