package com.groove.limited.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record LimitedDropListResponse(
		List<LimitedDropSummaryResponse> drops,
		OffsetDateTime serverTime
) {
}
