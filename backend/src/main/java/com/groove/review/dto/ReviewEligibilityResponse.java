package com.groove.review.dto;

public record ReviewEligibilityResponse(
		boolean eligible,
		ReviewIneligibleReason reason
) {

	public static ReviewEligibilityResponse allow() {
		return new ReviewEligibilityResponse(true, null);
	}

	public static ReviewEligibilityResponse deny(ReviewIneligibleReason reason) {
		return new ReviewEligibilityResponse(false, reason);
	}
}
