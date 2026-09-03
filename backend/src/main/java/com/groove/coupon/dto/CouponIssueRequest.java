package com.groove.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CouponIssueRequest(
		@NotBlank
		@Size(max = 30)
		String code
) {
}
