package com.groove.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressCreateRequest(
		@NotBlank(message = "수령인은 필수입니다.")
		@Size(max = 30, message = "수령인은 30자 이하여야 합니다.")
		String recipientName,

		@NotBlank(message = "전화번호는 필수입니다.")
		@Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
		String phone,

		@NotBlank(message = "우편번호는 필수입니다.")
		@Pattern(regexp = "^\\d{5}$", message = "우편번호는 5자리 숫자입니다.")
		String zipCode,

		@NotBlank(message = "기본 주소는 필수입니다.")
		@Size(max = 200, message = "기본 주소는 200자 이하여야 합니다.")
		String address1,

		@Size(max = 200, message = "상세 주소는 200자 이하여야 합니다.")
		String address2,

		Boolean isDefault
) {
}
