package com.groove.limited.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 부분 수정 요청. 필드가 null 이면 기존 값을 유지한다. 키 생략/null=유지. */
public record LimitedDropUpdateRequest(
		@Min(value = 1, message = "총 수량은 1 이상이어야 합니다.")
		Integer totalQuantity,

		@Min(value = 1, message = "회원당 구매 제한은 1 이상이어야 합니다.")
		@Max(value = 5, message = "회원당 구매 제한은 5 이하여야 합니다.")
		Integer perMemberLimit,

		@Future(message = "오픈 시각은 현재 시각 이후여야 합니다.")
		LocalDateTime openAt,

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
