package com.groove.limited.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 한정반 드롭 등록 요청. */
public record LimitedDropCreateRequest(
		@NotNull(message = "상품 ID는 필수입니다.")
		Long productId,

		@NotNull(message = "총 수량은 필수입니다.")
		@Min(value = 1, message = "총 수량은 1 이상이어야 합니다.")
		Integer totalQuantity,

		@Min(value = 1, message = "회원당 구매 제한은 1 이상이어야 합니다.")
		@Max(value = 5, message = "회원당 구매 제한은 5 이하여야 합니다.")
		Integer perMemberLimit,

		@NotNull(message = "오픈 시각은 필수입니다.")
		@Future(message = "오픈 시각은 현재 시각 이후여야 합니다.")
		LocalDateTime openAt,

		@NotNull(message = "마감 시각은 필수입니다.")
		LocalDateTime closeAt
) {

	@AssertTrue(message = "마감 시각은 오픈 시각 이후여야 합니다.")
	public boolean isCloseAfterOpen() {
		if (openAt == null || closeAt == null) {
			return true;
		}
		return openAt.isBefore(closeAt);
	}
}
