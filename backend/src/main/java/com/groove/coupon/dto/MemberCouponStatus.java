package com.groove.coupon.dto;

import org.springframework.util.StringUtils;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 내 쿠폰함 조회 필터. */
@Getter
@RequiredArgsConstructor
public enum MemberCouponStatus {

	USABLE("usable"),
	USED("used"),
	EXPIRED("expired");

	private final String value;

	public static MemberCouponStatus from(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		for (MemberCouponStatus status : values()) {
			if (status.value.equals(value)) {
				return status;
			}
		}
		throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
	}
}
