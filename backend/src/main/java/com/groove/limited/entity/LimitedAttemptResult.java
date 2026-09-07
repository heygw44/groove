package com.groove.limited.entity;

import java.util.Optional;

import com.groove.global.common.ErrorCode;

/**
 * 한정반 구매 시도의 실패 사유. 성공은 limited_drop.sold_count 가 이미 durable 하게 들고 있어 따로 세지 않는다.
 * 드롭 경쟁과 무관한 개별 요청 오류(배송지 없음, 정지 회원 등)는 어느 값에도 매핑되지 않는다.
 */
public enum LimitedAttemptResult {

	SOLD_OUT, ALREADY_PURCHASED, NOT_OPEN, CLOSED;

	public static Optional<LimitedAttemptResult> from(ErrorCode errorCode) {
		return switch (errorCode) {
			case LIMITED_SOLD_OUT -> Optional.of(SOLD_OUT);
			case LIMITED_ALREADY_PURCHASED -> Optional.of(ALREADY_PURCHASED);
			case LIMITED_NOT_OPEN -> Optional.of(NOT_OPEN);
			case LIMITED_CLOSED -> Optional.of(CLOSED);
			default -> Optional.empty();
		};
	}
}
