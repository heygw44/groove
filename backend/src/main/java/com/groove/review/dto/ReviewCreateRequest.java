package com.groove.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewCreateRequest(
		@NotNull(message = "별점은 필수입니다.")
		@Min(value = 1, message = "별점은 1 이상이어야 합니다.")
		@Max(value = 5, message = "별점은 5 이하여야 합니다.")
		Integer rating,

		@Size(max = 100, message = "제목은 100자를 초과할 수 없습니다.")
		String title,

		@Size(max = 1000, message = "내용은 1000자를 초과할 수 없습니다.")
		String content
) {
}
